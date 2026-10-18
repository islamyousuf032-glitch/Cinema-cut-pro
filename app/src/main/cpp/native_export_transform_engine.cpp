#include <jni.h>
#include <vector>
#include <cmath>
#include <algorithm>

namespace export_engine {

    // Helper to apply matrix and crop to a raw RGBA buffer
    void applyExportTransformRgba(
        uint8_t* outPixels,
        const uint8_t* inPixels,
        int width, int height,
        float px, float py, float rot, float sx, float sy,
        float anchorX, float anchorY,
        float cropL, float cropT, float cropR, float cropB,
        float opacity
    ) {
        // Mock software implementation of transform during export.
        // Similar to motion_blur approach but applying static transform.
        
        float rad = -rot * M_PI / 180.0f;
        float cosR = std::cos(rad);
        float sinR = std::sin(rad);

        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                float normX = static_cast<float>(x) / width;
                float normY = static_cast<float>(y) / height;

                // Inverse mapping to source
                float inNx = normX - (anchorX + px);
                float inNy = normY - (anchorY + py);
                
                float rx = inNx * cosR - inNy * sinR;
                float ry = inNx * sinR + inNy * cosR;
                
                inNx = rx / (sx != 0 ? sx : 1.0f) + anchorX;
                inNy = ry / (sy != 0 ? sy : 1.0f) + anchorY;

                // Check crop bounds
                if (inNx < cropL || inNx > (1.0f - cropR) || 
                    inNy < cropT || inNy > (1.0f - cropB)) {
                    outPixels[(y * width + x) * 4 + 3] = 0; // transparent
                    continue;
                }

                int srcX = std::round(inNx * width);
                int srcY = std::round(inNy * height);

                int outIdx = (y * width + x) * 4;
                if (srcX >= 0 && srcX < width && srcY >= 0 && srcY < height) {
                    int srcIdx = (srcY * width + srcX) * 4;
                    outPixels[outIdx]     = inPixels[srcIdx];
                    outPixels[outIdx + 1] = inPixels[srcIdx + 1];
                    outPixels[outIdx + 2] = inPixels[srcIdx + 2];
                    outPixels[outIdx + 3] = static_cast<uint8_t>(inPixels[srcIdx + 3] * opacity);
                } else {
                    outPixels[outIdx + 3] = 0;
                }
            }
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_timeline_export_NativeExportTransformEngine_nativeApplyTransformRgba8888(
    JNIEnv* env, jobject thiz,
    jbyteArray outPixels,
    jbyteArray inPixels,
    jint width, jint height,
    jfloat px, jfloat py, jfloat rot, jfloat sx, jfloat sy,
    jfloat anchorX, jfloat anchorY,
    jfloat cropL, jfloat cropT, jfloat cropR, jfloat cropB,
    jfloat opacity
) {
    jbyte* outBuf = env->GetByteArrayElements(outPixels, nullptr);
    jbyte* inBuf = env->GetByteArrayElements(inPixels, nullptr);

    export_engine::applyExportTransformRgba(
        reinterpret_cast<uint8_t*>(outBuf),
        reinterpret_cast<const uint8_t*>(inBuf),
        width, height,
        px, py, rot, sx, sy,
        anchorX, anchorY,
        cropL, cropT, cropR, cropB,
        opacity
    );

    env->ReleaseByteArrayElements(outPixels, outBuf, 0); 
    env->ReleaseByteArrayElements(inPixels, inBuf, JNI_ABORT);
}
