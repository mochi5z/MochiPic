package org.fossify.gallery.mochipic

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * MochiPic: Android の画像の一覧での「フォルダの番号（BUCKET_ID）」と、フォルダの場所の対応表。
 * 一覧を作るときに覚え、差分読み込みとアルバムを開くときに、番号でフォルダの中身を問い合わせるのに使う。
 */
object BucketMap {
    private const val FILE = "mochipic_buckets"
    private const val KEY = "buckets"

    private val byId = ConcurrentHashMap<Long, String>()
    private val byPath = ConcurrentHashMap<String, Long>()
    @Volatile
    private var loaded = false

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getStringSet(KEY, emptySet())!!
                .forEach { entry ->
                    val id = entry.substringBefore('\t').toLongOrNull() ?: return@forEach
                    put(id, entry.substringAfter('\t'))
                }
            loaded = true
        }
    }

    private fun put(id: Long, path: String) {
        byId[id] = path
        byPath[AlbumMerge.key(path)] = id
    }

    fun pathOf(context: Context, id: Long): String? {
        ensureLoaded(context)
        return byId[id]
    }

    fun idOf(context: Context, path: String): Long? {
        ensureLoaded(context)
        return byPath[AlbumMerge.key(path)]
    }

    /**
     * そのフォルダの画像が Android の一覧に載っていそうか。表が空（「全体」方式など）なら分からないので true。
     * 載っていない（隠しフォルダなど）なら、Android のサムネイルを探さずに元の画像から作る。
     */
    fun mayBeIndexed(context: Context, folder: String): Boolean {
        ensureLoaded(context)
        return byId.isEmpty() || byPath.containsKey(AlbumMerge.key(folder))
    }

    /** 全件読み込みのとき: 表を作り直す。 */
    fun replaceAll(context: Context, entries: Map<Long, String>) {
        synchronized(this) {
            byId.clear()
            byPath.clear()
            entries.forEach { (id, path) -> put(id, path) }
            loaded = true
        }
        save(context)
    }

    /** 差分読み込みのとき: 見つかったものを足す。 */
    fun addAll(context: Context, entries: Map<Long, String>) {
        ensureLoaded(context)
        if (entries.all { (id, path) -> byId[id] == path }) return
        entries.forEach { (id, path) -> put(id, path) }
        save(context)
    }

    private fun save(context: Context) {
        val set = byId.entries.map { "${it.key}\t${it.value}" }.toSet()
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet(KEY, set).apply()
    }
}
