package org.fossify.gallery.mochipic

import android.content.Context

/** MochiPic で追加した設定（Fossify の設定とは別のファイルに保存）。 */
class MochiPicPrefs(context: Context) {
    companion object {
        const val LOAD_MEDIASTORE = 0
        const val LOAD_FULL = 1
    }

    private val appContext = context.applicationContext
    private val prefs = context.applicationContext.getSharedPreferences("mochipic_settings", Context.MODE_PRIVATE)

    /** オン: 1 回タップで拡大・縮小、2 回タップでバーの表示切り替え（QP-Gallery の設定）。初期値はオフ。 */
    var singleTapZoom: Boolean
        get() = prefs.getBoolean("single_tap_zoom", false)
        set(value) = prefs.edit().putBoolean("single_tap_zoom", value).apply()

    /** オン: 起動するとアルバム一覧ではなくエクスプローラー（フォルダ階層）を開く。 */
    var explorerMode: Boolean
        get() = prefs.getBoolean("explorer_mode", false)
        set(value) = prefs.edit().putBoolean("explorer_mode", value).apply()

    /**
     * 隠しフォルダを出すか（アルバム一覧の目のボタンで切り替え、次の起動でもそのまま）。初期値は出す。
     * Android 11 以降は「すべてのファイルへのアクセス」を許可しているときだけ実際に出せる。
     */
    var showHiddenFolders: Boolean
        get() = prefs.getBoolean("show_hidden_folders", true)
        set(value) = prefs.edit().putBoolean("show_hidden_folders", value).apply()

    /**
     * アルバム一覧の読み込み方。[LOAD_MEDIASTORE]（標準）は Android の画像の一覧を 1 回読むだけで速い。
     * [LOAD_FULL] はフォルダを 1 つずつ直接調べる（遅いが、Android の一覧に載らない画像も拾う）。
     */
    var loadMode: Int
        get() = prefs.getInt("load_mode", LOAD_MEDIASTORE)
        set(value) = prefs.edit().putInt("load_mode", value).apply()

    /** 前回フォルダを調べ終えたときの Android の画像の一覧の状態（変化がなければ調べ直さないため）。 */
    var lastScanState: String?
        get() = prefs.getString("last_scan_state", null)
        set(value) = prefs.edit().putString("last_scan_state", value).apply()

    /** 探索フォルダー（空ならストレージ全体を探す）。 */
    var scanFolders: Set<String>
        get() = prefs.getStringSet("scan_folders", emptySet())!!.toSet()
        set(value) {
            prefs.edit().putStringSet("scan_folders", value).apply()
            ScanRules.load(appContext)
        }

    /** オン: アプリ用のフォルダ（Android/data など）とキャッシュ用の隠しフォルダを読み込まない。 */
    var skipAppFolders: Boolean
        get() = prefs.getBoolean("skip_app_folders", true)
        set(value) {
            prefs.edit().putBoolean("skip_app_folders", value).apply()
            ScanRules.load(appContext)
        }

    /** オン: 表示画面で開いた画像（プレビュー）を端末に保存しておき、次に開くときに速く出す。 */
    var previewCache: Boolean
        get() = prefs.getBoolean("preview_cache", true)
        set(value) = prefs.edit().putBoolean("preview_cache", value).apply()

    /** オン: 一覧を勢いよくスクロールしている間はサムネイルを読み込まず、止まってから読み込む。 */
    var lazyLoad: Boolean
        get() = prefs.getBoolean("lazy_load", false)
        set(value) = prefs.edit().putBoolean("lazy_load", value).apply()

    /** オン: アプリを閉じるときにキャッシュを消す。 */
    var clearCacheOnExit: Boolean
        get() = prefs.getBoolean("clear_cache_on_exit", false)
        set(value) = prefs.edit().putBoolean("clear_cache_on_exit", value).apply()
}

val Context.mochiPrefs: MochiPicPrefs get() = MochiPicPrefs(this)
