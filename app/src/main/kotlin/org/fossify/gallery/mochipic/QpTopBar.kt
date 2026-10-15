package org.fossify.gallery.mochipic

import android.app.Activity
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.RelativeLayout
import android.widget.TextView
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.views.MySearchMenu
import org.fossify.gallery.R

/**
 * MochiPic: Fossify の検索欄つき上部バー（MySearchMenu）を、QP-Gallery の「≡ アルバム  [並べ替え][検索][︙]」に変える。
 * 検索欄は普段は隠し、検索ボタンを押したときだけ出す。閉じるとタイトルに戻る。
 * MySearchMenu の中身はそのまま使うので、検索の処理（文字が変わったときの絞り込みなど）は各画面の既存の処理が動く。
 */
object QpTopBar {

    fun install(activity: Activity, bar: MySearchMenu, title: String, onMenu: () -> Unit) {
        val b = bar.binding
        b.toolbarContainer.background = null  // Fossify の丸い検索欄の枠を外して、平らなバーにする

        val titleView = TextView(activity).apply {
            id = R.id.mochi_top_bar_title
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(org.fossify.commons.R.dimen.big_text_size) * 1.15f)
            setTextColor(activity.getProperTextColor())
            maxLines = 1
            setPadding(resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin), 0, 0, 0)
        }
        val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
            addRule(RelativeLayout.CENTER_VERTICAL)
            addRule(RelativeLayout.END_OF, b.topToolbarSearchIcon.id)
            addRule(RelativeLayout.START_OF, b.topToolbar.id)
        }
        b.toolbarContainer.addView(titleView, lp)
        b.topToolbarSearch.beGone()
        showMenuIcon(bar)

        b.topToolbarSearchIcon.setOnClickListener {
            if (bar.isSearchOpen) bar.closeSearch() else onMenu()
        }
        val previousClosed = bar.onSearchClosedListener
        bar.onSearchClosedListener = {
            previousClosed?.invoke()
            // closeSearch() はこの後でアイコンを虫めがねに戻すので、それが終わってから ≡ に戻す
            bar.post { restore(bar) }
        }

        val search = bar.requireToolbar().menu.add(Menu.NONE, R.id.mochi_search, Menu.NONE, org.fossify.commons.R.string.search)
        search.setIcon(org.fossify.commons.R.drawable.ic_search_vector)
        search.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
    }

    /** 各画面のメニューのクリック処理から呼ぶ。検索ボタンなら検索欄を開いて true。 */
    fun handleMenuClick(activity: Activity, bar: MySearchMenu, item: MenuItem): Boolean {
        if (item.itemId != R.id.mochi_search) return false
        val b = bar.binding
        b.toolbarContainer.findViewById<TextView>(R.id.mochi_top_bar_title)?.beGone()
        b.topToolbarSearch.beVisible()
        b.topToolbarSearch.requestFocus()  // フォーカスが入ると MySearchMenu が検索中の表示（← アイコン）に変わる
        activity.showKeyboard(b.topToolbarSearch)
        return true
    }

    fun setTitle(bar: MySearchMenu, title: String) {
        bar.binding.toolbarContainer.findViewById<TextView>(R.id.mochi_top_bar_title)?.text = title
    }

    fun updateColors(activity: Activity, bar: MySearchMenu) {
        bar.binding.toolbarContainer.findViewById<TextView>(R.id.mochi_top_bar_title)?.setTextColor(activity.getProperTextColor())
    }

    private fun restore(bar: MySearchMenu) {
        val b = bar.binding
        b.topToolbarSearch.clearFocus()
        b.topToolbarSearch.beGone()
        b.toolbarContainer.findViewById<TextView>(R.id.mochi_top_bar_title)?.beVisible()
        showMenuIcon(bar)
    }

    private fun showMenuIcon(bar: MySearchMenu) {
        bar.binding.topToolbarSearchIcon.setImageResource(R.drawable.mochipic_ic_menu)
        bar.binding.topToolbarSearchIcon.contentDescription = bar.context.getString(R.string.mochipic_open_side_menu)
    }
}
