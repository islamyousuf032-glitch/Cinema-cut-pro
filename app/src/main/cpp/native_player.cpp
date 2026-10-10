#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "NativePlayerBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#ifndef HAS_FFMPEG
#define ERROR_FFMPEG_MISSING "FFmpeg library is missing. Please build with correct NDK dependencies."
#endif

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativeCreate(JNIEnv *env, jobject thiz, jobject surface) {
    LOGI("nativeCreate called");
    return 0; // Return dummy handle
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativeOpen(JNIEnv *env, jobject thiz, jlong handle, jstring path) {
#ifndef HAS_FFMPEG
    LOGE(ERROR_FFMPEG_MISSING);
    return -1; // DECODER_OPEN_FAILED
#else
    return 0;
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativePlay(JNIEnv *env, jobject thiz, jlong handle) {}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativePause(JNIEnv *env, jobject thiz, jlong handle) {}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativeSeek(JNIEnv *env, jobject thiz, jlong handle, jlong timeUs) {}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativeSetPreviewMaxSize(JNIEnv *env, jobject thiz, jlong handle, jint maxWidth, jint maxHeight) {}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativeSetFitMode(JNIEnv *env, jobject thiz, jlong handle, jint fitMode) {}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativeGetDurationUs(JNIEnv *env, jobject thiz, jlong handle) { return 0; }

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativeGetPositionUs(JNIEnv *env, jobject thiz, jlong handle) { return 0; }

extern "C" JNIEXPORT jint JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativeGetVideoWidth(JNIEnv *env, jobject thiz, jlong handle) { return 0; }

extern "C" JNIEXPORT jint JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativeGetVideoHeight(JNIEnv *env, jobject thiz, jlong handle) { return 0; }

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativeGetLastError(JNIEnv *env, jobject thiz, jlong handle) {
#ifndef HAS_FFMPEG
    return env->NewStringUTF(ERROR_FFMPEG_MISSING);
#else
    return env->NewStringUTF("Unknown error");
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_engine_preview_native_NativePlayerBridge_nativeRelease(JNIEnv *env, jobject thiz, jlong handle) {}
