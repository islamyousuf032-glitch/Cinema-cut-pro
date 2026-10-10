package com.example.model.colorgrade.lut

import android.net.Uri
import android.content.Context
import java.io.InputStreamReader
import java.io.BufferedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LutData(
    val title: String,
    val size: Int,
    val domainMin: FloatArray,
    val domainMax: FloatArray,
    val cubeData: FloatArray // 3D Array flattened: r, g, b per entry. r changes fastest, then g, then b. size needed: size*size*size*3
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LutData

        if (title != other.title) return false
        if (size != other.size) return false
        if (!domainMin.contentEquals(other.domainMin)) return false
        if (!domainMax.contentEquals(other.domainMax)) return false
        if (!cubeData.contentEquals(other.cubeData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + size
        result = 31 * result + domainMin.contentHashCode()
        result = 31 * result + domainMax.contentHashCode()
        result = 31 * result + cubeData.contentHashCode()
        return result
    }
}
