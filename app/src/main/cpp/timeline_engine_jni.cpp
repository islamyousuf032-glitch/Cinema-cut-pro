#include <jni.h>
#include "timeline_math.h"

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_timeline_engine_TimelineMathEngine_nativeFrameToPixel(JNIEnv* env, jobject thiz, jlong frame, jfloat pixelsPerFrame) {
    return TimelineMath::frameToPixel(frame, pixelsPerFrame);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_timeline_engine_TimelineMathEngine_nativePixelToFrame(JNIEnv* env, jobject thiz, jfloat pixel, jfloat pixelsPerFrame) {
    return TimelineMath::pixelToFrame(pixel, pixelsPerFrame);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_timeline_engine_TimelineMathEngine_nativeIsFrameVisible(JNIEnv* env, jobject thiz, jlong frame, jlong startVisibleFrame, jlong endVisibleFrame) {
    return TimelineMath::isFrameVisible(frame, startVisibleFrame, endVisibleFrame) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_timeline_engine_TimelineMathEngine_nativeCalculateSnapPoint(JNIEnv* env, jobject thiz, jlong dragFrame, jlongArray snapPoints, jlong thresholdFrames) {
    if (!snapPoints) return dragFrame;
    
    jsize len = env->GetArrayLength(snapPoints);
    jlong* elements = env->GetLongArrayElements(snapPoints, nullptr);
    
    std::vector<long> pts(elements, elements + len);
    env->ReleaseLongArrayElements(snapPoints, elements, JNI_ABORT);
    
    return TimelineMath::calculateSnapPoint(dragFrame, pts, thresholdFrames);
}
