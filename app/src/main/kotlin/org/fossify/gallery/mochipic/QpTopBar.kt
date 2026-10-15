package org.fossify.gallery.mochipic

import android.app.Activity
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.RelativeLayout
import android.widget.TextView
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R

/**
 * MochiPic: Fossify の検索欄つき上部バー（MySearchMenu）を、QP-Gallery の「≡ タイトル  [ボタン…][︙]」に変える。
 * 検索欄は普段は隠し、検索ボタンを押したときだけ出す。閉じるとタイトルに戻る。
 * 色はテーマの主色（バー）と、その上で読みやすい色（文字・アイコン）。
 */
object QpTopBar {

    enum class Nav { MENU, BACK }

    private class State(var title: String, var nav: Nav, var drawerOpen: Boolean = false)

    fun install(
        activity: Activity,
        bar: MySearchMenu,
        title: String,
        nav: Nav = Nav.MENU,
        withSearch: Boolean = true,
        onNav: () -> Unit,
    ) {
        val b = bar.binding
        b.toolbarContainer.background = null  // Fossify の丸い検索欄の枠を外して、平らなバーにする
        bar.tag = State(title, nav)

        val titleView = TextView(activity).apply {
            id = R.id.mochi_top_bar_title
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTextColor(contrast(activity))
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(resources.getDimensionPixelSize(R.dimen.mochipic_top_bar_title_start), 0, 0, 0)
        }
        val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.CENTER_VERTICAL)
            addRule(RelativeLayout.END_OF, b.topToolbarSearchIcon.id)
            addRule(RelativeLayout.START_OF, b.topToolbar.id)
        }
        b.toolbarContainer.addView(titleView, lp)
        b.topToolbarSearch.beGone()
        showNavIcon(bar)

        b.topToolbarSearchIcon.setOnClickListener {
            if (bar.isSearchOpen) bar.closeSearch() else onNav()
        }
        val previousClosed = bar.onSearchClosedListener
        bar.onSearchClosedListener = {
            previousClosed?.invoke()
            // closeSearch() はこの後でアイコンを虫めがねに戻すので、それが終わってから戻す
            bar.post { restore(bar) }
        }

        if (withSearch) {
            val search = bar.requireToolbar().menu.add(Menu.NONE, R.id.mochi_search, Menu.NONE, org.fossify.commons.R.string.search)
            search.setIcon(org.fossify.commons.R.drawable.ic_search_vector)
            search.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    /** 各画面のメニューのクリック処理から呼ぶ。検索ボタンなら検索欄を開いて true。 */
    fun handleMenuClick(activity: Activity, bar: MySearchMenu, item: MenuItem): Boolean {
        if (item.itemId != R.id.mochi_search) return false
        val b = bar.binding
        titleOf(bar)?.beGone()
        b.topToolbarSearch.beVisible()
        b.topToolbarSearch.requestFocus()  // フォーカスが入ると MySearchMenu が検索中の表示（← アイコン）に変わる
        activity.showKeyboard(b.topToolbarSearch)
        return true
    }

    fun setTitle(bar: MySearchMenu, title: String) {
        (bar.tag as? State)?.title = title
        if ((bar.tag as? State)?.drawerOpen != true) titleOf(bar)?.text = title
    }

    /** サイドメニューを開いている間は「← ギャラリー」（QP-Gallery と同じ）。 */
    fun setDrawerOpen(bar: MySearchMenu, open: Boolean) {
        val state = bar.tag as? State ?: return
        state.drawerOpen = open
        titleOf(bar)?.text = if (open) bar.context.getString(R.string.mochipic_side_menu_title) else state.title
        showNavIcon(bar)
    }

    fun updateColors(activity: Activity, bar: MySearchMenu) {
        titleOf(bar)?.setTextColor(contrast(activity))
    }

    private fun contrast(activity: Activity) = activity.getProperPrimaryColor().getContrastColor()

    private fun titleOf(bar: MySearchMenu) = bar.binding.toolbarContainer.findViewById<TextView>(R.id.mochi_top_bar_title)

    private fun restore(bar: MySearchMenu) {
        val b = bar.binding
        b.topToolbarSearch.clearFocus()
        b.topToolbarSearch.beGone()
        titleOf(bar)?.beVisible()
        showNavIcon(bar)
    }

    private fun showNavIcon(bar: MySearchMenu) {
        val state = bar.tag as? State
        val back = state == null || state.nav == Nav.BACK || state.drawerOpen
        val icon = bar.binding.topToolbarSearchIcon
        if (back) {
            icon.setImageResource(org.fossify.commons.R.drawable.ic_arrow_left_vector)
            icon.contentDescription = bar.context.getString(org.fossify.commons.R.string.back)
        } else {
            icon.setImageResource(R.drawable.mochipic_ic_menu)
            icon.contentDescription = bar.context.getString(R.string.mochipic_open_side_menu)
        }
    }
}
