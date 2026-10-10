#include <jni.h>
#include <vector>
#include "advanced_timeline_core.h"
#include "timeline_layout_engine.h"
#include "timeline_hit_test.h"
#include "timeline_snap_engine.h"
#include "timeline_edit_math.h"

extern "C" {

JNIEXPORT jfloat JNICALL
Java_com_example_timeline_engine_native_advanced_NativeAdvancedTimelineCore_nativeFrameToX(JNIEnv *env, jobject thiz, jlong frame, jfloat pixels_per_frame) {
    return AdvancedTimelineCore::frameToX(frame, pixels_per_frame);
}

JNIEXPORT jlong JNICALL
Java_com_example_timeline_engine_native_advanced_NativeAdvancedTimelineCore_nativeXToFrame(JNIEnv *env, jobject thiz, jfloat x, jfloat pixels_per_frame) {
    return AdvancedTimelineCore::xToFrame(x, pixels_per_frame);
}

JNIEXPORT jlongArray JNICALL
Java_com_example_timeline_engine_native_advanced_NativeAdvancedTimelineCore_nativeCalculateVisibleFrameRange(JNIEnv *env, jobject thiz, jfloat scroll_x, jint viewport_width, jfloat pixels_per_frame) {
    int64_t startFrame = 0;
    int64_t endFrame = 0;
    AdvancedTimelineCore::calculateVisibleFrameRange(scroll_x, viewport_width, pixels_per_frame, startFrame, endFrame);
    jlongArray result = env->NewLongArray(2);
    jlong fill[2] = {startFrame, endFrame};
    env->SetLongArrayRegion(result, 0, 2, fill);
    return result;
}

JNIEXPORT jfloat JNICALL
Java_com_example_timeline_engine_native_advanced_NativeAdvancedTimelineCore_nativeCalculatePlayheadX(JNIEnv *env, jobject thiz, jlong current_frame, jfloat scroll_x, jfloat pixels_per_frame) {
    return AdvancedTimelineCore::calculatePlayheadX(current_frame, scroll_x, pixels_per_frame);
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_native_advanced_NativeAdvancedTimelineCore_nativeLayoutClipRects(JNIEnv *env, jobject thiz, jlongArray input_data, jfloat track_height, jfloat track_padding, jfloat top_offset, jfloat pixels_per_frame, jfloat scroll_x) {
    jsize len = env->GetArrayLength(input_data);
    jlong* data = env->GetLongArrayElements(input_data, nullptr);
    
    std::vector<ClipLayoutInputData> inputs;
    int numClips = len / 4;
    inputs.reserve(numClips);
    for (int i = 0; i < numClips; ++i) {
        inputs.push_back({
            static_cast<int32_t>(data[i * 4]),
            static_cast<int32_t>(data[i * 4 + 1]),
            static_cast<int64_t>(data[i * 4 + 2]),
            static_cast<int64_t>(data[i * 4 + 3])
        });
    }
    env->ReleaseLongArrayElements(input_data, data, JNI_ABORT);

    std::vector<ClipLayoutRect> rects = TimelineLayoutEngine::layoutClipRects(inputs, track_height, track_padding, top_offset, pixels_per_frame, scroll_x);
    
    jfloatArray result = env->NewFloatArray(rects.size() * 5);
    std::vector<jfloat> flat_rects;
    flat_rects.reserve(rects.size() * 5);
    for (const auto& r : rects) {
        flat_rects.push_back(static_cast<float>(r.id));
        flat_rects.push_back(r.left);
        flat_rects.push_back(r.top);
        flat_rects.push_back(r.right);
        flat_rects.push_back(r.bottom);
    }
    env->SetFloatArrayRegion(result, 0, flat_rects.size(), flat_rects.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_native_advanced_NativeAdvancedTimelineCore_nativeLayoutTrackRects(JNIEnv *env, jobject thiz, jint num_tracks, jfloat viewport_width, jfloat track_height, jfloat track_padding, jfloat top_offset) {
    std::vector<TrackLayoutRect> rects = TimelineLayoutEngine::layoutTrackRects(num_tracks, viewport_width, track_height, track_padding, top_offset);
    
    jfloatArray result = env->NewFloatArray(rects.size() * 5);
    std::vector<jfloat> flat_rects;
    flat_rects.reserve(rects.size() * 5);
    for (const auto& r : rects) {
        flat_rects.push_back(static_cast<float>(r.trackIndex));
        flat_rects.push_back(r.left);
        flat_rects.push_back(r.top);
        flat_rects.push_back(r.right);
        flat_rects.push_back(r.bottom);
    }
    env->SetFloatArrayRegion(result, 0, flat_rects.size(), flat_rects.data());
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_native_advanced_NativeAdvancedTimelineCore_nativeCalculateRulerTicks(JNIEnv *env, jobject thiz, jfloat scroll_x, jint viewport_width, jfloat pixels_per_frame, jint frame_rate) {
    std::vector<AdvancedRulerTick> ticks = TimelineLayoutEngine::calculateTimelineRulerTicks(scroll_x, viewport_width, pixels_per_frame, frame_rate);
    jfloatArray result = env->NewFloatArray(ticks.size() * 2);
    std::vector<jfloat> flat_ticks;
    flat_ticks.reserve(ticks.size() * 2);
    for (const auto& t : ticks) {
        flat_ticks.push_back(t.x);
        flat_ticks.push_back(static_cast<float>(t.type));
    }
    env->SetFloatArrayRegion(result, 0, flat_ticks.size(), flat_ticks.data());
    return result;
}

}

extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_native_advanced_NativeAdvancedTimelineCore_nativeHitTestClip(JNIEnv *env, jobject thiz, jfloat pointer_x, jfloat pointer_y, jfloatArray clip_rects_data) {
    jsize len = env->GetArrayLength(clip_rects_data);
    jfloat* data = env->GetFloatArrayElements(clip_rects_data, nullptr);
    
    std::vector<ClipLayoutRect> clipRects;
    int numClips = len / 5;
    clipRects.reserve(numClips);
    for (int i = 0; i < numClips; ++i) {
        clipRects.push_back({
            static_cast<int32_t>(data[i * 5]),
            data[i * 5 + 1],
            data[i * 5 + 2],
            data[i * 5 + 3],
            data[i * 5 + 4]
        });
    }
    env->ReleaseFloatArrayElements(clip_rects_data, data, JNI_ABORT);

    AdvHitTestResult hit = AdvancedTimelineHitTest::hitTestClip(pointer_x, pointer_y, clipRects);
    jfloatArray result = env->NewFloatArray(3);
    jfloat resultData[3] = {hit.hit ? 1.0f : 0.0f, static_cast<float>(hit.clipId), static_cast<float>(hit.trackIndex)};
    env->SetFloatArrayRegion(result, 0, 3, resultData);
    return result;
}

JNIEXPORT jlong JNICALL
Java_com_example_timeline_engine_native_advanced_NativeAdvancedTimelineCore_nativeSnapFrame(JNIEnv *env, jobject thiz, jlong input_frame, jlongArray snap_targets, jlong threshold_frames) {
    jsize len = env->GetArrayLength(snap_targets);
    jlong* data = env->GetLongArrayElements(snap_targets, nullptr);
    
    std::vector<int64_t> targets;
    targets.reserve(len);
    for (int i = 0; i < len; ++i) {
        targets.push_back(data[i]);
    }
    env->ReleaseLongArrayElements(snap_targets, data, JNI_ABORT);

    return TimelineSnapEngine::snapFrame(input_frame, targets, threshold_frames);
}

}
