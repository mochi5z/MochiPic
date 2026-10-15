package org.fossify.gallery.mochipic

import android.content.Context
import android.provider.MediaStore
import android.text.format.DateFormat
import org.fossify.gallery.extensions.config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * MochiPic: 「日付」表示の元データ。端末の写真・動画を撮影日（なければ更新日）の月ごとにまとめる。
 * QP-Gallery と同じく月単位だけ。隠しフォルダ・除外フォルダの中身は入れない。
 */
object DateIndex {

    /** @param key "2026-10" の形。items は新しい順のパス。 */
    class Month(val key: String, val title: String, val items: List<String>, val newestTime: Long)

    private class Item(val path: String, val time: Long)

    @Volatile
    private var cache: List<Month> = emptyList()

    fun cached(key: String) = cache.firstOrNull { it.key == key }

    /** 重い（端末の全メディアを読む）ので裏のスレッドで呼ぶ。 */
    fun load(context: Context): List<Month> {
        val items = query(context)
        val format = SimpleDateFormat(DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMM"), Locale.getDefault())
        val cal = Calendar.getInstance()
        val months = items.groupBy {
            cal.timeInMillis = it.time
            "%04d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        }.map { (key, list) ->
            val sorted = list.sortedByDescending { it.time }
            Month(key, format.format(sorted.first().time), sorted.map { it.path }, sorted.first().time)
        }.sortedByDescending { it.key }
        cache = months
        return months
    }

    private fun query(context: Context): List<Item> {
        val config = context.config
        val showHidden = config.shouldShowHidden
        val excluded = config.excludedFolders.map { it.trimEnd('/') + "/" }
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            "datetaken",
            MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val result = ArrayList<Item>()
        try {
            context.contentResolver.query(uri, projection, selection, args, null)?.use { c ->
                val iPath = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val iTaken = c.getColumnIndex("datetaken")
                val iModified = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                while (c.moveToNext()) {
                    val path = c.getString(iPath) ?: continue
                    if (!showHidden && path.contains("/.")) continue
                    if (excluded.any { path.startsWith(it) }) continue
                    val taken = if (iTaken >= 0) c.getLong(iTaken) else 0L
                    val time = if (taken > 0) taken else c.getLong(iModified) * 1000L
                    result.add(Item(path, time))
                }
            }
        } catch (e: Exception) {
            // 権限がないときなどは空のまま
        }
        return result
    }
}
