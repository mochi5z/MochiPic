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
}

val Context.mochiPrefs: MochiPicPrefs get() = MochiPicPrefs(this)
