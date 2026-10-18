package com.example.model.colorgrade.lut

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class LutEntry(
    val id: String,
    val name: String,
    val path: String, // Internal storage path
    val size: Int,
    val category: String = "Imported"
)

object LutRepository {
    private val luts = mutableMapOf<String, LutEntry>()
    private val lutDataCache = mutableMapOf<String, LutData>()
    private var isInitialized = false

    suspend fun getLuts(): List<LutEntry> = withContext(Dispatchers.IO) {
        luts.values.toList()
    }
    
    fun getCachedData(id: String): LutData? {
        return lutDataCache[id]
    }

    suspend fun importLut(context: Context, uri: Uri): Result<LutEntry> = withContext(Dispatchers.IO) {
        try {
            val parseResult = CubeLutParser.parseFromUri(context, uri)
            val lutData = parseResult.getOrThrow()

            val id = java.util.UUID.randomUUID().toString()
            val fileName = "lut_$id.cube"
            val file = File(context.filesDir, "luts")
            if (!file.exists()) file.mkdirs()
            
            val destFile = File(file, fileName)
            
            // Just copy the original file content to internal storage
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            val entry = LutEntry(
                id = id,
                name = lutData.title.ifEmpty { "Custom LUT" },
                path = destFile.absolutePath,
                size = lutData.size,
                category = "Imported"
            )
            
            luts[id] = entry
            lutDataCache[id] = lutData
            Result.success(entry)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun loadLutData(id: String): LutData? = withContext(Dispatchers.IO) {
        lutDataCache[id]?.let { return@withContext it }
        val entry = luts[id] ?: return@withContext null
        val file = File(entry.path)
        if (!file.exists()) return@withContext null
        val reader = file.bufferedReader()
        val data = CubeLutParser.parse(reader).getOrNull()
        if (data != null) {
            lutDataCache[id] = data
        }
        data
    }
}
