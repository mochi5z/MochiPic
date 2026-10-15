package org.fossify.gallery.mochipic

import android.content.Context

/** MochiPic で追加した設定（Fossify の設定とは別のファイルに保存）。 */
class MochiPicPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mochipic_settings", Context.MODE_PRIVATE)

    /** オン: 1 回タップで拡大・縮小、2 回タップでバーの表示切り替え（QP-Gallery の設定）。初期値はオフ。 */
    var singleTapZoom: Boolean
        get() = prefs.getBoolean("single_tap_zoom", false)
        set(value) = prefs.edit().putBoolean("single_tap_zoom", value).apply()

    /** オン: 起動するとアルバム一覧ではなくエクスプローラー（フォルダ階層）を開く。 */
    var explorerMode: Boolean
        get() = prefs.getBoolean("explorer_mode", false)
        set(value) = prefs.edit().putBoolean("explorer_mode", value).apply()

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
