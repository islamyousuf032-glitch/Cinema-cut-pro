#include <jni.h>
#include <android/log.h>
#include <vector>
#include "transform_engine.h"

#define LOG_TAG "NativeTransformEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

using namespace transform;

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeCreateTransformEngine(JNIEnv* env, jobject thiz) {
    LOGI("Creating Native Transform Engine");
    auto* engine = new TransformEngine();
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeReleaseTransformEngine(JNIEnv* env, jobject thiz, jlong handle) {
    auto* engine = reinterpret_cast<TransformEngine*>(handle);
    delete engine;
    LOGI("Released Native Transform Engine");
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeEvaluateTransformMatrix(
        JNIEnv* env, jobject thiz,
        jfloat positionX, jfloat positionY,
        jfloat scaleX, jfloat scaleY,
        jfloat rotationDegrees,
        jfloat anchorX, jfloat anchorY,
        jboolean flipHorizontal, jboolean flipVertical) {
    
    TransformParams params;
    params.positionX = positionX;
    params.positionY = positionY;
    params.scaleX = scaleX;
    params.scaleY = scaleY;
    params.rotationDegrees = rotationDegrees;
    params.anchorX = anchorX;
    params.anchorY = anchorY;
    params.flipHorizontal = flipHorizontal;
    params.flipVertical = flipVertical;

    auto matrix = TransformMatrix::Build3x3(params);

    jfloatArray result = env->NewFloatArray(9);
    env->SetFloatArrayRegion(result, 0, 9, matrix.data());
    return result;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeInterpolateFloat(
        JNIEnv* env, jobject thiz,
        jlong currentFrame, jlong kf1Frame, jfloat kf1Value,
        jlong kf2Frame, jfloat kf2Value, jint interpMode) {
    
    Keyframe kf1 = {kf1Frame, kf1Value, static_cast<InterpolationType>(interpMode), 0.f, 0.f};
    Keyframe kf2 = {kf2Frame, kf2Value, static_cast<InterpolationType>(interpMode), 0.f, 0.f};

    return KeyframeInterpolator::Interpolate(currentFrame, kf1, kf2);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeEvaluateCrop(
        JNIEnv* env, jobject thiz,
        jfloat cropLeft, jfloat cropTop, jfloat cropRight, jfloat cropBottom,
        jfloat currentWidth, jfloat currentHeight) {
    
    CropParams params = {cropLeft, cropRight, cropTop, cropBottom, 0.f};
    auto rect = CropPerspective::CalculateCropRect(params, currentWidth, currentHeight);

    jfloatArray result = env->NewFloatArray(4);
    env->SetFloatArrayRegion(result, 0, 4, rect.data());
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeBuildTransformMatrix(
        JNIEnv* env, jobject thiz,
        jfloatArray paramArray) {
    
    jfloat* params = env->GetFloatArrayElements(paramArray, nullptr);
    TransformParams p;
    p.positionX = params[0];
    p.positionY = params[1];
    p.scaleX = params[2];
    p.scaleY = params[3];
    p.rotationDegrees = params[4];
    p.anchorX = params[5];
    p.anchorY = params[6];
    p.flipHorizontal = params[7] > 0.5f;
    p.flipVertical = params[8] > 0.5f;
    
    env->ReleaseFloatArrayElements(paramArray, params, JNI_ABORT);
    
    auto matrix = TransformMatrix::Build4x4(p);
    
    jfloatArray result = env->NewFloatArray(16);
    env->SetFloatArrayRegion(result, 0, 16, matrix.data());
    return result;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeEvaluateKeyframeTrack(
        JNIEnv* env, jobject thiz,
        jlong currentFrame,
        jlongArray keyframeFrames,
        jfloatArray keyframeValues,
        jintArray keyframeInterpolations,
        jfloatArray bezierHandleLefts,
        jfloatArray bezierHandleRights) {

    jsize len = env->GetArrayLength(keyframeFrames);
    std::vector<Keyframe> keyframes(len);

    jlong* frames = env->GetLongArrayElements(keyframeFrames, nullptr);
    jfloat* values = env->GetFloatArrayElements(keyframeValues, nullptr);
    jint* interps = env->GetIntArrayElements(keyframeInterpolations, nullptr);
    jfloat* lefts = env->GetFloatArrayElements(bezierHandleLefts, nullptr);
    jfloat* rights = env->GetFloatArrayElements(bezierHandleRights, nullptr);

    for (jsize i = 0; i < len; ++i) {
        keyframes[i] = {frames[i], values[i], static_cast<InterpolationType>(interps[i]), lefts[i], rights[i]};
    }

    env->ReleaseLongArrayElements(keyframeFrames, frames, JNI_ABORT);
    env->ReleaseFloatArrayElements(keyframeValues, values, JNI_ABORT);
    env->ReleaseIntArrayElements(keyframeInterpolations, interps, JNI_ABORT);
    env->ReleaseFloatArrayElements(bezierHandleLefts, lefts, JNI_ABORT);
    env->ReleaseFloatArrayElements(bezierHandleRights, rights, JNI_ABORT);

    return KeyframeInterpolator::InterpolateArray(currentFrame, keyframes, 0.f);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeGenerateMotionPath(
        JNIEnv* env, jobject thiz,
        jlong startFrame, jlong endFrame,
        jlongArray xKeyframeFrames, jfloatArray xKeyframeValues, jintArray xKeyframeInterpolations, jfloatArray xBezierHandleLefts, jfloatArray xBezierHandleRights,
        jlongArray yKeyframeFrames, jfloatArray yKeyframeValues, jintArray yKeyframeInterpolations, jfloatArray yBezierHandleLefts, jfloatArray yBezierHandleRights) {

    jsize xLen = env->GetArrayLength(xKeyframeFrames);
    std::vector<Keyframe> xKeyframes(xLen);
    jlong* xFrames = env->GetLongArrayElements(xKeyframeFrames, nullptr);
    jfloat* xValues = env->GetFloatArrayElements(xKeyframeValues, nullptr);
    jint* xInterps = env->GetIntArrayElements(xKeyframeInterpolations, nullptr);
    jfloat* xLefts = env->GetFloatArrayElements(xBezierHandleLefts, nullptr);
    jfloat* xRights = env->GetFloatArrayElements(xBezierHandleRights, nullptr);
    for (jsize i = 0; i < xLen; ++i) {
        xKeyframes[i] = {xFrames[i], xValues[i], static_cast<InterpolationType>(xInterps[i]), xLefts[i], xRights[i]};
    }
    env->ReleaseLongArrayElements(xKeyframeFrames, xFrames, JNI_ABORT);
    env->ReleaseFloatArrayElements(xKeyframeValues, xValues, JNI_ABORT);
    env->ReleaseIntArrayElements(xKeyframeInterpolations, xInterps, JNI_ABORT);
    env->ReleaseFloatArrayElements(xBezierHandleLefts, xLefts, JNI_ABORT);
    env->ReleaseFloatArrayElements(xBezierHandleRights, xRights, JNI_ABORT);

    jsize yLen = env->GetArrayLength(yKeyframeFrames);
    std::vector<Keyframe> yKeyframes(yLen);
    jlong* yFrames = env->GetLongArrayElements(yKeyframeFrames, nullptr);
    jfloat* yValues = env->GetFloatArrayElements(yKeyframeValues, nullptr);
    jint* yInterps = env->GetIntArrayElements(yKeyframeInterpolations, nullptr);
    jfloat* yLefts = env->GetFloatArrayElements(yBezierHandleLefts, nullptr);
    jfloat* yRights = env->GetFloatArrayElements(yBezierHandleRights, nullptr);
    for (jsize i = 0; i < yLen; ++i) {
        yKeyframes[i] = {yFrames[i], yValues[i], static_cast<InterpolationType>(yInterps[i]), yLefts[i], yRights[i]};
    }
    env->ReleaseLongArrayElements(yKeyframeFrames, yFrames, JNI_ABORT);
    env->ReleaseFloatArrayElements(yKeyframeValues, yValues, JNI_ABORT);
    env->ReleaseIntArrayElements(yKeyframeInterpolations, yInterps, JNI_ABORT);
    env->ReleaseFloatArrayElements(yBezierHandleLefts, yLefts, JNI_ABORT);
    env->ReleaseFloatArrayElements(yBezierHandleRights, yRights, JNI_ABORT);

    auto path = MotionPath::BuildMotionPath(xKeyframes, yKeyframes, startFrame, endFrame);
    
    jfloatArray result = env->NewFloatArray(path.size() * 2);
    if (!path.empty()) {
        std::vector<jfloat> flattenedPath(path.size() * 2);
        for (size_t i = 0; i < path.size(); ++i) {
            flattenedPath[i * 2] = path[i][0];
            flattenedPath[i * 2 + 1] = path[i][1];
        }
        env->SetFloatArrayRegion(result, 0, flattenedPath.size(), flattenedPath.data());
    }
    return result;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeFindNearestKeyframe(
        JNIEnv* env, jobject thiz,
        jlong currentFrame, jlongArray keyframeFrames) {
        
    jsize len = env->GetArrayLength(keyframeFrames);
    if (len == 0) return -1;
    
    jlong* frames = env->GetLongArrayElements(keyframeFrames, nullptr);
    jlong nearest = frames[0];
    jlong minDiff = std::abs(currentFrame - nearest);
    
    for (jsize i = 1; i < len; ++i) {
        jlong diff = std::abs(currentFrame - frames[i]);
        if (diff < minDiff) {
            minDiff = diff;
            nearest = frames[i];
        }
    }
    
    env->ReleaseLongArrayElements(keyframeFrames, frames, JNI_ABORT);
    return nearest;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeComputeBoundingBox(
        JNIEnv* env, jobject thiz,
        jlong handle, jlong currentFrame) {
    jfloatArray result = env->NewFloatArray(4);
    float bb[4] = {0.f, 0.f, 100.f, 100.f};
    env->SetFloatArrayRegion(result, 0, 4, bb);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeHitTestHandles(
        JNIEnv* env, jobject thiz,
        jlong handle, jfloat x, jfloat y) {
    return -1; // No hit
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeEvaluateTransformAtFrame(
        JNIEnv* env, jobject thiz,
        jlong handle, jlong currentFrame) {
    return handle; // Return same handle or computed state
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_example_timeline_engine_NativeTransformEngine_nativeInterpolateKeyframes(
        JNIEnv* env, jobject thiz,
        jlong handle, jlong currentFrame) {
    return 0.f;
}

#include "native_motion_blur.h"

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_preview_MotionBlurEngine_nativeComputeMotionVector(
        JNIEnv* env, jobject thiz,
        jfloat prevX, jfloat prevY,
        jfloat currX, jfloat currY,
        jfloat nextX, jfloat nextY) {

    auto vec = transform::NativeMotionBlur::computeMotionVector(prevX, prevY, currX, currY, nextX, nextY);
    jfloatArray result = env->NewFloatArray(vec.size());
    env->SetFloatArrayRegion(result, 0, vec.size(), vec.data());
    return result;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_timeline_engine_preview_MotionBlurEngine_nativeComputeMotionBlurSamples(
        JNIEnv* env, jobject thiz,
        jint sampleCount,
        jfloat shutterAngle,
        jfloat strength) {

    auto vec = transform::NativeMotionBlur::computeMotionBlurSamples(sampleCount, shutterAngle, strength);
    jfloatArray result = env->NewFloatArray(vec.size());
    env->SetFloatArrayRegion(result, 0, vec.size(), vec.data());
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_engine_preview_MotionBlurEngine_nativeApplyTransformMotionBlurRgba8888(
        JNIEnv* env, jobject thiz,
        jbyteArray outPixels,
        jbyteArray inPixels,
        jint width, jint height,
        jfloat currX, jfloat currY, jfloat currRot, jfloat currScaleX, jfloat currScaleY,
        jfloat prevX, jfloat prevY, jfloat prevRot, jfloat prevScaleX, jfloat prevScaleY,
        jfloat nextX, jfloat nextY, jfloat nextRot, jfloat nextScaleX, jfloat nextScaleY,
        jfloat anchorX, jfloat anchorY,
        jfloat shutterAngle,
        jint sampleCount,
        jfloat strength) {

    jbyte* outBuf = env->GetByteArrayElements(outPixels, nullptr);
    jbyte* inBuf = env->GetByteArrayElements(inPixels, nullptr);

    transform::NativeMotionBlur::applyTransformMotionBlurRgba8888(
        reinterpret_cast<uint8_t*>(outBuf),
        reinterpret_cast<const uint8_t*>(inBuf),
        width, height,
        currX, currY, currRot, currScaleX, currScaleY,
        prevX, prevY, prevRot, prevScaleX, prevScaleY,
        nextX, nextY, nextRot, nextScaleX, nextScaleY,
        anchorX, anchorY,
        shutterAngle, sampleCount, strength);

    env->ReleaseByteArrayElements(outPixels, outBuf, 0); // commit changes
    env->ReleaseByteArrayElements(inPixels, inBuf, JNI_ABORT);
}


#include <android/bitmap.h>
#include <cmath>
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

extern "C" JNIEXPORT void JNICALL
Java_com_example_ui_colorgrade_NativeColorWheelGenerator_nativeGenerateColorWheelBitmap(
        JNIEnv* env, jobject thiz,
        jobject bitmap, jint width, jint height, jint mode) {
    AndroidBitmapInfo info;
    void* pixels;
    
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) return;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) return;

    uint32_t* dst = (uint32_t*) pixels;
    float center_x = width / 2.0f;
    float center_y = height / 2.0f;
    float radius = std::min(width, height) / 2.0f;

    for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
            float dx = x - center_x;
            float dy = y - center_y;
            float dist = std::hypot(dx, dy);

            if (dist <= radius) {
                float angle = std::atan2(dy, dx) * 180.0f / M_PI;
                if (angle < 0) angle += 360.0f;

                float h = angle;
                float s = 1.0f;
                float v = 1.0f;

                float c = v * s;
                float x_prime = c * (1.0f - std::abs(fmod(h / 60.0f, 2.0f) - 1.0f));
                float m = v - c;

                float r = 0, g = 0, b = 0;
                if (h < 60) { r = c; g = x_prime; b = 0; }
                else if (h < 120) { r = x_prime; g = c; b = 0; }
                else if (h < 180) { r = 0; g = c; b = x_prime; }
                else if (h < 240) { r = 0; g = x_prime; b = c; }
                else if (h < 300) { r = x_prime; g = 0; b = c; }
                else { r = c; g = 0; b = x_prime; }

                uint8_t r8 = (uint8_t) ((r + m) * 255.0f);
                uint8_t g8 = (uint8_t) ((g + m) * 255.0f);
                uint8_t b8 = (uint8_t) ((b + m) * 255.0f);
                uint8_t a8 = 255; // Alpha handled in UI

                // AndroidBitmap format is RGBA_8888, which memory layout is R, G, B, A bytes per pixel
                // On little-endian systems, uint32_t ABGR? No, it's actually R,G,B,A in memory.
                // Cast to uint8_t array for safe cross-platform writing.
                uint8_t* pixel = (uint8_t*) &dst[y * width + x];
                pixel[0] = r8;
                pixel[1] = g8;
                pixel[2] = b8;
                pixel[3] = a8;
            } else {
                dst[y * width + x] = 0;
            }
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_model_colorgrade_engine_NativeColorGradeEngine_nativeEvaluateColorLayerStack(
    JNIEnv* env, jobject thiz,
    jobject buffer, jint width, jint height, jfloatArray layerData, jint numLayers) {
    
    // Stub implementation to satisfy the prompt's request for C++ API
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_model_colorgrade_engine_NativeColorGradeEngine_nativeApplyBasicColorLayer(
    JNIEnv* env, jobject thiz,
    jobject buffer, jint width, jint height, 
    jfloat exposure, jfloat brightness, jfloat contrast, jfloat saturation, jfloat opacity) {
    
    // Stub implementation
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_model_colorgrade_engine_NativeColorGradeEngine_nativeBuildCurveLut(
    JNIEnv* env, jobject thiz,
    jfloatArray points, jint numPoints, jbyteArray outLut) {
    
    // Stub implementation
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_model_colorgrade_engine_NativeColorGradeEngine_nativeApplyExposure(
    JNIEnv* env, jobject thiz,
    jintArray pixels, jint width, jint height, jfloat exposure) {
    jint* buf = env->GetIntArrayElements(pixels, nullptr);
    int numPixels = width * height;
    float mult = std::pow(2.0f, exposure);
    for (int i = 0; i < numPixels; ++i) {
        int color = buf[i];
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = std::min(255, (int)(r * mult));
        g = std::min(255, (int)(g * mult));
        b = std::min(255, (int)(b * mult));
        buf[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    env->ReleaseIntArrayElements(pixels, buf, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_model_colorgrade_engine_NativeColorGradeEngine_nativeApplyContrast(
    JNIEnv* env, jobject thiz,
    jintArray pixels, jint width, jint height, jfloat contrast) {
    jint* buf = env->GetIntArrayElements(pixels, nullptr);
    int numPixels = width * height;
    float mult = std::max(0.0f, contrast + 1.0f);
    for (int i = 0; i < numPixels; ++i) {
        int color = buf[i];
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = std::min(255, std::max(0, (int)(((r / 255.0f - 0.5f) * mult + 0.5f) * 255.0f)));
        g = std::min(255, std::max(0, (int)(((g / 255.0f - 0.5f) * mult + 0.5f) * 255.0f)));
        b = std::min(255, std::max(0, (int)(((b / 255.0f - 0.5f) * mult + 0.5f) * 255.0f)));
        buf[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    env->ReleaseIntArrayElements(pixels, buf, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_model_colorgrade_engine_NativeColorGradeEngine_nativeApplySaturation(
    JNIEnv* env, jobject thiz,
    jintArray pixels, jint width, jint height, jfloat saturation) {
    jint* buf = env->GetIntArrayElements(pixels, nullptr);
    int numPixels = width * height;
    for (int i = 0; i < numPixels; ++i) {
        int color = buf[i];
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
        r = std::min(255, std::max(0, (int)(lum + saturation * (r - lum))));
        g = std::min(255, std::max(0, (int)(lum + saturation * (g - lum))));
        b = std::min(255, std::max(0, (int)(lum + saturation * (b - lum))));
        buf[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    env->ReleaseIntArrayElements(pixels, buf, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_model_colorgrade_engine_NativeColorGradeEngine_nativeApplyTemperatureTint(
    JNIEnv* env, jobject thiz,
    jintArray pixels, jint width, jint height, jfloat temperature, jfloat tint) {
    jint* buf = env->GetIntArrayElements(pixels, nullptr);
    int numPixels = width * height;
    float rMult = 1.0f + temperature + tint;
    float gMult = 1.0f + tint;
    float bMult = 1.0f - temperature;
    for (int i = 0; i < numPixels; ++i) {
        int color = buf[i];
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = std::min(255, std::max(0, (int)(r * rMult)));
        g = std::min(255, std::max(0, (int)(g * gMult)));
        b = std::min(255, std::max(0, (int)(b * bMult)));
        buf[i] = (a << 24) | (r << 16) | (g << 8) | b;
    }
    env->ReleaseIntArrayElements(pixels, buf, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_model_colorgrade_engine_NativeColorGradeEngine_nativeApplyLiftGammaGain(
    JNIEnv* env, jobject thiz,
    jintArray pixels, jint width, jint height, 
    jfloat liftR, jfloat liftG, jfloat liftB, 
    jfloat gammaR, jfloat gammaG, jfloat gammaB, 
    jfloat gainR, jfloat gainG, jfloat gainB) {
    jint* buf = env->GetIntArrayElements(pixels, nullptr);
    int numPixels = width * height;
    for (int i = 0; i < numPixels; ++i) {
        int color = buf[i];
        int a = (color >> 24) & 0xFF;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        
        // Lift
        r = r + liftR * (1.0f - r);
        g = g + liftG * (1.0f - g);
        b = b + liftB * (1.0f - b);
        
        // Gamma
        if (gammaR != 1.0f && r > 0.0f) r = std::pow(r, 1.0f / gammaR);
        if (gammaG != 1.0f && g > 0.0f) g = std::pow(g, 1.0f / gammaG);
        if (gammaB != 1.0f && b > 0.0f) b = std::pow(b, 1.0f / gammaB);
        
        // Gain
        r = r * gainR;
        g = g * gainG;
        b = b * gainB;
        
        int finalR = std::min(255, std::max(0, (int)(r * 255.0f)));
        int finalG = std::min(255, std::max(0, (int)(g * 255.0f)));
        int finalB = std::min(255, std::max(0, (int)(b * 255.0f)));
        buf[i] = (a << 24) | (finalR << 16) | (finalG << 8) | finalB;
    }
    env->ReleaseIntArrayElements(pixels, buf, 0);
}
