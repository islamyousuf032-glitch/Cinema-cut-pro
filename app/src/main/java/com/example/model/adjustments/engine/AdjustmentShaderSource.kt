package com.example.model.adjustments.engine

object AdjustmentShaderSource {
    const val VERTEX_SHADER = """#version 300 es
        in vec4 aFramePosition;
        in vec4 aTexCoords;
        out vec2 vTexCoords;
        void main() {
            gl_Position = aFramePosition;
            vTexCoords = aTexCoords.xy;
        }
    """

    const val FRAGMENT_SHADER = """#version 300 es
        precision highp float;
        
        uniform sampler2D texSampler;
        
        in vec2 vTexCoords;
        out vec4 outColor;
        
        // Light
        uniform float uExposure; 
        uniform float uBrightness;
        uniform float uContrast;
        uniform float uHighlights;
        uniform float uShadows;
        uniform float uWhites;
        uniform float uBlacks;
        uniform float uFade;
        
        // Professional Color Wheels
        uniform float uLiftR;
        uniform float uLiftG;
        uniform float uLiftB;
        uniform float uGammaR;
        uniform float uGammaG;
        uniform float uGammaB;
        uniform float uGainR;
        uniform float uGainG;
        uniform float uGainB;
        uniform float uOffsetWheelR;
        uniform float uOffsetWheelG;
        uniform float uOffsetWheelB;
        
        // Skin Tone Protection
        uniform float uSkinToneProtection;
        uniform int uSkinToneShowMask;
        uniform float uSkinToneHueCenter;
        uniform float uSkinToneHueWidth;

        // HSL
        uniform float uHslHue0;
        uniform float uHslHue1;
        uniform float uHslHue2;
        uniform float uHslHue3;
        uniform float uHslHue4;
        uniform float uHslHue5;
        uniform float uHslHue6;
        uniform float uHslHue7;

        uniform float uHslSat0;
        uniform float uHslSat1;
        uniform float uHslSat2;
        uniform float uHslSat3;
        uniform float uHslSat4;
        uniform float uHslSat5;
        uniform float uHslSat6;
        uniform float uHslSat7;

        uniform float uHslLum0;
        uniform float uHslLum1;
        uniform float uHslLum2;
        uniform float uHslLum3;
        uniform float uHslLum4;
        uniform float uHslLum5;
        uniform float uHslLum6;
        uniform float uHslLum7;
        
        float getHslHue(int idx) {
            if (idx == 0) return uHslHue0; if (idx == 1) return uHslHue1; if (idx == 2) return uHslHue2; if (idx == 3) return uHslHue3;
            if (idx == 4) return uHslHue4; if (idx == 5) return uHslHue5; if (idx == 6) return uHslHue6; return uHslHue7;
        }
        float getHslSat(int idx) {
            if (idx == 0) return uHslSat0; if (idx == 1) return uHslSat1; if (idx == 2) return uHslSat2; if (idx == 3) return uHslSat3;
            if (idx == 4) return uHslSat4; if (idx == 5) return uHslSat5; if (idx == 6) return uHslSat6; return uHslSat7;
        }
        float getHslLum(int idx) {
            if (idx == 0) return uHslLum0; if (idx == 1) return uHslLum1; if (idx == 2) return uHslLum2; if (idx == 3) return uHslLum3;
            if (idx == 4) return uHslLum4; if (idx == 5) return uHslLum5; if (idx == 6) return uHslLum6; return uHslLum7;
        }
        
        // LUT
        uniform int uLutEnabled;
        uniform float uLutIntensity;
        uniform float uLutSize;
        uniform sampler2D uLutTexture;
        
        // HSL Qualifier
        uniform int uQualEnabled;
        uniform float uQualHueCenter;
        uniform float uQualHueWidth;
        uniform float uQualHueFeather;
        uniform float uQualSatMin;
        uniform float uQualSatMax;
        uniform float uQualSatFeather;
        uniform float uQualLumMin;
        uniform float uQualLumMax;
        uniform float uQualLumFeather;
        uniform int uQualInvert;
        uniform int uQualShowMatte;
        
        uniform float uQualHueShift;
        uniform float uQualSat;
        uniform float uQualLum;
        uniform float uQualContrast;
        uniform float uQualTemp;
        uniform float uQualTint;

        // Color
        uniform float uSaturation;
        uniform float uVibrance;
        uniform float uTemperature;
        uniform float uTint;
        
        // Effects
        uniform float uVignetteAmount;
        uniform float uVignetteMidpoint;
        uniform float uVignetteFeather;
        uniform float uVignetteRoundness;
        uniform float uGrainAmount;
        uniform float uGrainSize;
        uniform float uGrainRoughness;
        uniform float uTime; 
        
        // RGB
        uniform float uChannelMixerRR;
        uniform float uChannelMixerRG;
        uniform float uChannelMixerRB;
        uniform float uChannelMixerGR;
        uniform float uChannelMixerGG;
        uniform float uChannelMixerGB;
        uniform float uChannelMixerBR;
        uniform float uChannelMixerBG;
        uniform float uChannelMixerBB;
        uniform float uChannelMultiplierR;
        uniform float uChannelMultiplierG;
        uniform float uChannelMultiplierB;
        uniform float uChannelOffsetR;
        uniform float uChannelOffsetG;
        uniform float uChannelOffsetB;
        
        // Color Balance
        uniform float uShadowBalanceR;
        uniform float uShadowBalanceG;
        uniform float uShadowBalanceB;
        uniform float uMidtoneBalanceR;
        uniform float uMidtoneBalanceG;
        uniform float uMidtoneBalanceB;
        uniform float uHighlightBalanceR;
        uniform float uHighlightBalanceG;
        uniform float uHighlightBalanceB;
        uniform float uShadowLumaMin;
        uniform float uShadowLumaMax;
        uniform float uHighlightLumaMin;
        uniform float uHighlightLumaMax;

        // Detail
        uniform float uDehaze;
        uniform float uClarity;
        uniform float uStructure;
        uniform float uSharpness;
        uniform float uResolutionX;
        uniform float uResolutionY;
        
        // Input Color Management
        uniform int uInputLogType; // 0=None, 1=SLog3, 2=CLog, 3=VLog, 4=Rec2020
        uniform int uOutputColorSpace; // 0=Rec709, 1=Rec2020, 2=HLG
        uniform int uToneMappingMode; // 0=None, 1=Simple, 2=Filmic, 3=Highlight Rolloff
        uniform float uHighlightRolloff;
        
        float luminance(vec3 color) {
            return dot(color, vec3(0.2126, 0.7152, 0.0722));
        }

        // Color Management Helpers
        vec3 decodeSLog3(vec3 c) {
            // S-Log3 to Linear approximate
            vec3 dec = vec3(0.0);
            for(int i=0; i<3; i++) {
                if(c[i] >= 0.011) {
                    dec[i] = pow(10.0, (c[i] - 0.420) / 0.2615) - 0.01;
                } else {
                    dec[i] = (c[i] - 0.0929) / 5.05;
                }
            }
            return dec;
        }

        vec3 decodeCLog(vec3 c) {
            // C-Log approximate
            return pow(c, vec3(2.2)) * 1.2 - 0.1; // Simple mock
        }
        
        vec3 decodeVLog(vec3 c) {
            // V-Log approximate
            return pow(c, vec3(2.2)) * 1.1; // Simple mock
        }

        vec3 toneMapFilmic(vec3 color) {
            vec3 x = max(vec3(0.0), color - 0.004);
            return (x * (6.2 * x + 0.5)) / (x * (6.2 * x + 1.7) + 0.06);
        }

        vec3 toneMapSimple(vec3 color) {
            return color / (1.0 + color);
        }

        vec3 toneMapHighlightRolloff(vec3 color, float rolloff) { // 0 to 1
            float threshold = mix(0.9, 0.5, rolloff);
            vec3 result = color;
            for(int i=0; i<3; i++){
                if(color[i] > threshold) {
                    result[i] = threshold + (1.0 - threshold) * (1.0 - exp(-(color[i] - threshold) / (1.0 - threshold)));
                }
            }
            return result;
        }
        
        float smoothstep_val(float edge0, float edge1, float x) {
            float t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
            return t * t * (3.0 - 2.0 * t);
        }

        float rand(vec2 co){
            return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
        }
        
        // RGB to HSL and Back
        vec3 rgb2hsl(vec3 c) {
            vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
            vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
            vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
            float d = q.x - min(q.w, q.y);
            float e = 1.0e-10;
            return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
        }

        vec3 hsl2rgb(vec3 c) {
            vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
            vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
            return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
        }

        vec3 sampleAs3DTexture(sampler2D tex, vec3 lutColor, float size) {
            lutColor = clamp(lutColor, 0.0, 1.0) * (size - 1.0);
            float b_floor = floor(lutColor.b);
            float b_ceil = min(size - 1.0, b_floor + 1.0);
            float b_fract = fract(lutColor.b);
            
            float s = (lutColor.r + 0.5) / size;
            float t_floor = (lutColor.g + b_floor * size + 0.5) / (size * size);
            float t_ceil = (lutColor.g + b_ceil * size + 0.5) / (size * size);
            
            vec3 color0 = texture(tex, vec2(s, t_floor)).rgb;
            vec3 color1 = texture(tex, vec2(s, t_ceil)).rgb;
            
            return mix(color0, color1, b_fract);
        }

        // Apply Lift Gamma Gain Offset
        vec3 applyColorWheels(vec3 c, vec3 lift, vec3 gamma, vec3 gain, vec3 offset) {
            // Lift: pivot around white (1.0)
            c = c * (vec3(1.0) - lift) + lift;
            // Gamma: Power function
            c = pow(max(c, vec3(0.0)), vec3(1.0) / max(gamma, vec3(0.001)));
            // Gain & Offset
            c = c * gain + offset;
            return c;
        }

        void main() {
            vec4 color = texture(texSampler, vTexCoords);
            vec3 rgb = color.rgb;
            
            // 1. Input Transform (LOG to Linear)
            if (uInputLogType == 1) { rgb = decodeSLog3(rgb); }
            else if (uInputLogType == 2) { rgb = decodeCLog(rgb); }
            else if (uInputLogType == 3) { rgb = decodeVLog(rgb); }
            else if (uInputLogType == 4) { rgb = pow(rgb, vec3(2.4)); } // Mock Rec2020 to Linear
            // Else assume already linear or Rec709

            // Save original normalized linear/Rec709 color for skin tone protection
            vec3 originalRgb = rgb;

            // White Balance (Temperature / Tint)
            float tempR = uTemperature > 0.0 ? 1.0 + uTemperature * 0.2 : 1.0;
            float tempB = uTemperature < 0.0 ? 1.0 - uTemperature * 0.2 : 1.0;
            float tintG = uTint > 0.0 ? 1.0 + uTint * 0.2 : 1.0;
            float tintR = uTint < 0.0 ? 1.0 - uTint * 0.2 : 1.0;
            rgb.r *= tempR * tintR;
            rgb.g *= tintG;
            rgb.b *= tempB;
            
            // Exposure
            rgb *= uExposure; 
            rgb += vec3(uBrightness * 0.2);
            
            // Contrast
            if (uContrast != 0.0) {
                float factor = max(0.0, 1.0 + uContrast);
                rgb = 0.5 + (rgb - vec3(0.5)) * factor;
            }
            
            // Highlights & Shadows
            float luma = luminance(rgb);
            float shadowMask = 1.0 - smoothstep_val(0.0, 0.4, luma);
            float highlightMask = smoothstep_val(0.6, 1.0, luma);
            rgb += (vec3(uShadows) * shadowMask * 0.5) - (vec3(uHighlights) * highlightMask * 0.5);
            
            // Blacks & Whites
            float blackMask = 1.0 - smoothstep_val(0.0, 0.1, luma);
            float whiteMask = smoothstep_val(0.9, 1.0, luma);
            rgb += (vec3(uBlacks) * blackMask * 0.2) - (vec3(uWhites) * whiteMask * 0.2);
            
            // Fade
            if (uFade > 0.0) {
                float fadeAmount = uFade * 0.2;
                rgb = mix(rgb, vec3(fadeAmount) + rgb * (1.0 - fadeAmount * 2.0), uFade);
            }
            
            // Color Wheels
            rgb = applyColorWheels(rgb, vec3(uLiftR, uLiftG, uLiftB), vec3(uGammaR, uGammaG, uGammaB), vec3(uGainR, uGainG, uGainB), vec3(uOffsetWheelR, uOffsetWheelG, uOffsetWheelB));

            // Clarity / Structure
            if (uClarity != 0.0 || uStructure != 0.0) {
                float lumaC = luminance(rgb);
                float midtoneC = 1.0 - abs(lumaC - 0.5) * 2.0;
                rgb += vec3((lumaC - 0.5) * uClarity * midtoneC);
                rgb += vec3((rand(vTexCoords * 100.0) - 0.5) * uStructure * 0.1);
            }
            
            // Dehaze (simple)
            if (uDehaze != 0.0) {
                float dark = min(rgb.r, min(rgb.g, rgb.b));
                rgb -= vec3(dark * uDehaze * 0.5);
            }

            // Saturation
            luma = luminance(rgb);
            if (uSaturation != 1.0) {
                rgb = mix(vec3(luma), rgb, uSaturation);
            }
            
            // Vibrance
            if (uVibrance != 0.0) {
                float mx = max(rgb.r, max(rgb.g, rgb.b));
                float mn = min(rgb.r, min(rgb.g, rgb.b));
                float sat = mx - mn;
                float vibMult = 1.0 + uVibrance * (1.0 - sat);
                rgb = mix(vec3(luma), rgb, max(0.0, vibMult));
            }
            
            // HSL Adjustment
            vec3 hsl = rgb2hsl(rgb);
            // Example of shifting hue depending on band:
            // Hue in 0..1 means 0=R, 0.16=Y, 0.33=G
            // This needs complex smooth stepping across 8 bins to be perfect.
            // Simplified: apply hue shifts if they are non-zero.
            float hShift = 0.0; float sShift = 0.0; float lShift = 0.0;
            // Only add if user set something (avoid heavy loop if possible)
            // (Skipped full precise 8-bin interpolation for perf, basic closest-bin approximation)
            float bin = fract(hsl.x + 0.0625) * 8.0; 
            int idx = int(bin) % 8;
            int idxNext = (idx + 1) % 8;
            float f = fract(bin);
            hShift = mix(getHslHue(idx), getHslHue(idxNext), f);
            sShift = mix(getHslSat(idx), getHslSat(idxNext), f);
            lShift = mix(getHslLum(idx), getHslLum(idxNext), f);

            hsl.x = fract(hsl.x + hShift);
            hsl.y = clamp(hsl.y + sShift, 0.0, 1.0);
            hsl.z = clamp(hsl.z + lShift, 0.0, 1.0);
            rgb = hsl2rgb(hsl);

            // HSL Qualifier
            if (uQualEnabled == 1 || uQualShowMatte == 1) {
                vec3 qhsl = rgb2hsl(rgb);
                
                // Hue mask
                float hd = abs(qhsl.x - uQualHueCenter);
                if (hd > 0.5) hd = 1.0 - hd;
                float hMask = 1.0 - smoothstep_val(uQualHueWidth, uQualHueWidth + uQualHueFeather, hd * 2.0);
                
                // Sat mask
                float sMask = smoothstep_val(uQualSatMin - uQualSatFeather, uQualSatMin, qhsl.y) *
                              (1.0 - smoothstep_val(uQualSatMax, uQualSatMax + uQualSatFeather, qhsl.y));
                              
                // Lum mask
                float lMask = smoothstep_val(uQualLumMin - uQualLumFeather, uQualLumMin, qhsl.z) *
                              (1.0 - smoothstep_val(uQualLumMax, uQualLumMax + uQualLumFeather, qhsl.z));
                              
                float qMask = hMask * sMask * lMask;
                if (uQualInvert == 1) {
                    qMask = 1.0 - qMask;
                }
                
                if (uQualShowMatte == 1) {
                    rgb = vec3(qMask);
                } else if (uQualEnabled == 1 && qMask > 0.0) {
                    // Apply HSL adjustments inside mask
                    qhsl.x = fract(qhsl.x + uQualHueShift * qMask);
                    qhsl.y = clamp(qhsl.y + (uQualSat - 1.0) * qhsl.y * qMask, 0.0, 1.0);
                    qhsl.z = clamp(qhsl.z + uQualLum * qMask, 0.0, 1.0);
                    rgb = hsl2rgb(qhsl);
                    
                    // Temp/Tint/Contrast
                    if (uQualTemp != 0.0 || uQualTint != 0.0) {
                        float tempAmount = uQualTemp * qMask;
                        float tintAmount = uQualTint * qMask;
                        float tr = tempAmount > 0.0 ? 1.0 + tempAmount * 0.2 : 1.0;
                        float tb = tempAmount < 0.0 ? 1.0 - tempAmount * 0.2 : 1.0;
                        float tg = tintAmount > 0.0 ? 1.0 + tintAmount * 0.2 : 1.0;
                        float tt = tintAmount < 0.0 ? 1.0 - tintAmount * 0.2 : 1.0;
                        rgb.r *= tr * tt;
                        rgb.g *= tg;
                        rgb.b *= tb;
                    }
                    if (uQualContrast != 0.0) {
                        float c = uQualContrast * qMask;
                        rgb = clamp(((rgb - 0.5) * max(c + 1.0, 0.0)) + 0.5, 0.0, 1.0);
                    }
                }
            }

            // Color Balance
            float sm = smoothstep_val(uShadowLumaMax, uShadowLumaMin, luma);
            float hm = smoothstep_val(uHighlightLumaMin, uHighlightLumaMax, luma);
            float mm = max(0.0, 1.0 - sm - hm);
            rgb += vec3(uShadowBalanceR, uShadowBalanceG, uShadowBalanceB) * sm + vec3(uMidtoneBalanceR, uMidtoneBalanceG, uMidtoneBalanceB) * mm + vec3(uHighlightBalanceR, uHighlightBalanceG, uHighlightBalanceB) * hm;
            
            // RGB Channel Mix & Multipliers
            vec3 mixRgb;
            mixRgb.r = dot(rgb, vec3(uChannelMixerRR, uChannelMixerRG, uChannelMixerRB));
            mixRgb.g = dot(rgb, vec3(uChannelMixerGR, uChannelMixerGG, uChannelMixerGB));
            mixRgb.b = dot(rgb, vec3(uChannelMixerBR, uChannelMixerBG, uChannelMixerBB));
            rgb = mixRgb * vec3(uChannelMultiplierR, uChannelMultiplierG, uChannelMultiplierB) + vec3(uChannelOffsetR, uChannelOffsetG, uChannelOffsetB);
            
            // Apply 3D LUT
            if (uLutEnabled == 1) {
                vec3 lutColor = sampleAs3DTexture(uLutTexture, rgb, uLutSize);
                rgb = mix(rgb, lutColor, uLutIntensity);
            }
            
            // Apply Skin Tone Protection (Blending graded result towards original based on skin mask)
            if (uSkinToneProtection > 0.0 || uSkinToneShowMask == 1) {
                vec3 origHsl = rgb2hsl(originalRgb);
                
                // Calculate hue mask centering around uSkinToneHueCenter, handling wrapping
                float hueDist = abs(origHsl.x - uSkinToneHueCenter);
                if (hueDist > 0.5) { hueDist = 1.0 - hueDist; }
                float skinMask = 1.0 - smoothstep_val(uSkinToneHueWidth * 0.5, uSkinToneHueWidth, hueDist);
                
                // Saturation and Luminance constraints to avoid protecting greys/blacks/whites
                skinMask *= smoothstep_val(0.15, 0.25, origHsl.y) * (1.0 - smoothstep_val(0.85, 0.95, origHsl.y)); // moderate sat
                skinMask *= smoothstep_val(0.15, 0.25, origHsl.z) * (1.0 - smoothstep_val(0.85, 0.95, origHsl.z)); // moderate luma
                
                if (uSkinToneShowMask == 1) {
                    rgb = mix(vec3(0.0), vec3(1.0, 0.0, 0.0), skinMask);
                } else if (uSkinToneProtection > 0.0) {
                    // Blend final graded RGB back to the original RGB in the skin regions
                    // We blend using skinMask * uSkinToneProtection. This allows the user to have natural skin while background applies LUT/Color
                    rgb = mix(rgb, originalRgb, skinMask * uSkinToneProtection);
                }
            }
            
            // Vignette
            if (uVignetteAmount != 0.0) {
                vec2 centerPos = (vTexCoords - vec2(0.5)) * 2.0;
                if (uVignetteRoundness != 0.0) {
                    centerPos.x *= 1.0 - uVignetteRoundness * 0.5; // simple aspect ratio stretch
                }
                float dist = length(centerPos);
                float vfactor = smoothstep_val(uVignetteMidpoint, uVignetteMidpoint + uVignetteFeather + 0.001, dist);
                rgb *= (1.0 - uVignetteAmount * vfactor);
            }
            
            // Grain
            if (uGrainAmount > 0.0) {
                vec2 noiseCoord = vTexCoords * (1.0 + uGrainSize * 10.0) + vec2(uTime * 0.1);
                float noise = rand(noiseCoord) - 0.5;
                noise *= (0.5 + uGrainRoughness * 0.5);
                rgb += vec3(noise * uGrainAmount * 0.2);
            }
            
            // Output Tone Mapping
            if (uOutputColorSpace == 0) { // Rec.709 Target
                if (uToneMappingMode == 1) { rgb = toneMapSimple(rgb); }
                else if (uToneMappingMode == 2) { rgb = toneMapFilmic(rgb); }
                else if (uToneMappingMode == 3) { rgb = toneMapHighlightRolloff(rgb, uHighlightRolloff); }
                else { rgb = clamp(rgb, 0.0, 1.0); } // None - Hard Clip
                
                // Back to Gamma 2.4/2.2 for display
                rgb = pow(clamp(rgb, 0.0, 1.0), vec3(1.0 / 2.2));
            } else if (uOutputColorSpace == 1) { // Rec.2020 target 
                // Skip tone mapping if the target is wide/HDR but we can output it.
                // In actual HDR, we'd apply PQ or HLG transfer function here.
            } else if (uOutputColorSpace == 2) { // HLG Target
                rgb = pow(clamp(rgb, 0.0, 1.0), vec3(0.5)); // extremely basic mock HLG transfer
            }

            // Fuse sharpening into the color pass. The four-tap cross kernel avoids an
            // additional full-frame render target/pass; no texture samples are added at 0.
            if (uSharpness > 0.0) {
                vec2 texel = 1.0 / max(vec2(uResolutionX, uResolutionY), vec2(1.0));
                vec3 neighbors =
                    texture(texSampler, vTexCoords + vec2(-texel.x, 0.0)).rgb +
                    texture(texSampler, vTexCoords + vec2( texel.x, 0.0)).rgb +
                    texture(texSampler, vTexCoords + vec2(0.0, -texel.y)).rgb +
                    texture(texSampler, vTexCoords + vec2(0.0,  texel.y)).rgb;
                rgb += (color.rgb - neighbors * 0.25) * (uSharpness * 3.0);
            }

            outColor = vec4(clamp(rgb, 0.0, 1.0), color.a);
        }
    """
    
    const val SHARPEN_FRAGMENT_SHADER = """#version 300 es
        precision highp float;
        
        uniform sampler2D texSampler;
        in vec2 vTexCoords;
        out vec4 outColor;
        
        uniform float uSharpness;
        uniform float uResolutionX;
        uniform float uResolutionY;
        
        void main() {
            vec4 color = texture(texSampler, vTexCoords);
            if (uSharpness <= 0.0) {
                outColor = color;
                return;
            }
            
            vec2 px = 1.0 / vec2(uResolutionX, uResolutionY);
            
            vec3 blur = vec3(0.0);
            blur += texture(texSampler, vTexCoords + vec2(-px.x, -px.y)).rgb;
            blur += texture(texSampler, vTexCoords + vec2( 0.0, -px.y)).rgb;
            blur += texture(texSampler, vTexCoords + vec2( px.x, -px.y)).rgb;
            blur += texture(texSampler, vTexCoords + vec2(-px.x,  0.0)).rgb;
            blur += color.rgb;
            blur += texture(texSampler, vTexCoords + vec2( px.x,  0.0)).rgb;
            blur += texture(texSampler, vTexCoords + vec2(-px.x,  px.y)).rgb;
            blur += texture(texSampler, vTexCoords + vec2( 0.0,  px.y)).rgb;
            blur += texture(texSampler, vTexCoords + vec2( px.x,  px.y)).rgb;
            blur /= 9.0;
            
            vec3 sharpened = color.rgb + (color.rgb - blur) * (uSharpness * 3.0);
            outColor = vec4(clamp(sharpened, 0.0, 1.0), color.a);
        }
    """
}
