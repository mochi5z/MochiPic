package org.fossify.gallery.mochipic

import android.content.Context

/**
 * MochiPic: どのフォルダを読みに行くか（アルバム一覧・日付表示・コピー先の一覧に効く）。
 *  - 探索フォルダー（QP-Gallery と同じ）: 指定があれば、その中だけを探す。指定がなければストレージ全体
 *  - アプリ用のフォルダを読み込まない: Android/data・obb・media と、キャッシュ用の隠しフォルダ（.thumbnails など）を最初から除外
 * Fossify の「読み込むフォルダ（追加フォルダ）」に入れたフォルダは、どちらの決まりよりも優先して出す。
 */
object ScanRules {
    @Volatile
    var scanRoots: Set<String> = emptySet()
        private set

    @Volatile
    var skipAppFolders: Boolean = true
        private set

    private val APP_DIR = Regex("/Android/(data|obb|media)(/|$)", RegexOption.IGNORE_CASE)

    /** キャッシュや一時ファイル用としてよく使われる隠しフォルダの名前。 */
    private val CACHE_NAMES = setOf(".thumbnails", ".thumbs", ".cache", ".tmp", ".temp", ".trash", ".trashed", ".pending", ".recycle", ".recyclebin")

    fun load(context: Context) {
        val prefs = MochiPicPrefs(context)
        scanRoots = prefs.scanFolders.map { it.trimEnd('/') }.toSet()
        skipAppFolders = prefs.skipAppFolders
    }

    fun isAllowed(path: String): Boolean {
        val roots = scanRoots
        if (roots.isNotEmpty() && roots.none { path.equals(it, true) || path.startsWith("$it/", true) }) return false
        if (skipAppFolders) {
            if (APP_DIR.containsMatchIn(path)) return false
            if (path.split('/').any { seg ->
                    seg.startsWith('.') && (seg.lowercase() in CACHE_NAMES || seg.startsWith(".trashed-", true) || seg.startsWith(".pending-", true))
                }
            ) return false
        }
        return true
    }
}
