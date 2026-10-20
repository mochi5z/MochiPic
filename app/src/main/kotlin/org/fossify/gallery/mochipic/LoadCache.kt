package org.fossify.gallery.mochipic

import android.content.Context
import android.provider.MediaStore
import org.fossify.commons.helpers.isRPlus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * MochiPic: 読み込みのときに調べた結果を保存しておき、変化がなければ使い回す（端末の発熱・電池を抑えるため。v0.1.2）。
 *  - .nomedia の場所（Android の一覧の全件問い合わせ）… Android の一覧の状態が変わったときだけ探し直す
 *  - フォルダを表示してよいかの判定 … 設定・.nomedia の場所・日付が同じなら使い回す（新しいフォルダだけ判定する）
 *  - フォルダの「実際の場所」（canonicalPath）… 一度調べたら覚えておく
 *  - 消えたフォルダの確認 … Android の一覧の状態が変わったときだけ行う
 */
object LoadCache {
    private const val PREFS = "mochipic_load_cache"
    private const val MAX_VISIBILITY_KEYS = 4
    private const val MAX_DISTINCT = 5000

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Android の一覧の状態（画像以外も含む全ファイル）。どのファイルが変わっても変わる。Android 11 未満は null。 */
    fun fullStamp(context: Context): String? {
        if (!isRPlus()) return null
        return try {
            MediaStore.getExternalVolumeNames(context).sorted().joinToString(",") {
                "$it:${MediaStore.getVersion(context, it)}:${MediaStore.getGeneration(context, it)}"
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---- .nomedia の場所 ----

    fun loadNoMedia(context: Context, stamp: String?): ArrayList<String>? {
        if (stamp == null) return null
        val p = prefs(context)
        if (p.getString("nomedia_stamp", null) != stamp) return null
        val text = p.getString("nomedia_list", null) ?: return null
        return ArrayList(text.split('\n').filter { it.isNotEmpty() })
    }

    fun saveNoMedia(context: Context, stamp: String?, folders: List<String>) {
        if (stamp == null) return
        prefs(context).edit().putString("nomedia_stamp", stamp).putString("nomedia_list", folders.joinToString("\n")).apply()
    }

    fun clearNoMedia(context: Context) {
        prefs(context).edit().remove("nomedia_stamp").remove("nomedia_list").apply()
    }

    // ---- フォルダを表示してよいかの判定 ----

    private class Visibility(val key: String, val map: ConcurrentHashMap<String, Boolean>) {
        @Volatile
        var dirty = false
    }

    private val visibilities = LinkedHashMap<String, Visibility>()

    /**
     * 判定の前提をまとめたキー。前提が 1 つでも変われば判定し直す。
     * 1 日 1 回は判定し直す（Android の一覧に載らない .nomedia を後から置かれた場合のため）。
     */
    fun visibilityKey(
        context: Context,
        showHidden: Boolean,
        excluded: Collection<String>,
        included: Collection<String>,
        noMediaFolders: Collection<String>,
    ): String {
        val prefs = context.mochiPrefs
        val day = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        return listOf(
            showHidden, excluded.sorted(), included.sorted(), prefs.scanFolders.sorted(), prefs.skipAppFolders,
            if (showHidden) "" else noMediaFolders.sorted().hashCode(), day,
        ).joinToString("|").hashCode().toString()
    }

    /** そのキーの判定結果（場所 → 表示してよいか）。ここに無いフォルダだけ判定して [remember] で足す。 */
    fun visibility(context: Context, key: String): Map<String, Boolean> = synchronized(visibilities) {
        visibilities[key]?.let { return it.map }
        val map = ConcurrentHashMap<String, Boolean>()
        val p = prefs(context)
        if (p.getString("vis_key", null) == key) {
            p.getStringSet("vis_yes", emptySet())?.forEach { map[it] = true }
            p.getStringSet("vis_no", emptySet())?.forEach { map[it] = false }
        }
        visibilities[key] = Visibility(key, map)
        while (visibilities.size > MAX_VISIBILITY_KEYS) visibilities.remove(visibilities.keys.first())
        map
    }

    fun remember(key: String, path: String, visible: Boolean) {
        val v = synchronized(visibilities) { visibilities[key] } ?: return
        v.map[path] = visible
        v.dirty = true
    }

    /** 判定を足したときだけ保存する（保存するのは最後に使ったキーの分だけ）。 */
    fun saveVisibility(context: Context, key: String) {
        val v = synchronized(visibilities) { visibilities[key] } ?: return
        if (!v.dirty) return
        v.dirty = false
        val yes = HashSet<String>()
        val no = HashSet<String>()
        v.map.forEach { (path, visible) -> if (visible) yes.add(path) else no.add(path) }
        prefs(context).edit().putString("vis_key", key).putStringSet("vis_yes", yes).putStringSet("vis_no", no).apply()
    }

    /** フォルダを隠した・戻した・除外したときなど、判定が変わる操作のあとに呼ぶ。 */
    fun clearVisibility(context: Context) {
        synchronized(visibilities) { visibilities.clear() }
        prefs(context).edit().remove("vis_key").remove("vis_yes").remove("vis_no").apply()
    }

    // ---- フォルダの「実際の場所」 ----

    private val distinct = ConcurrentHashMap<String, String>()

    @Volatile
    private var distinctLoaded = false

    @Volatile
    private var distinctDirty = false

    /** アプリの起動時に裏で読んでおく。読み終わる前に聞かれたら、その場で調べる（今までと同じ）。 */
    fun loadDistinct(context: Context) {
        if (distinctLoaded) return
        try {
            prefs(context).getStringSet("distinct", emptySet())?.forEach {
                val tab = it.indexOf('\t')
                if (tab > 0) distinct.putIfAbsent(it.substring(0, tab), it.substring(tab + 1))
            }
        } catch (ignored: Exception) {
        }
        distinctLoaded = true
    }

    fun getDistinct(path: String): String? = distinct[path]

    fun putDistinct(path: String, value: String) {
        if (distinct.size > MAX_DISTINCT) distinct.clear()
        distinct[path] = value
        distinctDirty = true
    }

    fun saveDistinct(context: Context) {
        if (!distinctDirty || !distinctLoaded) return
        distinctDirty = false
        val set = HashSet<String>(distinct.size)
        distinct.forEach { (k, v) -> set.add("$k\t$v") }
        prefs(context).edit().putStringSet("distinct", set).apply()
    }

    // ---- ボリュームごとの画像・動画の枚数（差分読み込みで、画像以外の変化かを見分ける） ----

    fun mediaCounts(context: Context): Map<String, Int> =
        (prefs(context).getString("media_counts", null) ?: "").split(';').filter { it.contains('=') }.mapNotNull {
            val count = it.substringAfter('=').toIntOrNull() ?: return@mapNotNull null
            it.substringBefore('=') to count
        }.toMap()

    fun saveMediaCounts(context: Context, counts: Map<String, Int>) {
        prefs(context).edit().putString("media_counts", counts.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    fun clearMediaCounts(context: Context) {
        prefs(context).edit().remove("media_counts").apply()
    }

    // ---- アルバム一覧を保存した時刻（エクスプローラーが保存済みの枚数を使ってよいかの判断に使う） ----

    fun dirsSavedAt(context: Context): Long = prefs(context).getLong("dirs_saved_at", 0L)

    /** アルバム一覧（アプリのデータベース）が Android の一覧と同じになった時刻を記録する。 */
    fun markDirsSaved(context: Context) {
        prefs(context).edit().putLong("dirs_saved_at", System.currentTimeMillis()).apply()
    }

    // ---- 消えたフォルダの確認 ----

    /** 前回の確認から Android の一覧が変わっていれば true（変わっていなければ消えたフォルダもない）。 */
    fun needsInvalidCheck(context: Context, stamp: String?): Boolean =
        stamp == null || prefs(context).getString("invalid_check_stamp", null) != stamp

    fun invalidChecked(context: Context, stamp: String?) {
        if (stamp != null) prefs(context).edit().putString("invalid_check_stamp", stamp).apply()
    }
}
