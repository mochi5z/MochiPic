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
    private const val GROUP_EXPLORER = 2
    private const val EXPLORER = -100

    /** Fossify の他のメニューと同じテーマ（明るさ・文字色）で出す。 */
    private fun themed(activity: Activity) = ContextThemeWrapper(activity, activity.getPopupMenuTheme())

    /** アルバム一覧の表示モード（QP-Gallery と同じ 4 つ）。 */
    private val MODES = listOf(
        AlbumStyles.Mode.DEFAULT to R.string.mochipic_view_default,
        AlbumStyles.Mode.STACK to R.string.mochipic_view_stack,
        AlbumStyles.Mode.GRID to R.string.mochipic_view_grid,
        AlbumStyles.Mode.LIST to R.string.mochipic_view_list,
    )

    /**
     * 表示モード: デフォルト／スタック／グリッド／リスト（選んでいるものに印）と、
     * 別枠の「エクスプローラー」（チェックの入れ外しで、フォルダ階層をたどる画面との行き来）。
     */
    fun showViewType(
        activity: Activity, anchor: View, onExplorer: (() -> Unit)? = null, explorerChecked: Boolean = false, onChanged: () -> Unit,
    ) {
        val config = activity.config
        val current = AlbumStyles.current(config)
        val popup = PopupMenu(themed(activity), anchor, Gravity.END)
        val menu = popup.menu
        MODES.forEachIndexed { i, (mode, title) -> menu.add(GROUP, i, i, title).isChecked = mode == current }
        menu.setGroupCheckable(GROUP, true, true)
        MODES.indexOfFirst { it.first == current }.takeIf { it >= 0 }?.let { menu.findItem(it)?.isChecked = true }
        if (onExplorer != null) {
            menu.add(GROUP_EXPLORER, EXPLORER, MODES.size, R.string.mochipic_view_explorer).apply {
                isCheckable = true
                isChecked = explorerChecked
            }
        }
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == EXPLORER) {
                onExplorer?.invoke()
            } else {
                val mode = MODES[item.itemId].first
                if (mode != current) {
                    AlbumStyles.set(config, mode)
                    onChanged()
                }
            }
            true
        }
        popup.show()
    }

    /** アルバムの中身の表示モード: グリッド／リスト（QP-Gallery と同じ 2 つ）。 */
    fun showMediaViewType(activity: Activity, anchor: View, path: String, onChanged: () -> Unit) {
        val config = activity.config
        val current = config.getFolderViewType(path)
        val popup = PopupMenu(themed(activity), anchor, Gravity.END)
        val menu = popup.menu
        menu.add(GROUP, VIEW_TYPE_GRID, 0, R.string.mochipic_view_grid)
        menu.add(GROUP, VIEW_TYPE_LIST, 1, R.string.mochipic_view_list)
        menu.setGroupCheckable(GROUP, true, true)
        menu.findItem(current)?.isChecked = true
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId != current) {
                config.viewTypeFiles = item.itemId
                config.removeFolderViewType(path)
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

    /**
     * 同じ種類をもう一度選ぶと向きが入れ替わる。別の種類を選んだときは、名前・場所は昇順（0→9、A→Z）、
     * 日時は降順（新しい順）から始める。数値順の設定はそのまま。
     */
    fun nextSorting(current: Int, chosen: Int): Int =
        if (kindOf(current) == chosen) {
            current xor SORT_DESCENDING
        } else {
            val numeric = current and SORT_USE_NUMERIC_VALUE
            val direction = if (chosen == SORT_BY_NAME || chosen == SORT_BY_PATH) 0 else SORT_DESCENDING
            numeric or direction or chosen
        }

    /** アルバムの中身の並べ替え（QP-Gallery と同じ 4 つ）。 */
    private val MEDIA_SORTS = listOf(
        SORT_BY_NAME to R.string.mochipic_sort_name,
        SORT_BY_DATE_MODIFIED to R.string.mochipic_sort_modified,
        org.fossify.commons.helpers.SORT_BY_DATE_TAKEN to R.string.mochipic_sort_taken,
        org.fossify.commons.helpers.SORT_BY_SIZE to R.string.mochipic_sort_size,
    )

    /**
     * アルバムの中身の並べ替えメニュー（QP-Gallery と同じ短いメニュー）。選んでいる項目に ▲（昇順）／▼（降順）。
     * 同じ項目をもう一度選ぶと向きが入れ替わる。このフォルダだけの並べ替えが設定されていれば、それを変える。
     */
    fun showMediaSort(activity: Activity, anchor: View, path: String, onChanged: () -> Unit) {
        val config = activity.config
        val current = config.getFolderSorting(path)
        val popup = PopupMenu(themed(activity), anchor, Gravity.END)
        MEDIA_SORTS.forEachIndexed { i, (kind, title) ->
            var text = activity.getString(title)
            if (kindOf(current) == kind) {
                text += if (current and SORT_DESCENDING != 0) "  ▼" else "  ▲"
            }
            popup.menu.add(Menu.NONE, kind, i, text)
        }
        popup.setOnMenuItemClickListener { item ->
            val next = nextSorting(current, item.itemId)
            if (config.hasCustomSorting(path)) {
                config.saveCustomSorting(path, next)
            } else {
                config.sorting = next
            }
            onChanged()
            true
        }
        popup.show()
    }

    fun showSort(activity: Activity, anchor: View, onDetailed: () -> Unit, onChanged: () -> Unit, detailed: Boolean = true) {
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
        if (detailed) menu.add(Menu.NONE, DETAILED, SORTS.size, R.string.mochipic_sort_detailed)
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
