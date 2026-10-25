#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavcodec/jni.h>
#include <libavcodec/mediacodec.h>
#include <libavformat/avformat.h>
#include <libavutil/error.h>
#include <libavutil/mathematics.h>
#include <libavutil/mem.h>
#include <libavutil/time.h>
#include <libswscale/swscale.h>
}

#include <algorithm>
#include <cerrno>
#include <chrono>
#include <cmath>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <exception>
#include <memory>
#include <mutex>
#include <limits>
#include <string>
#include <sys/stat.h>
#include <thread>
#include <unistd.h>

namespace {

constexpr int kIoBufferBytes = 64 * 1024;
constexpr int kMaxSoftwarePreviewEdge = 1920;
constexpr int64_t kLateFrameDropThresholdNs = 80'000'000LL;
constexpr int64_t kNoTimestamp = AV_NOPTS_VALUE;

struct FfmpegPreview {
    explicit FfmpegPreview(const int ownedFd) : fd(ownedFd) {}

    int fd = -1;
    int64_t ioPosition = 0;
    AVIOContext* io = nullptr;
    AVFormatContext* format = nullptr;
    AVCodecContext* videoCodec = nullptr;
    AVPacket* packet = nullptr;
    AVFrame* frame = nullptr;
    AVMediaCodecContext* mediaCodecContext = nullptr;
    SwsContext* scaler = nullptr;
    ANativeWindow* window = nullptr;
    AVStream* videoStream = nullptr;
    int videoStreamIndex = -1;
    int64_t streamStartTime = 0;
    int64_t durationUs = 0;
    int64_t positionUs = 0;
    int windowWidth = 0;
    int windowHeight = 0;
    int surfaceMaxWidth = 0;
    int surfaceMaxHeight = 0;
    int64_t discardBeforeUs = 0;
    int64_t clockMediaStartUs = 0;
    int64_t clockWallStartNs = 0;
    float playbackSpeed = 1.0f;
    bool useMediaCodec = false;
    bool firstFrameRendered = false;
    bool atEnd = false;
    bool flushSent = false;
    bool packetPending = false;
    bool stop = false;
    bool playing = false;
    bool stepPending = false;
    bool seekRequested = false;
    int64_t seekTargetUs = 0;
    std::string lastError;
    mutable std::mutex mutex;
    std::condition_variable condition;
    std::thread worker;
};

FfmpegPreview* asPreview(const jlong handle) {
    return reinterpret_cast<FfmpegPreview*>(static_cast<intptr_t>(handle));
}

std::string ffmpegError(const int errorCode) {
    char text[AV_ERROR_MAX_STRING_SIZE]{};
    av_strerror(errorCode, text, sizeof(text));
    return text;
}

int readFromFd(void* opaque, uint8_t* buffer, const int bufferSize) {
    auto* preview = static_cast<FfmpegPreview*>(opaque);
    if (preview == nullptr || preview->fd < 0 || buffer == nullptr || bufferSize <= 0) return AVERROR(EINVAL);

    for (;;) {
        const ssize_t count = pread(preview->fd, buffer, static_cast<size_t>(bufferSize), preview->ioPosition);
        if (count > 0) {
            preview->ioPosition += count;
            return static_cast<int>(count);
        }
        if (count == 0) return AVERROR_EOF;
        if (errno == EINTR) continue;
        return AVERROR(errno);
    }
}

int64_t seekInFd(void* opaque, const int64_t offset, const int whence) {
    auto* preview = static_cast<FfmpegPreview*>(opaque);
    if (preview == nullptr || preview->fd < 0) return AVERROR(EINVAL);

    if ((whence & AVSEEK_SIZE) != 0) {
        struct stat fileInfo {};
        if (fstat(preview->fd, &fileInfo) != 0 || fileInfo.st_size < 0) return AVERROR(errno ? errno : EIO);
        return static_cast<int64_t>(fileInfo.st_size);
    }

    const int origin = whence & ~AVSEEK_FORCE;
    int64_t base = 0;
    if (origin == SEEK_CUR) {
        base = preview->ioPosition;
    } else if (origin == SEEK_END) {
        struct stat fileInfo {};
        if (fstat(preview->fd, &fileInfo) != 0 || fileInfo.st_size < 0) return AVERROR(errno ? errno : EIO);
        base = static_cast<int64_t>(fileInfo.st_size);
    } else if (origin != SEEK_SET) {
        return AVERROR(EINVAL);
    }

    if ((offset > 0 && base > INT64_MAX - offset) ||
        (offset < 0 && (offset == std::numeric_limits<int64_t>::min() || base < -offset))) {
        return AVERROR(EINVAL);
    }
    const int64_t target = base + offset;
    if (target < 0) return AVERROR(EINVAL);
    preview->ioPosition = target;
    return target;
}

void releasePreviewResources(FfmpegPreview& preview) {
    if (preview.worker.joinable()) {
        {
            std::lock_guard<std::mutex> guard(preview.mutex);
            preview.stop = true;
            preview.condition.notify_all();
        }
        preview.worker.join();
    }

    if (preview.videoCodec != nullptr) {
        if (preview.useMediaCodec) {
            av_mediacodec_default_free(preview.videoCodec);
            preview.mediaCodecContext = nullptr;
        }
        avcodec_free_context(&preview.videoCodec);
    }
    av_packet_free(&preview.packet);
    av_frame_free(&preview.frame);
    sws_freeContext(preview.scaler);
    preview.scaler = nullptr;
    if (preview.format != nullptr) avformat_close_input(&preview.format);
    if (preview.io != nullptr) {
        av_freep(&preview.io->buffer);
        avio_context_free(&preview.io);
    }
    if (preview.window != nullptr) {
        ANativeWindow_release(preview.window);
        preview.window = nullptr;
    }
    if (preview.fd >= 0) {
        close(preview.fd);
        preview.fd = -1;
    }
}

void setError(FfmpegPreview& preview, const std::string& error) {
    std::lock_guard<std::mutex> guard(preview.mutex);
    preview.lastError = error.empty() ? "FFmpeg could not decode the video stream" : error.substr(0, 512);
    preview.playing = false;
    preview.stepPending = false;
    preview.condition.notify_all();
}

AVCodecContext* openVideoDecoder(FfmpegPreview& preview, const AVCodec* decoder, jobject surface, bool useMediaCodec) {
    AVCodecContext* codecContext = avcodec_alloc_context3(decoder);
    if (codecContext == nullptr) return nullptr;

    int result = avcodec_parameters_to_context(codecContext, preview.videoStream->codecpar);
    if (result < 0) {
        avcodec_free_context(&codecContext);
        return nullptr;
    }
    codecContext->pkt_timebase = preview.videoStream->time_base;
    codecContext->thread_count = 0;
    codecContext->flags2 |= AV_CODEC_FLAG2_FAST;

    if (useMediaCodec) {
        preview.mediaCodecContext = av_mediacodec_alloc_context();
        if (preview.mediaCodecContext == nullptr ||
            av_mediacodec_default_init(codecContext, preview.mediaCodecContext, surface) < 0) {
            if (preview.mediaCodecContext != nullptr) {
                av_freep(&preview.mediaCodecContext);
                preview.mediaCodecContext = nullptr;
            }
            avcodec_free_context(&codecContext);
            return nullptr;
        }
    }

    result = avcodec_open2(codecContext, decoder, nullptr);
    if (result < 0) {
        if (useMediaCodec) av_mediacodec_default_free(codecContext);
        preview.mediaCodecContext = nullptr;
        avcodec_free_context(&codecContext);
        return nullptr;
    }
    return codecContext;
}

bool initializeDecoder(FfmpegPreview& preview, jobject surface) {
    const AVCodecID codecId = preview.videoStream->codecpar->codec_id;
    const char* hardwareDecoderName = nullptr;
    switch (codecId) {
        case AV_CODEC_ID_H264: hardwareDecoderName = "h264_mediacodec"; break;
        case AV_CODEC_ID_HEVC: hardwareDecoderName = "hevc_mediacodec"; break;
        case AV_CODEC_ID_AV1: hardwareDecoderName = "av1_mediacodec"; break;
        case AV_CODEC_ID_VP8: hardwareDecoderName = "vp8_mediacodec"; break;
        case AV_CODEC_ID_VP9: hardwareDecoderName = "vp9_mediacodec"; break;
        case AV_CODEC_ID_MPEG2VIDEO: hardwareDecoderName = "mpeg2_mediacodec"; break;
        case AV_CODEC_ID_MPEG4: hardwareDecoderName = "mpeg4_mediacodec"; break;
        default: break;
    }

    // FFmpeg's Android MediaCodec wrappers still defer actual profile, bit-depth, and device
    // support decisions to the platform codec. A failed open falls through to the bundled
    // software decoder; that path is also used for ProRes and APV.
    if (hardwareDecoderName != nullptr && surface != nullptr) {
        const AVCodec* hardwareDecoder = avcodec_find_decoder_by_name(hardwareDecoderName);
        if (hardwareDecoder != nullptr) {
            preview.videoCodec = openVideoDecoder(preview, hardwareDecoder, surface, true);
            if (preview.videoCodec != nullptr) {
                preview.useMediaCodec = true;
                return true;
            }
        }
    }

    const AVCodec* softwareDecoder = avcodec_find_decoder(codecId);
    if (softwareDecoder == nullptr) {
        preview.lastError = "This FFmpeg build does not include a decoder for the video's codec";
        return false;
    }
    preview.videoCodec = openVideoDecoder(preview, softwareDecoder, nullptr, false);
    if (preview.videoCodec == nullptr) {
        preview.lastError = "FFmpeg could not initialize a compatible video decoder";
        return false;
    }
    preview.useMediaCodec = false;
    return true;
}

bool seekTo(FfmpegPreview& preview, const int64_t targetUs) {
    const int64_t nonNegativeTargetUs = std::max<int64_t>(targetUs, 0);
    const int64_t safeTargetUs = preview.durationUs > 0
        ? std::min(nonNegativeTargetUs, preview.durationUs)
        : nonNegativeTargetUs;
    const int64_t targetTimestamp = preview.streamStartTime + av_rescale_q(
        safeTargetUs, AVRational{1, AV_TIME_BASE}, preview.videoStream->time_base);
    const int result = av_seek_frame(preview.format, preview.videoStreamIndex, targetTimestamp, AVSEEK_FLAG_BACKWARD);
    if (result < 0) return false;

    avcodec_flush_buffers(preview.videoCodec);
    avformat_flush(preview.format);
    preview.flushSent = false;
    preview.packetPending = false;
    av_packet_unref(preview.packet);
    preview.discardBeforeUs = safeTargetUs;
    {
        std::lock_guard<std::mutex> guard(preview.mutex);
        preview.positionUs = safeTargetUs;
        preview.atEnd = false;
        preview.lastError.clear();
        preview.clockMediaStartUs = safeTargetUs;
        preview.clockWallStartNs = 0;
    }
    return true;
}

int64_t frameTimestampUs(const FfmpegPreview& preview, const AVFrame* frame) {
    int64_t timestamp = frame->best_effort_timestamp;
    if (timestamp == kNoTimestamp) timestamp = frame->pts;
    if (timestamp == kNoTimestamp) {
        std::lock_guard<std::mutex> guard(preview.mutex);
        return preview.positionUs;
    }
    timestamp -= preview.streamStartTime;
    return std::max<int64_t>(0, av_rescale_q(timestamp, preview.videoStream->time_base, AVRational{1, AV_TIME_BASE}));
}

int64_t monotonicTimeNs() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

enum class FrameRenderOutcome {
    Rendered,
    Skipped,
    HardwareDecoderFailed,
    Failed,
};

FrameRenderOutcome waitForFrameTime(FfmpegPreview& preview, const int64_t frameUs) {
    int64_t targetWallNs = monotonicTimeNs();
    float speed = 1.0f;
    bool playing = false;
    {
        std::lock_guard<std::mutex> guard(preview.mutex);
        playing = preview.playing;
        speed = preview.playbackSpeed;
        if (playing) {
            if (preview.clockWallStartNs == 0) {
                preview.clockWallStartNs = monotonicTimeNs();
                preview.clockMediaStartUs = frameUs;
            }
            const int64_t mediaDeltaUs = std::max<int64_t>(0, frameUs - preview.clockMediaStartUs);
            targetWallNs = preview.clockWallStartNs + static_cast<int64_t>(
                static_cast<double>(mediaDeltaUs) * 1'000.0 / std::max(0.1f, speed));
        }
    }

    const auto wasInterrupted = [&preview, playing] {
        std::lock_guard<std::mutex> guard(preview.mutex);
        return preview.stop || preview.seekRequested || (playing && !preview.playing);
    };

    // Keep real-time playback close to the media clock instead of displaying a growing queue of
    // stale frames on slower devices. VLC/Media3 also prefer dropping late preview frames to
    // stalling the whole editor; paused seeks are never dropped.
    const int64_t nowNs = monotonicTimeNs();
    if (playing && targetWallNs < nowNs - kLateFrameDropThresholdNs) {
        return FrameRenderOutcome::Skipped;
    }

    if (preview.useMediaCodec && preview.frame->format == AV_PIX_FMT_MEDIACODEC) {
        if (wasInterrupted()) return FrameRenderOutcome::Skipped;
        const int64_t nowNs = monotonicTimeNs();
        // MediaCodec only accepts target timestamps close to the monotonic clock. A late frame
        // is rendered immediately rather than delaying the rest of the stream.
        targetWallNs = std::clamp<int64_t>(targetWallNs, nowNs - 500'000'000LL, nowNs + 500'000'000LL);
        auto* buffer = reinterpret_cast<AVMediaCodecBuffer*>(preview.frame->data[3]);
        if (buffer == nullptr || av_mediacodec_render_buffer_at_time(buffer, targetWallNs) < 0) {
            return FrameRenderOutcome::HardwareDecoderFailed;
        }
        return FrameRenderOutcome::Rendered;
    }

    const int64_t waitNs = targetWallNs - monotonicTimeNs();
    if (playing && waitNs > 0) {
        std::unique_lock<std::mutex> lock(preview.mutex);
        preview.condition.wait_for(lock, std::chrono::nanoseconds(waitNs), [&preview] {
            return preview.stop || !preview.playing || preview.seekRequested;
        });
    }
    if (wasInterrupted()) return FrameRenderOutcome::Skipped;

    if (preview.window == nullptr) return FrameRenderOutcome::Failed;
    const int sourceWidth = preview.frame->width;
    const int sourceHeight = preview.frame->height;
    if (sourceWidth <= 0 || sourceHeight <= 0) return FrameRenderOutcome::Failed;

    const int availableWidth = preview.surfaceMaxWidth > 0 ? preview.surfaceMaxWidth : 1280;
    const int availableHeight = preview.surfaceMaxHeight > 0 ? preview.surfaceMaxHeight : 720;
    const double fitScale = std::min({
        1.0,
        static_cast<double>(availableWidth) / sourceWidth,
        static_cast<double>(availableHeight) / sourceHeight,
        static_cast<double>(kMaxSoftwarePreviewEdge) / std::max(sourceWidth, sourceHeight)
    });
    const int destinationWidth = std::max(1, static_cast<int>(std::lround(sourceWidth * fitScale)));
    const int destinationHeight = std::max(1, static_cast<int>(std::lround(sourceHeight * fitScale)));

    if (preview.windowWidth != destinationWidth || preview.windowHeight != destinationHeight) {
        if (ANativeWindow_setBuffersGeometry(
                preview.window, destinationWidth, destinationHeight, WINDOW_FORMAT_RGBA_8888) != 0) {
            return FrameRenderOutcome::Failed;
        }
        preview.windowWidth = destinationWidth;
        preview.windowHeight = destinationHeight;
    }
    ANativeWindow_Buffer windowBuffer{};
    if (ANativeWindow_lock(preview.window, &windowBuffer, nullptr) != 0 || windowBuffer.bits == nullptr) {
        return FrameRenderOutcome::Failed;
    }

    preview.scaler = sws_getCachedContext(
        preview.scaler,
        sourceWidth,
        sourceHeight,
        static_cast<AVPixelFormat>(preview.frame->format),
        destinationWidth,
        destinationHeight,
        AV_PIX_FMT_RGBA,
        SWS_BILINEAR,
        nullptr,
        nullptr,
        nullptr);
    if (preview.scaler == nullptr) {
        ANativeWindow_unlockAndPost(preview.window);
        return FrameRenderOutcome::Failed;
    }

    uint8_t* destination[4] = {
        static_cast<uint8_t*>(windowBuffer.bits), nullptr, nullptr, nullptr
    };
    const int destinationStride[4] = {windowBuffer.stride * 4, 0, 0, 0};
    const int rows = sws_scale(
        preview.scaler,
        preview.frame->data,
        preview.frame->linesize,
        0,
        sourceHeight,
        destination,
        destinationStride);
    const int postResult = ANativeWindow_unlockAndPost(preview.window);
    return rows == destinationHeight && postResult == 0
        ? FrameRenderOutcome::Rendered
        : FrameRenderOutcome::Failed;
}

enum class DecodeOutcome {
    Rendered,
    Skipped,
    EndOfStream,
    HardwareDecoderFailed,
    Failed,
};

DecodeOutcome decodeAndRenderNextFrame(FfmpegPreview& preview, std::string* error) {
    for (;;) {
        const int receiveResult = avcodec_receive_frame(preview.videoCodec, preview.frame);
        if (receiveResult == 0) {
            const int64_t timestampUs = frameTimestampUs(preview, preview.frame);
            const int64_t targetUs = preview.discardBeforeUs;
            const int64_t frameToleranceUs = std::max<int64_t>(1, av_rescale_q(
                1, preview.videoStream->time_base, AVRational{1, AV_TIME_BASE}));
            if (timestampUs + frameToleranceUs < targetUs) {
                av_frame_unref(preview.frame);
                continue;
            }
            const FrameRenderOutcome renderOutcome = waitForFrameTime(preview, timestampUs);
            if (renderOutcome == FrameRenderOutcome::Failed) {
                av_frame_unref(preview.frame);
                *error = "FFmpeg could not render the decoded video frame to the preview surface";
                return DecodeOutcome::Failed;
            }
            if (renderOutcome == FrameRenderOutcome::HardwareDecoderFailed) {
                av_frame_unref(preview.frame);
                *error = "Android MediaCodec could not render a decoded frame";
                return DecodeOutcome::HardwareDecoderFailed;
            }
            if (renderOutcome == FrameRenderOutcome::Skipped) {
                av_frame_unref(preview.frame);
                return DecodeOutcome::Skipped;
            }
            {
                std::lock_guard<std::mutex> guard(preview.mutex);
                preview.positionUs = timestampUs;
                preview.firstFrameRendered = true;
                preview.discardBeforeUs = 0;
                preview.lastError.clear();
            }
            av_frame_unref(preview.frame);
            return DecodeOutcome::Rendered;
        }

        if (receiveResult == AVERROR_EOF) return DecodeOutcome::EndOfStream;
        if (receiveResult != AVERROR(EAGAIN)) {
            *error = "FFmpeg video decoder failed: " + ffmpegError(receiveResult);
            if (preview.useMediaCodec) {
                av_frame_unref(preview.frame);
                return DecodeOutcome::HardwareDecoderFailed;
            }
            return DecodeOutcome::Failed;
        }
        if (preview.packetPending) {
            const int sendResult = avcodec_send_packet(preview.videoCodec, preview.packet);
            if (sendResult == AVERROR(EAGAIN)) {
                *error = "FFmpeg decoder made no progress while submitting a video packet";
                return DecodeOutcome::Failed;
            }
            av_packet_unref(preview.packet);
            preview.packetPending = false;
            if (sendResult < 0) {
                *error = "FFmpeg could not submit a video packet: " + ffmpegError(sendResult);
                return preview.useMediaCodec
                    ? DecodeOutcome::HardwareDecoderFailed
                    : DecodeOutcome::Failed;
            }
            continue;
        }
        if (preview.flushSent) return DecodeOutcome::EndOfStream;

        const int readResult = av_read_frame(preview.format, preview.packet);
        if (readResult < 0) {
            if (readResult != AVERROR_EOF) {
                *error = "FFmpeg could not read the next video packet: " + ffmpegError(readResult);
                return DecodeOutcome::Failed;
            }
            const int flushResult = avcodec_send_packet(preview.videoCodec, nullptr);
            preview.flushSent = true;
            if (flushResult < 0 && flushResult != AVERROR_EOF && flushResult != AVERROR(EAGAIN)) {
                *error = "FFmpeg could not flush the video decoder: " + ffmpegError(flushResult);
                return DecodeOutcome::Failed;
            }
            continue;
        }

        if (preview.packet->stream_index == preview.videoStreamIndex) {
            const int sendResult = avcodec_send_packet(preview.videoCodec, preview.packet);
            if (sendResult == AVERROR(EAGAIN)) {
                preview.packetPending = true;
                continue;
            }
            av_packet_unref(preview.packet);
            if (sendResult < 0) {
                *error = "FFmpeg could not submit a video packet: " + ffmpegError(sendResult);
                return preview.useMediaCodec
                    ? DecodeOutcome::HardwareDecoderFailed
                    : DecodeOutcome::Failed;
            }
        } else {
            av_packet_unref(preview.packet);
        }
    }
}

bool switchToSoftwareDecoder(FfmpegPreview& preview, std::string* error) {
    const AVCodecID codecId = preview.videoStream->codecpar->codec_id;
    const AVCodec* softwareDecoder = avcodec_find_decoder(codecId);
    if (softwareDecoder == nullptr) {
        *error = "Android MediaCodec failed and this FFmpeg build has no software decoder for the codec";
        return false;
    }

    int64_t resumePositionUs = 0;
    {
        std::lock_guard<std::mutex> guard(preview.mutex);
        resumePositionUs = preview.positionUs;
    }

    if (preview.videoCodec != nullptr) {
        if (preview.useMediaCodec) {
            av_mediacodec_default_free(preview.videoCodec);
            preview.mediaCodecContext = nullptr;
        }
        avcodec_free_context(&preview.videoCodec);
    }
    preview.useMediaCodec = false;
    preview.videoCodec = openVideoDecoder(preview, softwareDecoder, nullptr, false);
    if (preview.videoCodec == nullptr) {
        *error = "Android MediaCodec failed and FFmpeg could not initialize its software decoder";
        return false;
    }
    if (!seekTo(preview, resumePositionUs)) {
        *error = "Android MediaCodec failed and FFmpeg could not resume in software at the current position";
        return false;
    }
    return true;
}

void playbackLoop(FfmpegPreview* preview) {
    while (preview != nullptr) {
        int64_t seekTargetUs = 0;
        bool hasSeek = false;
        bool shouldDecode = false;
        bool isPlaying = false;
        {
            std::unique_lock<std::mutex> lock(preview->mutex);
            preview->condition.wait(lock, [preview] {
                return preview->stop || preview->seekRequested || preview->playing || preview->stepPending;
            });
            if (preview->stop) return;
            if (preview->seekRequested) {
                seekTargetUs = preview->seekTargetUs;
                preview->seekRequested = false;
                hasSeek = true;
            }
            isPlaying = preview->playing;
            shouldDecode = preview->playing || preview->stepPending;
        }

        if (hasSeek && !seekTo(*preview, seekTargetUs)) {
            setError(*preview, "FFmpeg could not seek to the requested video position");
            continue;
        }
        if (!shouldDecode) continue;

        std::string error;
        const DecodeOutcome outcome = decodeAndRenderNextFrame(*preview, &error);
        if (outcome == DecodeOutcome::HardwareDecoderFailed) {
            std::string fallbackError;
            if (switchToSoftwareDecoder(*preview, &fallbackError)) continue;
            setError(*preview, fallbackError.empty() ? error : fallbackError);
            continue;
        }
        if (outcome == DecodeOutcome::Failed) {
            setError(*preview, error);
            continue;
        }
        if (outcome == DecodeOutcome::EndOfStream) {
            std::lock_guard<std::mutex> guard(preview->mutex);
            preview->atEnd = true;
            preview->playing = false;
            preview->stepPending = false;
            preview->positionUs = preview->durationUs;
            preview->condition.notify_all();
            continue;
        }
        if (outcome == DecodeOutcome::Skipped) continue;

        if (!isPlaying) {
            std::lock_guard<std::mutex> guard(preview->mutex);
            if (!preview->seekRequested) preview->stepPending = false;
            preview->condition.notify_all();
        }
    }
}

void throwIoException(JNIEnv* env, const std::string& message) {
    if (env->ExceptionCheck()) return;
    jclass exceptionClass = env->FindClass("java/io/IOException");
    if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, message.c_str());
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    if (av_jni_set_java_vm(vm, nullptr) < 0) return JNI_ERR;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_timeline_media_NativeFfmpegDecoder_nativeOpen(
    JNIEnv* env, jobject, const jint descriptor, jobject surface) {
    if (descriptor < 0 || surface == nullptr) {
        throwIoException(env, "FFmpeg needs a readable source file and an active preview surface");
        return 0;
    }

    const int ownedFd = dup(descriptor);
    if (ownedFd < 0) {
        throwIoException(env, std::string("FFmpeg could not duplicate the source descriptor: ") + std::strerror(errno));
        return 0;
    }

    auto preview = std::make_unique<FfmpegPreview>(ownedFd);
    preview->window = ANativeWindow_fromSurface(env, surface);
    if (preview->window == nullptr) {
        close(ownedFd);
        preview->fd = -1;
        throwIoException(env, "FFmpeg could not attach the preview surface");
        return 0;
    }
    preview->surfaceMaxWidth = std::max(0, ANativeWindow_getWidth(preview->window));
    preview->surfaceMaxHeight = std::max(0, ANativeWindow_getHeight(preview->window));

    uint8_t* ioBuffer = static_cast<uint8_t*>(av_malloc(kIoBufferBytes));
    if (ioBuffer == nullptr) {
        releasePreviewResources(*preview);
        throwIoException(env, "FFmpeg could not allocate its input buffer");
        return 0;
    }
    preview->io = avio_alloc_context(ioBuffer, kIoBufferBytes, 0, preview.get(), readFromFd, nullptr, seekInFd);
    if (preview->io == nullptr) {
        av_free(ioBuffer);
        releasePreviewResources(*preview);
        throwIoException(env, "FFmpeg could not initialize source-file access");
        return 0;
    }
    preview->io->seekable = AVIO_SEEKABLE_NORMAL;

    preview->format = avformat_alloc_context();
    if (preview->format == nullptr) {
        releasePreviewResources(*preview);
        throwIoException(env, "FFmpeg could not allocate a media demuxer");
        return 0;
    }
    preview->format->pb = preview->io;
    preview->format->flags |= AVFMT_FLAG_CUSTOM_IO;

    int result = avformat_open_input(&preview->format, nullptr, nullptr, nullptr);
    if (result < 0) {
        const std::string error = "FFmpeg could not open this media container: " + ffmpegError(result);
        releasePreviewResources(*preview);
        throwIoException(env, error);
        return 0;
    }
    result = avformat_find_stream_info(preview->format, nullptr);
    if (result < 0) {
        const std::string error = "FFmpeg could not read the media stream headers: " + ffmpegError(result);
        releasePreviewResources(*preview);
        throwIoException(env, error);
        return 0;
    }

    preview->videoStreamIndex = av_find_best_stream(
        preview->format, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    if (preview->videoStreamIndex < 0) {
        releasePreviewResources(*preview);
        throwIoException(env, "FFmpeg could not find a video stream in this media file");
        return 0;
    }
    preview->videoStream = preview->format->streams[preview->videoStreamIndex];
    if (preview->videoStream->start_time != AV_NOPTS_VALUE) {
        preview->streamStartTime = preview->videoStream->start_time;
    } else if (preview->format->start_time != AV_NOPTS_VALUE) {
        preview->streamStartTime = av_rescale_q(
            preview->format->start_time, AVRational{1, AV_TIME_BASE}, preview->videoStream->time_base);
    }

    if (preview->videoStream->duration != AV_NOPTS_VALUE && preview->videoStream->duration > 0) {
        preview->durationUs = av_rescale_q(
            preview->videoStream->duration, preview->videoStream->time_base, AVRational{1, AV_TIME_BASE});
    } else if (preview->format->duration != AV_NOPTS_VALUE && preview->format->duration > 0) {
        preview->durationUs = preview->format->duration;
    }

    if (!initializeDecoder(*preview, surface)) {
        const std::string error = preview->lastError;
        releasePreviewResources(*preview);
        throwIoException(env, error);
        return 0;
    }
    preview->packet = av_packet_alloc();
    preview->frame = av_frame_alloc();
    if (preview->packet == nullptr || preview->frame == nullptr) {
        releasePreviewResources(*preview);
        throwIoException(env, "FFmpeg could not allocate its video frame buffers");
        return 0;
    }

    try {
        preview->worker = std::thread(playbackLoop, preview.get());
    } catch (const std::exception& error) {
        releasePreviewResources(*preview);
        throwIoException(env, std::string("FFmpeg could not start its playback worker: ") + error.what());
        return 0;
    }
    FfmpegPreview* raw = preview.release();
    return static_cast<jlong>(reinterpret_cast<intptr_t>(raw));
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_media_NativeFfmpegDecoder_nativeClose(JNIEnv*, jobject, const jlong handle) {
    FfmpegPreview* preview = asPreview(handle);
    if (preview == nullptr) return;
    releasePreviewResources(*preview);
    delete preview;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_media_NativeFfmpegDecoder_nativePlay(JNIEnv*, jobject, const jlong handle) {
    FfmpegPreview* preview = asPreview(handle);
    if (preview == nullptr) return;
    std::lock_guard<std::mutex> guard(preview->mutex);
    if (preview->atEnd) {
        preview->seekTargetUs = 0;
        preview->seekRequested = true;
        preview->positionUs = 0;
        preview->atEnd = false;
    }
    preview->playing = true;
    preview->stepPending = false;
    preview->clockMediaStartUs = preview->positionUs;
    preview->clockWallStartNs = monotonicTimeNs();
    preview->lastError.clear();
    preview->condition.notify_all();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_media_NativeFfmpegDecoder_nativePause(JNIEnv*, jobject, const jlong handle) {
    FfmpegPreview* preview = asPreview(handle);
    if (preview == nullptr) return;
    std::lock_guard<std::mutex> guard(preview->mutex);
    preview->playing = false;
    preview->stepPending = false;
    preview->condition.notify_all();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_media_NativeFfmpegDecoder_nativeSeek(
    JNIEnv*, jobject, const jlong handle, const jlong positionUs) {
    FfmpegPreview* preview = asPreview(handle);
    if (preview == nullptr) return;
    std::lock_guard<std::mutex> guard(preview->mutex);
    preview->seekTargetUs = std::max<jlong>(0, positionUs);
    preview->seekRequested = true;
    preview->stepPending = !preview->playing;
    preview->clockMediaStartUs = preview->seekTargetUs;
    preview->clockWallStartNs = 0;
    preview->condition.notify_all();
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_media_NativeFfmpegDecoder_nativeSetPlaybackSpeed(
    JNIEnv*, jobject, const jlong handle, const jfloat speed) {
    FfmpegPreview* preview = asPreview(handle);
    if (preview == nullptr) return;
    std::lock_guard<std::mutex> guard(preview->mutex);
    preview->playbackSpeed = std::isfinite(speed) ? std::clamp(speed, 0.1f, 4.0f) : 1.0f;
    preview->clockMediaStartUs = preview->positionUs;
    preview->clockWallStartNs = monotonicTimeNs();
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_timeline_media_NativeFfmpegDecoder_nativeGetPositionUs(JNIEnv*, jobject, const jlong handle) {
    FfmpegPreview* preview = asPreview(handle);
    if (preview == nullptr) return 0;
    std::lock_guard<std::mutex> guard(preview->mutex);
    return preview->positionUs;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_timeline_media_NativeFfmpegDecoder_nativeGetDurationUs(JNIEnv*, jobject, const jlong handle) {
    FfmpegPreview* preview = asPreview(handle);
    if (preview == nullptr) return 0;
    std::lock_guard<std::mutex> guard(preview->mutex);
    return preview->durationUs;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_timeline_media_NativeFfmpegDecoder_nativeIsPlaying(JNIEnv*, jobject, const jlong handle) {
    FfmpegPreview* preview = asPreview(handle);
    if (preview == nullptr) return JNI_FALSE;
    std::lock_guard<std::mutex> guard(preview->mutex);
    return preview->playing ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_timeline_media_NativeFfmpegDecoder_nativeHasFirstFrame(JNIEnv*, jobject, const jlong handle) {
    FfmpegPreview* preview = asPreview(handle);
    if (preview == nullptr) return JNI_FALSE;
    std::lock_guard<std::mutex> guard(preview->mutex);
    return preview->firstFrameRendered ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_timeline_media_NativeFfmpegDecoder_nativeGetLastError(JNIEnv* env, jobject, const jlong handle) {
    FfmpegPreview* preview = asPreview(handle);
    if (preview == nullptr) return env->NewStringUTF("FFmpeg decoder is closed");
    std::lock_guard<std::mutex> guard(preview->mutex);
    return env->NewStringUTF(preview->lastError.c_str());
}
