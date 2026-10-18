#include <jni.h>
#include "timeline_core.h"
#include "timeline_layout.h"
#include "timeline_hit_test.h"
#include "timeline_snap.h"
#include "timeline_edit.h"
#include <vector>

extern "C" {

JNIEXPORT jfloat JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeFrameToX(JNIEnv *env, jobject thiz, jlong frame, jfloat pixels_per_frame) {
    return TimelineCore::frameToX(frame, pixels_per_frame);
}

JNIEXPORT jlong JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeXToFrame(JNIEnv *env, jobject thiz, jfloat x, jfloat pixels_per_frame) {
    return TimelineCore::xToFrame(x, pixels_per_frame);
}

JNIEXPORT jlongArray JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeCalculateVisibleRange(JNIEnv *env, jobject thiz, jfloat scroll_x, jint viewport_width, jfloat pixels_per_frame) {
    int64_t startFrame = 0;
    int64_t endFrame = 0;
    TimelineCore::calculateVisibleFrameRange(scroll_x, viewport_width, pixels_per_frame, startFrame, endFrame);
    
    jlongArray result = env->NewLongArray(2);
    jlong fill[2] = {startFrame, endFrame};
    env->SetLongArrayRegion(result, 0, 2, fill);
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeLayoutClips(JNIEnv *env, jobject thiz, jlongArray input_data, jfloat track_height, jfloat track_padding, jfloat top_offset, jfloat pixels_per_frame, jfloat scroll_x) {
    jsize len = env->GetArrayLength(input_data);
    jlong* data = env->GetLongArrayElements(input_data, nullptr);
    
    std::vector<ClipLayoutInput> inputs;
    int numClips = len / 3;
    inputs.reserve(numClips);
    
    for (int i = 0; i < numClips; ++i) {
        ClipLayoutInput input;
        input.trackIndex = static_cast<int32_t>(data[i * 3]);
        input.startFrame = data[i * 3 + 1];
        input.durationFrames = data[i * 3 + 2];
        inputs.push_back(input);
    }
    
    env->ReleaseLongArrayElements(input_data, data, JNI_ABORT);
    
    std::vector<ClipRect> rects = TimelineLayout::layoutClips(inputs, track_height, track_padding, top_offset, pixels_per_frame, scroll_x);
    
    jfloatArray result = env->NewFloatArray(rects.size() * 5);
    std::vector<jfloat> outData;
    outData.reserve(rects.size() * 5);
    
    for (const auto& rect : rects) {
        outData.push_back(static_cast<jfloat>(rect.trackIndex));
        outData.push_back(rect.left);
        outData.push_back(rect.top);
        outData.push_back(rect.right);
        outData.push_back(rect.bottom);
    }
    
    env->SetFloatArrayRegion(result, 0, outData.size(), outData.data());
    return result;
}

JNIEXPORT jobject JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeHitTestClip(JNIEnv *env, jobject thiz, jfloat pointer_x, jfloat pointer_y, jfloatArray clip_rects_data) {
    jsize len = env->GetArrayLength(clip_rects_data);
    jfloat* data = env->GetFloatArrayElements(clip_rects_data, nullptr);
    
    std::vector<ClipRect> rects;
    int numRects = len / 5;
    rects.reserve(numRects);
    
    for (int i = 0; i < numRects; ++i) {
        ClipRect r;
        r.trackIndex = static_cast<int32_t>(data[i * 5]);
        r.left = data[i * 5 + 1];
        r.top = data[i * 5 + 2];
        r.right = data[i * 5 + 3];
        r.bottom = data[i * 5 + 4];
        rects.push_back(r);
    }
    env->ReleaseFloatArrayElements(clip_rects_data, data, JNI_ABORT);
    
    HitTestResult res = TimelineHitTest::hitTestClip(pointer_x, pointer_y, rects);
    
    jclass resultClass = env->FindClass("com/example/timeline/engine/native/NativeTimelineHitResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(ZII)V");
    
    return env->NewObject(resultClass, constructor, res.hit, res.clipIndex, res.trackIndex);
}

JNIEXPORT jobject JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeSnapFrame(JNIEnv *env, jobject thiz, jlong input_frame, jlongArray target_frames, jintArray target_types, jfloat pixels_per_frame, jfloat snap_threshold_pixels) {
    jsize len = env->GetArrayLength(target_frames);
    jlong* frames = env->GetLongArrayElements(target_frames, nullptr);
    jint* types = env->GetIntArrayElements(target_types, nullptr);
    
    std::vector<SnapTarget> targets;
    targets.reserve(len);
    for (int i = 0; i < len; ++i) {
        SnapTarget t;
        t.frame = frames[i];
        t.type = types[i];
        targets.push_back(t);
    }
    
    env->ReleaseLongArrayElements(target_frames, frames, JNI_ABORT);
    env->ReleaseIntArrayElements(target_types, types, JNI_ABORT);
    
    SnapResult res = TimelineSnap::snapFrame(input_frame, targets, pixels_per_frame, snap_threshold_pixels);
    
    jclass resultClass = env->FindClass("com/example/timeline/engine/native/NativeTimelineSnapResult");
    jmethodID constructor = env->GetMethodID(resultClass, "<init>", "(ZJIF)V");
    
    return env->NewObject(resultClass, constructor, res.snapped, res.snappedFrame, res.targetType, res.distancePixels);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeCalculateThumbnailCells(JNIEnv *env, jobject thiz, jlong start_frame, jlong duration_frames, jfloat cell_width, jfloat pixels_per_frame, jlong source_in_frame) {
    std::vector<ThumbnailCell> cells = TimelineLayout::calculateThumbnailCellsForClip(start_frame, duration_frames, cell_width, pixels_per_frame, source_in_frame);
    
    jfloatArray result = env->NewFloatArray(cells.size() * 4);
    std::vector<jfloat> outData;
    outData.reserve(cells.size() * 4);
    
    for (const auto& cell : cells) {
        outData.push_back(static_cast<jfloat>(cell.cellIndex));
        outData.push_back(static_cast<jfloat>(cell.frameTime));
        outData.push_back(cell.startX);
        outData.push_back(cell.width);
    }
    
    env->SetFloatArrayRegion(result, 0, outData.size(), outData.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeCalculateRulerTicks(JNIEnv *env, jobject thiz, jfloat scroll_x, jint viewport_width, jfloat pixels_per_frame, jint frame_rate) {
    std::vector<RulerTick> ticks = TimelineLayout::calculateTimelineRulerTicks(scroll_x, viewport_width, pixels_per_frame, frame_rate);
    
    jfloatArray result = env->NewFloatArray(ticks.size() * 3);
    std::vector<jfloat> outData;
    outData.reserve(ticks.size() * 3);
    
    for (const auto& tick : ticks) {
        outData.push_back(static_cast<jfloat>(tick.frame));
        outData.push_back(tick.x);
        outData.push_back(tick.isMajor ? 1.0f : 0.0f);
    }
    
    env->SetFloatArrayRegion(result, 0, outData.size(), outData.data());
    return result;
}

static std::vector<EditClipInput> parseEditClipInputs(JNIEnv* env, jlongArray input_data) {
    jsize len = env->GetArrayLength(input_data);
    jlong* data = env->GetLongArrayElements(input_data, nullptr);
    std::vector<EditClipInput> inputs;
    int numClips = len / 6;
    inputs.reserve(numClips);
    for (int i = 0; i < numClips; ++i) {
        inputs.push_back({
            static_cast<int32_t>(data[i * 6]),
            data[i * 6 + 1],
            data[i * 6 + 2],
            data[i * 6 + 3],
            data[i * 6 + 4],
            data[i * 6 + 5]
        });
    }
    env->ReleaseLongArrayElements(input_data, data, JNI_ABORT);
    return inputs;
}

static jlongArray formatEditClipOutputs(JNIEnv* env, const std::vector<EditClipOutput>& outputs) {
    jlongArray result = env->NewLongArray(outputs.size() * 5);
    std::vector<jlong> outData;
    outData.reserve(outputs.size() * 5);
    for (const auto& out : outputs) {
        outData.push_back(out.id);
        outData.push_back(out.startFrame);
        outData.push_back(out.durationFrames);
        outData.push_back(out.sourceIn);
        outData.push_back(out.sourceOut);
    }
    env->SetLongArrayRegion(result, 0, outData.size(), outData.data());
    return result;
}

JNIEXPORT jlongArray JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeRippleEdit(JNIEnv *env, jobject thiz, jlongArray input_data, jint target_id, jboolean is_start, jlong delta_frames) {
    auto inputs = parseEditClipInputs(env, input_data);
    auto outputs = TimelineEdit::rippleEdit(inputs, target_id, is_start, delta_frames);
    return formatEditClipOutputs(env, outputs);
}

JNIEXPORT jlongArray JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeRollEdit(JNIEnv *env, jobject thiz, jlongArray input_data, jint left_id, jint right_id, jlong delta_frames) {
    auto inputs = parseEditClipInputs(env, input_data);
    auto outputs = TimelineEdit::rollEdit(inputs, left_id, right_id, delta_frames);
    return formatEditClipOutputs(env, outputs);
}

JNIEXPORT jlongArray JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeSlipEdit(JNIEnv *env, jobject thiz, jlongArray input_data, jlong delta_frames) {
    auto inputs = parseEditClipInputs(env, input_data);
    if (inputs.empty()) return env->NewLongArray(0);
    auto outputs = TimelineEdit::slipEdit(inputs[0], delta_frames);
    return formatEditClipOutputs(env, outputs);
}

JNIEXPORT jlongArray JNICALL
Java_com_example_timeline_engine_native_NativeTimelineCore_nativeSlideEdit(JNIEnv *env, jobject thiz, jlongArray input_data, jint target_id, jlong delta_frames) {
    auto inputs = parseEditClipInputs(env, input_data);
    auto outputs = TimelineEdit::slideEdit(inputs, target_id, delta_frames);
    return formatEditClipOutputs(env, outputs);
}

}
