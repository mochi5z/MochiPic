package org.fossify.gallery.mochipic

import android.content.Context
import org.fossify.gallery.models.Directory
import java.util.Locale

/**
 * MochiPic v0.1.3: コピー・移動したアルバムの「最後に触った時刻」を覚えておく。
 *
 * 同じ端末内の移動ではファイルの更新日時が変わらないので、アルバムの「最新の更新日」（中の一番新しいファイルの日時）は
 * 上がらず、更新順で並べても移動先が一番上に来なかった。そこで移動・コピーした時刻を別に覚え、
 * 並べ替えのときは「ファイルの日時」と「最後に触った時刻」の新しい方を使う。
 */
object MoveTouch {
    private const val PREFS = "mochipic_move_touch"
    private const val KEY = "touched"
    private const val MAX = 300

    private val times = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @Volatile
    private var loaded = false

    private fun key(path: String) = path.trimEnd('/').lowercase(Locale.ROOT)

    private fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getStringSet(KEY, emptySet())?.forEach {
                        val tab = it.lastIndexOf('\t')
                        if (tab > 0) it.substring(tab + 1).toLongOrNull()?.let { t -> times.putIfAbsent(it.substring(0, tab), t) }
                    }
            } catch (ignored: Exception) {
            }
            loaded = true
        }
    }

    /** コピー・移動が終わったアルバム（移動先）を「今触った」として覚える。 */
    fun touch(context: Context, path: String, now: Long = System.currentTimeMillis()) {
        load(context)
        times[key(path)] = now
        if (times.size > MAX) {
            times.entries.sortedByDescending { it.value }.drop(MAX).forEach { times.remove(it.key) }
        }
        val set = HashSet<String>(times.size)
        times.forEach { (k, v) -> set.add("$k\t$v") }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, set).apply()
    }

    /** 並べ替えに使う更新日時: ファイルの日時と最後に触った時刻の新しい方。 */
    fun effectiveModified(context: Context, dir: Directory): Long {
        load(context)
        return maxOf(dir.modified, times[key(dir.path)] ?: 0L)
    }
}
