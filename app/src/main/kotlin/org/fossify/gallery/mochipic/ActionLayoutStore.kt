package org.fossify.gallery.mochipic

import android.content.Context

/** ボタン配置を設定できる画面。画面を増やすときはここに足し、[defaults] と画面側の一覧を用意する。 */
enum class LayoutScreen(val key: String) {
    VIEWER("viewer"),
}

/** MochiPic: ボタン配置の保存（端末内の SharedPreferences）。 */
object ActionLayoutStore {
    private const val PREFS = "mochipic_action_layout"

    fun defaults(screen: LayoutScreen): ActionLayout = when (screen) {
        LayoutScreen.VIEWER -> ViewerActions.DEFAULT_LAYOUT
    }

    fun load(context: Context, screen: LayoutScreen): ActionLayout {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(screen.key, null)
        return ActionLayout.parse(text, defaults(screen))
    }

    fun save(context: Context, screen: LayoutScreen, layout: ActionLayout) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(screen.key, layout.serialize())
            .apply()
    }

    fun reset(context: Context, screen: LayoutScreen) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(screen.key).apply()
    }
}
