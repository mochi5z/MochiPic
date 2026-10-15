package org.fossify.gallery.mochipic

import android.app.Activity
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ContextThemeWrapper
import androidx.appcompat.widget.PopupMenu
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_PATH
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config

/**
 * MochiPic: アルバム一覧の QP-Gallery 風メニュー。
 * Fossify の大きなダイアログの代わりに、右上から出る短いメニューで選ぶ。
 * 細かい並べ替え（撮影日時・サイズ・数値順など）は「詳細な並べ替え」から Fossify のダイアログを開く。
 */
object AlbumMenus {

    private const val GROUP = 1

    /** Fossify の他のメニューと同じテーマ（明るさ・文字色）で出す。 */
    private fun themed(activity: Activity) = ContextThemeWrapper(activity, activity.getPopupMenuTheme())

    /** 表示形式: グリッド／リスト（選んでいるものに印）。 */
    fun showViewType(activity: Activity, anchor: View, onChanged: () -> Unit) {
        val config = activity.config
        val popup = PopupMenu(themed(activity), anchor, Gravity.END)
        val menu = popup.menu
        menu.add(GROUP, VIEW_TYPE_GRID, 0, R.string.mochipic_view_grid)
        menu.add(GROUP, VIEW_TYPE_LIST, 1, R.string.mochipic_view_list)
        menu.setGroupCheckable(GROUP, true, true)
        menu.findItem(config.viewTypeFolders)?.isChecked = true
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId != config.viewTypeFolders) {
                config.viewTypeFolders = item.itemId
                onChanged()
            }
            true
        }
        popup.show()
    }

    /** 並べ替えの種類（QP-Gallery と同じ 3 つ）。選んでいる項目をもう一度選ぶと昇順・降順が入れ替わる。 */
    private val SORTS = listOf(
        SORT_BY_NAME to R.string.mochipic_sort_name,
        SORT_BY_DATE_MODIFIED to R.string.mochipic_sort_modified,
        SORT_BY_PATH to R.string.mochipic_sort_path,
    )
    private const val DETAILED = -1

    /** 並べ替えの値のうち「向き・数値順」の印（それ以外のビットが並べ替えの種類）。 */
    private const val SORT_OPTIONS = SORT_DESCENDING or SORT_USE_NUMERIC_VALUE

    private fun kindOf(sorting: Int) = sorting and SORT_OPTIONS.inv()

    fun nextSorting(current: Int, chosen: Int): Int =
        if (kindOf(current) == chosen) {
            current xor SORT_DESCENDING
        } else {
            (current and SORT_OPTIONS) or chosen
        }

    fun showSort(activity: Activity, anchor: View, onDetailed: () -> Unit, onChanged: () -> Unit) {
        val config = activity.config
        val current = config.directorySorting
        val popup = PopupMenu(themed(activity), anchor, Gravity.END)
        val menu = popup.menu
        SORTS.forEachIndexed { i, (kind, title) ->
            var text = activity.getString(title)
            if (kindOf(current) == kind) {
                text += if (current and SORT_DESCENDING != 0) "  ▼" else "  ▲"
            }
            menu.add(Menu.NONE, kind, i, text)
        }
        menu.add(Menu.NONE, DETAILED, SORTS.size, R.string.mochipic_sort_detailed)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == DETAILED) {
                onDetailed()
            } else {
                config.directorySorting = nextSorting(config.directorySorting, item.itemId)
                onChanged()
            }
            true
        }
        popup.show()
    }
}
