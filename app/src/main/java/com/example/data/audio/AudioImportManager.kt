package com.example.data.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AudioImportManager(private val context: Context) {

    /**
     * Copies an audio file from a Uri (provided by System SAF) to the app's cache directory.
     * Returns the absolute file path of the copied file, or null if copy failed.
     */
    suspend fun importAudio(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val fileName = getFileName(uri) ?: "imported_${UUID.randomUUID()}.mp3"
            val targetFile = File(context.cacheDir, "shadowing_audio_$fileName")
            
            // Delete if exists
            if (targetFile.exists()) {
                targetFile.delete()
            }

            contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(targetFile).use { outputStream ->
                    val buffer = ByteArray(4 * 1024) // 4KB buffer
                    var read: Int
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                }
            }
            
            Log.d("AudioImportManager", "Successfully copied audio from $uri to ${targetFile.absolutePath}")
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e("AudioImportManager", "Error copying audio file", e)
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    /**
     * Scans audio files in context.cacheDir and deletes the oldest unused MP3/WAV recordings
     * if the total cache size exceeds [maxSizeBytes].
     */
    suspend fun cleanOrphanedAudioCache(maxSizeBytes: Long = 200 * 1024 * 1024) = withContext(Dispatchers.IO) {
        try {
            val cacheDir = context.cacheDir ?: return@withContext
            val files = cacheDir.listFiles() ?: return@withContext
            
            val audioFiles = files.filter { file ->
                file.isFile && (file.name.startsWith("shadowing_audio_") || 
                                file.name.endsWith(".mp3", ignoreCase = true) || 
                                file.name.endsWith(".wav", ignoreCase = true))
            }.sortedBy { it.lastModified() } // oldest first
            
            var totalSize = audioFiles.sumOf { it.length() }
            
            if (totalSize > maxSizeBytes) {
                Log.d("AudioImportManager", "Cache size ($totalSize bytes) exceeds limit ($maxSizeBytes bytes). Cleaning up oldest files...")
                for (file in audioFiles) {
                    val fileSize = file.length()
                    if (file.delete()) {
                        totalSize -= fileSize
                        Log.d("AudioImportManager", "Deleted cached audio: ${file.name}, current total size: $totalSize bytes")
                    }
                    if (totalSize <= maxSizeBytes) {
                        break
                    }
                }
            } else {
                Log.d("AudioImportManager", "Cache size ($totalSize bytes) is within limits.")
            }
        } catch (e: Exception) {
            Log.e("AudioImportManager", "Error cleaning audio cache", e)
        }
    }
}
