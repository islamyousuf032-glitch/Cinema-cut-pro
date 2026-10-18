#include <jni.h>
#include <android/log.h>

#include "audio_mixer.h"
#include "color_eval.h"
#include "export_core.h"
#include "frame_compositor.h"
#include "lut_processor.h"
#include "render_frame.h"
#include "transform_eval.h"

#include <cstdint>
#include <limits>

#define LOG_TAG "NativeExportJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace {

constexpr jint kMaximumFrameDimension = 16384;
ExportCore* exportCoreInstance = nullptr;

bool validDimensions(jint width, jint height) {
    return width > 0 && height > 0 && width <= kMaximumFrameDimension && height <= kMaximumFrameDimension;
}

jlong rgbaCapacity(jint width, jint height) {
    if (!validDimensions(width, height)) return -1;
    return static_cast<jlong>(width) * static_cast<jlong>(height) * 4;
}

jlong i420Capacity(jint width, jint height) {
    if (!validDimensions(width, height) || (width & 1) != 0 || (height & 1) != 0) return -1;
    return static_cast<jlong>(width) * static_cast<jlong>(height) * 3 / 2;
}

uint8_t* directBytes(JNIEnv* env, jobject buffer, jlong minimumCapacity) {
    if (buffer == nullptr || minimumCapacity < 0) return nullptr;
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (capacity < minimumCapacity) return nullptr;
    return static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
}

void throwIllegalArgumentException(JNIEnv* env, const char* message) {
    jclass exceptionClass = env->FindClass("java/lang/IllegalArgumentException");
    if (exceptionClass != nullptr) env->ThrowNew(exceptionClass, message);
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeCreateExportCore(JNIEnv*, jobject) {
    if (exportCoreInstance == nullptr) {
        try {
            exportCoreInstance = new ExportCore();
            LOGI("Created ExportCore instance");
        } catch (...) {
            return JNI_FALSE;
        }
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeReleaseExportCore(JNIEnv*, jobject) {
    delete exportCoreInstance;
    exportCoreInstance = nullptr;
    LOGI("Released ExportCore instance");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeEvaluateFramePlan(JNIEnv*, jobject, jlong frameIndex) {
    return exportCoreInstance != nullptr && frameIndex >= 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeApplyTransform(
    JNIEnv* env, jobject, jobject buffer, jint width, jint height,
    jfloat scaleX, jfloat scaleY, jfloat rotation, jfloat posX, jfloat posY, jfloat opacity) {
    const jlong capacity = rgbaCapacity(width, height);
    uint8_t* pixels = directBytes(env, buffer, capacity);
    if (pixels == nullptr) return;
    RenderFrame frame{pixels, width, height, width * 4, 0};
    try {
        TransformEval::applyTransform(frame, scaleX, scaleY, rotation, posX, posY, opacity);
    } catch (...) {
        // Do not let native allocation failures cross the JNI boundary.
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeApplyColor(
    JNIEnv* env, jobject, jobject buffer, jint width, jint height,
    jfloat exposure, jfloat brightness, jfloat contrast, jfloat saturation) {
    const jlong capacity = rgbaCapacity(width, height);
    uint8_t* pixels = directBytes(env, buffer, capacity);
    if (pixels == nullptr) return;
    RenderFrame frame{pixels, width, height, width * 4, 0};
    ColorEval::applyColorAdjustments(frame, exposure, brightness, contrast, saturation);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeApplyClipAdjustments(
    JNIEnv* env, jobject, jobject buffer, jint width, jint height, jfloatArray values) {
    constexpr jsize kAdjustmentCount = 8;
    const jlong capacity = rgbaCapacity(width, height);
    uint8_t* pixels = directBytes(env, buffer, capacity);
    if (pixels == nullptr) {
        throwIllegalArgumentException(env, "Clip-adjustment frame must be a complete direct RGBA buffer.");
        return;
    }
    if (values == nullptr || env->GetArrayLength(values) != kAdjustmentCount) {
        throwIllegalArgumentException(env, "Clip-adjustment parameters must contain exactly eight values.");
        return;
    }

    jfloat parameters[kAdjustmentCount];
    env->GetFloatArrayRegion(values, 0, kAdjustmentCount, parameters);
    if (env->ExceptionCheck()) return;

    const ClipColorAdjustments adjustments{
        parameters[0], // exposure stops
        parameters[1], // brightness
        parameters[2], // contrast
        parameters[3], // saturation
        parameters[4], // vibrance
        parameters[5], // temperature
        parameters[6], // tint
        parameters[7]  // combined sharpness, clarity and structure
    };
    RenderFrame frame{pixels, width, height, width * 4, 0};
    ColorEval::applyClipAdjustments(frame, adjustments);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeApplyLut(
    JNIEnv* env, jobject, jobject buffer, jint width, jint height, jobject lutBuffer, jint lutSize) {
    const jlong imageBytes = rgbaCapacity(width, height);
    if (lutSize < 2 || lutSize > 64) return;
    const jlong lutBytes = static_cast<jlong>(lutSize) * lutSize * lutSize * 3;
    uint8_t* pixels = directBytes(env, buffer, imageBytes);
    uint8_t* lut = directBytes(env, lutBuffer, lutBytes);
    if (pixels == nullptr || lut == nullptr) return;
    RenderFrame frame{pixels, width, height, width * 4, 0};
    LUTProcessor::applyLUT(frame, lut, lutSize);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeCompositeFrame(
    JNIEnv* env, jobject, jobject bgBuffer, jobject fgBuffer, jint width, jint height, jint blendMode) {
    const jlong capacity = rgbaCapacity(width, height);
    uint8_t* bgPixels = directBytes(env, bgBuffer, capacity);
    uint8_t* fgPixels = directBytes(env, fgBuffer, capacity);
    if (bgPixels == nullptr || fgPixels == nullptr) return;
    RenderFrame bgFrame{bgPixels, width, height, width * 4, 0};
    const RenderFrame fgFrame{fgPixels, width, height, width * 4, 0};
    FrameCompositor::compositeFrames(bgFrame, fgFrame, blendMode);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeConvertRgbaToYuv420(
    JNIEnv* env, jobject, jobject rgbaBuffer, jobject yuvBuffer, jint width, jint height) {
    const jlong rgbaBytes = rgbaCapacity(width, height);
    const jlong yuvBytes = i420Capacity(width, height);
    uint8_t* rgbaPixels = directBytes(env, rgbaBuffer, rgbaBytes);
    uint8_t* yuvPixels = directBytes(env, yuvBuffer, yuvBytes);
    if (rgbaPixels == nullptr || yuvPixels == nullptr) return;
    const RenderFrame rgbaFrame{rgbaPixels, width, height, width * 4, 0};
    RenderFrame yuvFrame{yuvPixels, width, height, width, 1};
    FrameCompositor::convertRgbaToYuv420(rgbaFrame, yuvFrame);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeMixAudio(
    JNIEnv* env, jobject, jobject destBuffer, jobject srcBuffer, jint numSamples, jfloat volume) {
    if (numSamples <= 0 || static_cast<jlong>(numSamples) > std::numeric_limits<jlong>::max() / 2) return;
    const jlong bytes = static_cast<jlong>(numSamples) * 2;
    auto* destination = reinterpret_cast<int16_t*>(directBytes(env, destBuffer, bytes));
    const auto* source = reinterpret_cast<const int16_t*>(directBytes(env, srcBuffer, bytes));
    if (destination == nullptr || source == nullptr) return;
    AudioMixer::mixAudio(destination, source, numSamples, volume);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeBlendWatermarkYuv(
    JNIEnv* env, jobject, jobject yuvBuffer, jobject watermarkRgbaBuffer, jint width, jint height) {
    const jlong yuvBytes = i420Capacity(width, height);
    const jlong rgbaBytes = rgbaCapacity(width, height);
    uint8_t* yuvPixels = directBytes(env, yuvBuffer, yuvBytes);
    uint8_t* watermarkPixels = directBytes(env, watermarkRgbaBuffer, rgbaBytes);
    if (yuvPixels == nullptr || watermarkPixels == nullptr) return;
    RenderFrame yuvFrame{yuvPixels, width, height, width, 1};
    const RenderFrame watermarkFrame{watermarkPixels, width, height, width * 4, 0};
    FrameCompositor::blendRgbaWatermarkIntoYuv420(yuvFrame, watermarkFrame);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_timeline_export_native_NativeExportCore_nativeGetStats(JNIEnv* env, jobject) {
    jintArray result = env->NewIntArray(3);
    if (result == nullptr) return nullptr;
    if (exportCoreInstance != nullptr) {
        const ExportStats stats = exportCoreInstance->getStats();
        const jint values[3] = {stats.framesProcessed, static_cast<jint>(stats.totalRenderTimeMs), stats.audioSamplesMixed};
        env->SetIntArrayRegion(result, 0, 3, values);
    } else {
        const jint values[3] = {0, 0, 0};
        env->SetIntArrayRegion(result, 0, 3, values);
    }
    return result;
}
