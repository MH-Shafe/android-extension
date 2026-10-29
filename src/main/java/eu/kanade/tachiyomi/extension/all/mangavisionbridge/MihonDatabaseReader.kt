package eu.kanade.tachiyomi.extension.all.mangavisionbridge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class MihonMangaItem(
    val id: Long,
    val source: Long,
    val url: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
    val status: Int = 0,
    val thumbnailUrl: String? = null,
    val favorite: Boolean = false,
    val unreadCount: Int = 0,
    val totalChapters: Int = 0,
    val category: String? = null
)

@Serializable
data class MihonChapterItem(
    val id: Long,
    val mangaId: Long,
    val url: String,
    val name: String,
    val chapterNumber: Float = 0f,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val dateFetch: Long = 0,
    val dateUpload: Long = 0,
    val isDownloaded: Boolean = false
)

/**
 * Accesses Mihon's SQLite database directly from within Mihon's application process.
 */
class MihonDatabaseReader(private val context: Context) {

    private fun getDatabaseFile(): File {
        val dbFile = context.getDatabasePath("tachiyomi.db")
        if (dbFile.exists()) return dbFile

        // Fallback checks for alternate forks or internal paths
        val legacy = File(context.filesDir.parentFile, "databases/tachiyomi.db")
        if (legacy.exists()) return legacy

        val mihonDb = context.getDatabasePath("mihon.db")
        if (mihonDb.exists()) return mihonDb

        return dbFile
    }

    private fun openDatabase(writable: Boolean = false): SQLiteDatabase? {
        val file = getDatabaseFile()
        if (!file.exists()) return null
        val flags = if (writable) SQLiteDatabase.OPEN_READWRITE else SQLiteDatabase.OPEN_READONLY
        return SQLiteDatabase.openDatabase(file.absolutePath, null, flags)
    }

    fun getLibraryManga(): List<MihonMangaItem> {
        val db = openDatabase() ?: return emptyList()
        val result = mutableListOf<MihonMangaItem>()

        try {
            val query = """
                SELECT _id, source, url, title, author, artist, description, genre, status, thumbnail_url, favorite
                FROM mangas
                WHERE favorite = 1
                ORDER BY title COLLATE NOCASE ASC
            """.trimIndent()

            val cursor = db.rawQuery(query, null)
            cursor.use { c ->
                val idIdx = c.getColumnIndexOrThrow("_id")
                val srcIdx = c.getColumnIndexOrThrow("source")
                val urlIdx = c.getColumnIndexOrThrow("url")
                val titleIdx = c.getColumnIndexOrThrow("title")
                val authorIdx = c.getColumnIndex("author")
                val artistIdx = c.getColumnIndex("artist")
                val descIdx = c.getColumnIndex("description")
                val genreIdx = c.getColumnIndex("genre")
                val statusIdx = c.getColumnIndex("status")
                val thumbIdx = c.getColumnIndex("thumbnail_url")
                val favIdx = c.getColumnIndex("favorite")

                while (c.moveToNext()) {
                    val mangaId = c.getLong(idIdx)
                    val rawGenre = if (genreIdx >= 0) c.getString(genreIdx) else null
                    val genres = rawGenre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

                    result.add(
                        MihonMangaItem(
                            id = mangaId,
                            source = c.getLong(srcIdx),
                            url = c.getString(urlIdx),
                            title = c.getString(titleIdx),
                            author = if (authorIdx >= 0) c.getString(authorIdx) else null,
                            artist = if (artistIdx >= 0) c.getString(artistIdx) else null,
                            description = if (descIdx >= 0) c.getString(descIdx) else null,
                            genre = genres,
                            status = if (statusIdx >= 0) c.getInt(statusIdx) else 0,
                            thumbnailUrl = if (thumbIdx >= 0) c.getString(thumbIdx) else null,
                            favorite = if (favIdx >= 0) c.getInt(favIdx) == 1 else true,
                            unreadCount = getUnreadCount(db, mangaId),
                            totalChapters = getTotalChapterCount(db, mangaId)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.close()
        }

        return result
    }

    fun getChapters(mangaId: Long): List<MihonChapterItem> {
        val db = openDatabase() ?: return emptyList()
        val result = mutableListOf<MihonChapterItem>()

        try {
            val query = """
                SELECT _id, manga_id, url, name, chapter_number, read, bookmark, last_page_read, date_fetch, date_upload
                FROM chapters
                WHERE manga_id = ?
                ORDER BY chapter_number DESC, _id DESC
            """.trimIndent()

            val cursor = db.rawQuery(query, arrayOf(mangaId.toString()))
            cursor.use { c ->
                val idIdx = c.getColumnIndexOrThrow("_id")
                val mangaIdIdx = c.getColumnIndexOrThrow("manga_id")
                val urlIdx = c.getColumnIndexOrThrow("url")
                val nameIdx = c.getColumnIndexOrThrow("name")
                val numIdx = c.getColumnIndex("chapter_number")
                val readIdx = c.getColumnIndex("read")
                val bookmarkIdx = c.getColumnIndex("bookmark")
                val lastPageIdx = c.getColumnIndex("last_page_read")
                val fetchIdx = c.getColumnIndex("date_fetch")
                val uploadIdx = c.getColumnIndex("date_upload")

                while (c.moveToNext()) {
                    val readVal = if (readIdx >= 0) c.getInt(readIdx) == 1 else false
                    result.add(
                        MihonChapterItem(
                            id = c.getLong(idIdx),
                            mangaId = c.getLong(mangaIdIdx),
                            url = c.getString(urlIdx),
                            name = c.getString(nameIdx),
                            chapterNumber = if (numIdx >= 0) c.getFloat(numIdx) else 0f,
                            read = readVal,
                            bookmark = if (bookmarkIdx >= 0) c.getInt(bookmarkIdx) == 1 else false,
                            lastPageRead = if (lastPageIdx >= 0) c.getInt(lastPageIdx) else 0,
                            dateFetch = if (fetchIdx >= 0) c.getLong(fetchIdx) else 0L,
                            dateUpload = if (uploadIdx >= 0) c.getLong(uploadIdx) else 0L
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.close()
        }

        return result
    }

    fun updateProgress(chapterId: Long, lastPageRead: Int, isRead: Boolean): Boolean {
        val db = openDatabase(writable = true) ?: return false
        return try {
            val values = ContentValues().apply {
                put("last_page_read", lastPageRead)
                put("read", if (isRead) 1 else 0)
            }
            val rows = db.update("chapters", values, "_id = ?", arrayOf(chapterId.toString()))
            rows > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            db.close()
        }
    }

    private fun getUnreadCount(db: SQLiteDatabase, mangaId: Long): Int {
        return try {
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM chapters WHERE manga_id = ? AND read = 0",
                arrayOf(mangaId.toString())
            )
            cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        } catch (e: Exception) {
            0
        }
    }

    private fun getTotalChapterCount(db: SQLiteDatabase, mangaId: Long): Int {
        return try {
            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM chapters WHERE manga_id = ?",
                arrayOf(mangaId.toString())
            )
            cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        } catch (e: Exception) {
            0
        }
    }
}
