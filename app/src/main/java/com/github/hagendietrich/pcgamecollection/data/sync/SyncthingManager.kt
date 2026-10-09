package com.github.hagendietrich.pcgamecollection.data.sync

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.github.hagendietrich.pcgamecollection.data.model.SyncSnapshot
import kotlinx.serialization.json.Json
import java.io.File

class SyncthingManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        prettyPrint = true
    }

    companion object {
        private const val SYNC_FILE_PREFIX = "pcgc_sync_"
        private const val SYNC_FILE_SUFFIX = ".json"
    }

    fun getLocalSyncFileName(deviceId: String): String {
        return "$SYNC_FILE_PREFIX$deviceId$SYNC_FILE_SUFFIX"
    }

    /**
     * Writes local snapshot JSON to the configured sync directory.
     */
    fun writeLocalSnapshot(folderUriOrPath: String, snapshot: SyncSnapshot): Boolean {
        val fileName = getLocalSyncFileName(snapshot.deviceId)
        val jsonContent = json.encodeToString(SyncSnapshot.serializer(), snapshot)

        Log.d("SyncthingManager", "Writing local snapshot $fileName to $folderUriOrPath")

        // Try direct file path first (Waydroid Direct Path Fallback)
        val directFile = resolveDirectFile(folderUriOrPath, fileName)
        if (directFile != null) {
            try {
                directFile.parentFile?.mkdirs()
                directFile.writeText(jsonContent)
                Log.d("SyncthingManager", "Direct write SUCCESS to ${directFile.absolutePath}")
                return true
            } catch (e: Exception) {
                Log.w("SyncthingManager", "Direct write failed: ${e.message}, falling back to SAF")
            }
        }

        // Fallback to SAF Uri
        return try {
            val uri = Uri.parse(folderUriOrPath)
            val treeDir = DocumentFile.fromTreeUri(context, uri) ?: return false
            var targetFile = treeDir.findFile(fileName)
            if (targetFile == null || !targetFile.exists()) {
                targetFile = treeDir.createFile("application/json", fileName) ?: return false
            }

            context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { outputStream ->
                outputStream.write(jsonContent.toByteArray())
            }
            Log.d("SyncthingManager", "SAF write SUCCESS to ${targetFile.uri}")
            true
        } catch (e: Exception) {
            Log.e("SyncthingManager", "SAF write failed", e)
            false
        }
    }

    /**
     * Reads all remote sync snapshot files from the sync folder, excluding local device's file.
     */
    fun readRemoteSnapshots(folderUriOrPath: String, localDeviceId: String): List<SyncSnapshot> {
        val remoteSnapshots = mutableListOf<SyncSnapshot>()
        val localFileName = getLocalSyncFileName(localDeviceId)

        Log.d("SyncthingManager", "Reading remote snapshots from $folderUriOrPath (ignoring $localFileName)")

        // 1. Try Direct Directory Access
        val directDir = resolveDirectDir(folderUriOrPath)
        if (directDir != null && directDir.isDirectory) {
            val files = directDir.listFiles { _, name ->
                name.startsWith(SYNC_FILE_PREFIX) && name.endsWith(SYNC_FILE_SUFFIX) && name != localFileName
            }
            if (files != null) {
                for (file in files) {
                    try {
                        val content = file.readText()
                        val snapshot = json.decodeFromString(SyncSnapshot.serializer(), content)
                        remoteSnapshots.add(snapshot)
                        Log.d("SyncthingManager", "Successfully read remote snapshot from direct file: ${file.name}")
                    } catch (e: Exception) {
                        Log.e("SyncthingManager", "Failed to parse remote snapshot file ${file.name}", e)
                    }
                }
                if (remoteSnapshots.isNotEmpty() || files.isNotEmpty()) {
                    return remoteSnapshots
                }
            }
        }

        // 2. Try SAF Uri Directory Access
        try {
            val uri = Uri.parse(folderUriOrPath)
            val treeDir = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
            for (file in treeDir.listFiles()) {
                val name = file.name ?: continue
                if (name.startsWith(SYNC_FILE_PREFIX) && name.endsWith(SYNC_FILE_SUFFIX) && name != localFileName) {
                    try {
                        context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { reader ->
                            val content = reader.readText()
                            val snapshot = json.decodeFromString(SyncSnapshot.serializer(), content)
                            remoteSnapshots.add(snapshot)
                            Log.d("SyncthingManager", "Successfully read remote snapshot from SAF file: $name")
                        }
                    } catch (e: Exception) {
                        Log.e("SyncthingManager", "Failed to parse remote SAF file $name", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SyncthingManager", "SAF Directory read failed", e)
        }

        return remoteSnapshots
    }

    private fun resolveDirectFile(folderUriOrPath: String, fileName: String): File? {
        val dir = resolveDirectDir(folderUriOrPath) ?: return null
        return File(dir, fileName)
    }

    private fun resolveDirectDir(folderUriOrPath: String): File? {
        if (!folderUriOrPath.startsWith("content:")) {
            val f = File(folderUriOrPath)
            if (f.exists()) return f
        }

        val uri = Uri.parse(folderUriOrPath)
        val docId = uri.lastPathSegment ?: ""
        val relativePath = when {
            docId.startsWith("primary:") -> docId.substringAfter("primary:")
            docId.startsWith("raw:") -> docId.substringAfter("raw:")
            else -> null
        }

        if (relativePath != null) {
            val candidates = listOf(
                File("/storage/emulated/0/$relativePath"),
                File(Environment.getExternalStorageDirectory(), relativePath),
                File("/mnt/media_rw/0/$relativePath"),
            )
            for (candidate in candidates) {
                if (candidate.exists() && candidate.isDirectory) {
                    return candidate
                }
            }
        }
        return null
    }
}
