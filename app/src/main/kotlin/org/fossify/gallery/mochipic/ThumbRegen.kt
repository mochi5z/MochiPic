package org.fossify.gallery.mochipic

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * MochiPic: フォルダごとの「サムネイルを作り直した回数」。アルバムの︙「サムネイルを再生成」で 1 つ増やす。
 * 回数をサムネイルの目印（Glide の signature）に混ぜるので、そのフォルダだけ前のサムネイルを使わずに作り直される。
 * 作り直すときは Android のサムネイルを使わず、元の画像から作る（Android 側のサムネイルが壊れている場合に備える）。
 */
object ThumbRegen {
    private const val FILE = "mochipic_thumb_regen"
    private const val KEY = "folders"

    private val counts = ConcurrentHashMap<String, Int>()
    @Volatile
    private var loaded = false

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getStringSet(KEY, emptySet())!!
                .forEach { entry ->
                    val n = entry.substringBefore('\t').toIntOrNull() ?: return@forEach
                    counts[entry.substringAfter('\t')] = n
                }
            loaded = true
        }
    }

    /** そのフォルダを作り直した回数（一度もなければ 0）。 */
    fun generation(context: Context, folder: String): Int {
        ensureLoaded(context)
        if (counts.isEmpty()) return 0
        return counts[AlbumMerge.key(folder)] ?: 0
    }

    fun bump(context: Context, folder: String) {
        ensureLoaded(context)
        val key = AlbumMerge.key(folder)
        counts[key] = (counts[key] ?: 0) + 1
        val set = counts.entries.map { "${it.value}\t${it.key}" }.toSet()
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet(KEY, set).apply()
    }
}
