package com.example.model.colorgrade.lut

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object CubeLutParser {

    suspend fun parseFromUri(context: Context, uri: Uri): Result<LutData> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Could not open file"))
            
            val reader = BufferedReader(InputStreamReader(inputStream))
            parse(reader)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parse(reader: BufferedReader): Result<LutData> {
        try {
            var title = "Unknown LUT"
            var size = -1
            var domainMin = floatArrayOf(0f, 0f, 0f)
            var domainMax = floatArrayOf(1f, 1f, 1f)
            
            val dataLines = mutableListOf<FloatArray>()
            
            reader.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                    
                    if (trimmed.startsWith("TITLE")) {
                        val parts = trimmed.split("\"", limit = 3)
                        if (parts.size >= 2) title = parts[1]
                    } else if (trimmed.startsWith("LUT_3D_SIZE")) {
                        val parts = trimmed.split("\\s+".toRegex())
                        if (parts.size >= 2) size = parts[1].toInt()
                    } else if (trimmed.startsWith("DOMAIN_MIN")) {
                        val parts = trimmed.split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            domainMin = floatArrayOf(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat())
                        }
                    } else if (trimmed.startsWith("DOMAIN_MAX")) {
                        val parts = trimmed.split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            domainMax = floatArrayOf(parts[1].toFloat(), parts[2].toFloat(), parts[3].toFloat())
                        }
                    } else if (trimmed.startsWith("LUT_1D_SIZE")) {
                        // We only support 3D LUTs for now natively, or fail out.
                        return Result.failure(Exception("1D LUTs not supported in this version"))
                    } else {
                        // Data line
                        val parts = trimmed.split("\\s+".toRegex())
                        if (parts.size == 3) {
                            val r = parts[0].toFloatOrNull()
                            val g = parts[1].toFloatOrNull()
                            val b = parts[2].toFloatOrNull()
                            if (r != null && g != null && b != null) {
                                dataLines.add(floatArrayOf(r, g, b))
                            }
                        }
                    }
                }
            }

            if (size <= 0) {
                return Result.failure(Exception("Invalid LUT size: $size"))
            }

            val expectedSize = size * size * size
            if (dataLines.size != expectedSize) {
                return Result.failure(Exception("LUT data size mismatch. Expected $expectedSize, got ${dataLines.size}"))
            }

            val cubeData = FloatArray(expectedSize * 3)
            var index = 0
            for (line in dataLines) {
                cubeData[index++] = line[0]
                cubeData[index++] = line[1]
                cubeData[index++] = line[2]
            }

            return Result.success(LutData(title, size, domainMin, domainMax, cubeData))
            
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}
