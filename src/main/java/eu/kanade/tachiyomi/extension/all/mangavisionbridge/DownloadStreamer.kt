package eu.kanade.tachiyomi.extension.all.mangavisionbridge

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

data class ChapterPageAsset(
    val index: Int,
    val name: String,
    val mimeType: String,
    val size: Long,
    val openStream: () -> InputStream?
)

/**
 * Reads downloaded manga chapter images or CBZ files from local storage.
 */
class DownloadStreamer(private val context: Context) {

    private fun getPotentialDownloadDirs(): List<File> {
        val dirs = mutableListOf<File>()

        // Standard Mihon downloads in external files
        context.getExternalFilesDirs(null).forEach { ext ->
            if (ext != null) {
                dirs.add(File(ext, "downloads"))
            }
        }

        // Public storage location (Documents/Mihon/downloads or Tachiyomi/downloads)
        val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (publicDocs != null && publicDocs.exists()) {
            dirs.add(File(publicDocs, "Mihon/downloads"))
            dirs.add(File(publicDocs, "Tachiyomi/downloads"))
        }

        val publicRoot = Environment.getExternalStorageDirectory()
        if (publicRoot != null && publicRoot.exists()) {
            dirs.add(File(publicRoot, "Tachiyomi/downloads"))
            dirs.add(File(publicRoot, "Mihon/downloads"))
        }

        return dirs.filter { it.exists() && it.isDirectory }
    }

    fun findChapterPages(mangaTitle: String, chapterName: String): List<ChapterPageAsset> {
        val sanitizedManga = sanitizeFileName(mangaTitle)
        val sanitizedChapter = sanitizeFileName(chapterName)

        for (baseDir in getPotentialDownloadDirs()) {
            // Check direct folder tree: downloads/<source>/<manga>/<chapter>
            baseDir.listFiles()?.forEach { sourceDir ->
                if (sourceDir.isDirectory) {
                    val mangaDir = sourceDir.listFiles()?.find {
                        it.name.equals(sanitizedManga, ignoreCase = true) || it.name.contains(sanitizedManga, ignoreCase = true)
                    }

                    if (mangaDir != null && mangaDir.isDirectory) {
                        // Look for directory or CBZ
                        val chapterDir = mangaDir.listFiles()?.find {
                            it.name.equals(sanitizedChapter, ignoreCase = true) || it.name.contains(sanitizedChapter, ignoreCase = true)
                        }

                        if (chapterDir != null) {
                            if (chapterDir.isDirectory) {
                                return readFromDirectory(chapterDir)
                            } else if (chapterDir.name.endsWith(".cbz", ignoreCase = true)) {
                                return readFromCbz(chapterDir)
                            }
                        }
                    }
                }
            }
        }

        return emptyList()
    }

    private fun readFromDirectory(dir: File): List<ChapterPageAsset> {
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
        val files = dir.listFiles()?.filter {
            val ext = it.extension.lowercase()
            it.isFile && imageExtensions.contains(ext)
        }?.sortedBy { it.name.lowercase() } ?: return emptyList()

        return files.mapIndexed { idx, file ->
            ChapterPageAsset(
                index = idx,
                name = file.name,
                mimeType = getMimeType(file.extension),
                size = file.length(),
                openStream = { file.inputStream() }
            )
        }
    }

    private fun readFromCbz(cbzFile: File): List<ChapterPageAsset> {
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
        val zip = try {
            ZipFile(cbzFile)
        } catch (e: Exception) {
            return emptyList()
        }

        val entries = zip.entries().asSequence()
            .filter { !it.isDirectory && imageExtensions.contains(it.name.substringAfterLast('.', "").lowercase()) }
            .sortedBy { it.name.lowercase() }
            .toList()

        return entries.mapIndexed { idx, entry ->
            ChapterPageAsset(
                index = idx,
                name = entry.name.substringAfterLast('/'),
                mimeType = getMimeType(entry.name.substringAfterLast('.', "")),
                size = entry.size,
                openStream = {
                    val z = ZipFile(cbzFile)
                    z.getInputStream(entry)
                }
            )
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "avif" -> "image/avif"
            else -> "application/octet-stream"
        }
    }
}
