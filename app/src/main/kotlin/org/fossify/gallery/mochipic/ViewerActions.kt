package org.fossify.gallery.mochipic

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import org.fossify.gallery.R
import org.fossify.gallery.databinding.BottomActionsBinding

/**
 * MochiPic: 画像・動画の表示画面に置けるボタンの一覧（アイコン・名前・メニューの ID・下部バーの部品）。
 * 置き場所と並び順は [ActionLayout] が持ち、ここは「何があるか」だけを定義する。
 */
object ViewerActions {

    /** 初期配置: QP-Gallery 9.7 のスクリーンショットに合わせる（︙メニューの中身は仮）。 */
    /**
     * 初期配置（QP-Gallery の画面に合わせ、ユーザーの指定で 移動・コピー・名前の変更 を上部バーに、編集と共有を︙に移した形）。
     * 上部バー: 削除・移動・コピー・名前の変更、下部: 情報（解像度と日時の 1 行表示つき）、
     * ︙: スライドショー・壁紙などに設定・回転・マップに表示・編集・共有・拡大・縮小。それ以外は非表示（ボタンの配置で出せる）。
     */
    val DEFAULT_LAYOUT = ActionLayout(
        listOf(
            ActionEntry(ActionId.DELETE, Slot.TOP),
            ActionEntry(ActionId.MOVE, Slot.TOP),
            ActionEntry(ActionId.COPY, Slot.TOP),
            ActionEntry(ActionId.RENAME, Slot.TOP),
            ActionEntry(ActionId.PROPERTIES, Slot.BOTTOM),
            ActionEntry(ActionId.SLIDESHOW, Slot.MENU),
            ActionEntry(ActionId.SET_AS, Slot.MENU),
            ActionEntry(ActionId.ROTATE, Slot.MENU),
            ActionEntry(ActionId.SHOW_ON_MAP, Slot.MENU),
            ActionEntry(ActionId.EDIT, Slot.MENU),
            ActionEntry(ActionId.SHARE, Slot.MENU),
            ActionEntry(ActionId.ZOOM_IN, Slot.MENU),
            ActionEntry(ActionId.ZOOM_OUT, Slot.MENU),
            ActionEntry(ActionId.FAVORITE, Slot.HIDDEN),
            ActionEntry(ActionId.TOGGLE_VISIBILITY, Slot.HIDDEN),
            ActionEntry(ActionId.RESIZE, Slot.HIDDEN),
            ActionEntry(ActionId.CHANGE_ORIENTATION, Slot.HIDDEN),
            ActionEntry(ActionId.OPEN_WITH, Slot.HIDDEN),
            ActionEntry(ActionId.COPY_TO_CLIPBOARD, Slot.HIDDEN),
            ActionEntry(ActionId.CREATE_SHORTCUT, Slot.HIDDEN),
            ActionEntry(ActionId.PRINT, Slot.HIDDEN),
            ActionEntry(ActionId.SETTINGS, Slot.HIDDEN),
        )
    )

    /** ボタンの名前（設定画面で表示）。 */
    fun titleRes(action: ActionId): Int = when (action) {
        ActionId.EDIT -> R.string.edit
        ActionId.DELETE -> org.fossify.commons.R.string.delete
        ActionId.SHARE -> org.fossify.commons.R.string.share
        ActionId.PROPERTIES -> org.fossify.commons.R.string.properties
        ActionId.SLIDESHOW -> R.string.slideshow
        ActionId.ROTATE -> R.string.rotate
        ActionId.RENAME -> org.fossify.commons.R.string.rename
        ActionId.COPY -> org.fossify.commons.R.string.copy_to
        ActionId.MOVE -> org.fossify.commons.R.string.move_to
        ActionId.SET_AS -> org.fossify.commons.R.string.set_as
        ActionId.FAVORITE -> R.string.toggle_favorite
        ActionId.TOGGLE_VISIBILITY -> R.string.mochipic_action_toggle_visibility
        ActionId.RESIZE -> org.fossify.commons.R.string.resize
        ActionId.CHANGE_ORIENTATION -> R.string.change_orientation
        ActionId.SHOW_ON_MAP -> R.string.show_on_map
        ActionId.OPEN_WITH -> org.fossify.commons.R.string.open_with
        ActionId.COPY_TO_CLIPBOARD -> org.fossify.commons.R.string.copy_to_clipboard
        ActionId.CREATE_SHORTCUT -> org.fossify.commons.R.string.create_shortcut
        ActionId.PRINT -> org.fossify.commons.R.string.print
        ActionId.SETTINGS -> org.fossify.commons.R.string.settings
        ActionId.ZOOM_IN -> R.string.mochipic_zoom_in
        ActionId.ZOOM_OUT -> R.string.mochipic_zoom_out
    }

    /** ボタンのアイコン（設定画面で表示）。無いものは 0。 */
    fun iconRes(action: ActionId): Int = when (action) {
        ActionId.EDIT -> org.fossify.commons.R.drawable.ic_edit_vector
        ActionId.DELETE -> org.fossify.commons.R.drawable.ic_delete_vector
        ActionId.SHARE -> org.fossify.commons.R.drawable.ic_share_vector
        ActionId.PROPERTIES -> org.fossify.commons.R.drawable.ic_info_vector
        ActionId.SLIDESHOW -> R.drawable.ic_slideshow_vector
        ActionId.ROTATE -> R.drawable.ic_rotate_right_vector
        ActionId.RENAME -> org.fossify.commons.R.drawable.ic_rename_vector
        ActionId.COPY -> org.fossify.commons.R.drawable.ic_copy_vector
        ActionId.MOVE -> org.fossify.commons.R.drawable.ic_move_vector
        ActionId.SET_AS -> org.fossify.commons.R.drawable.ic_set_as_vector
        ActionId.FAVORITE -> org.fossify.commons.R.drawable.ic_star_outline_vector
        ActionId.TOGGLE_VISIBILITY -> org.fossify.commons.R.drawable.ic_hide_vector
        ActionId.RESIZE -> R.drawable.ic_minimize_vector
        ActionId.CHANGE_ORIENTATION -> org.fossify.commons.R.drawable.ic_orientation_auto_vector
        ActionId.SHOW_ON_MAP -> org.fossify.commons.R.drawable.ic_place_vector
        ActionId.SETTINGS -> org.fossify.commons.R.drawable.ic_settings_cog_vector
        ActionId.PRINT -> org.fossify.commons.R.drawable.ic_print_vector
        ActionId.ZOOM_IN -> R.drawable.mochipic_ic_zoom_in
        ActionId.ZOOM_OUT -> R.drawable.mochipic_ic_zoom_out
        else -> 0
    }

    /** そのボタンに対応する上部バー・︙メニューの項目（お気に入りなどは状態で 2 つを切り替える）。 */
    fun menuIds(action: ActionId): List<Int> = when (action) {
        ActionId.EDIT -> listOf(R.id.menu_edit)
        ActionId.DELETE -> listOf(R.id.menu_delete)
        ActionId.SHARE -> listOf(R.id.menu_share)
        ActionId.PROPERTIES -> listOf(R.id.menu_properties)
        ActionId.SLIDESHOW -> listOf(R.id.menu_slideshow)
        ActionId.ROTATE -> listOf(R.id.menu_rotate)
        ActionId.RENAME -> listOf(R.id.menu_rename)
        ActionId.COPY -> listOf(R.id.menu_copy_to)
        ActionId.MOVE -> listOf(R.id.menu_move_to)
        ActionId.SET_AS -> listOf(R.id.menu_set_as)
        ActionId.FAVORITE -> listOf(R.id.menu_add_to_favorites, R.id.menu_remove_from_favorites)
        ActionId.TOGGLE_VISIBILITY -> listOf(R.id.menu_hide, R.id.menu_unhide)
        ActionId.RESIZE -> listOf(R.id.menu_resize)
        ActionId.CHANGE_ORIENTATION -> listOf(R.id.menu_change_orientation)
        ActionId.SHOW_ON_MAP -> listOf(R.id.menu_show_on_map)
        ActionId.OPEN_WITH -> listOf(R.id.menu_open_with)
        ActionId.COPY_TO_CLIPBOARD -> listOf(R.id.menu_copy_to_clipboard)
        ActionId.CREATE_SHORTCUT -> listOf(R.id.menu_create_shortcut)
        ActionId.PRINT -> listOf(R.id.menu_print)
        ActionId.SETTINGS -> listOf(R.id.menu_settings)
        ActionId.ZOOM_IN -> listOf(R.id.menu_zoom_in)
        ActionId.ZOOM_OUT -> listOf(R.id.menu_zoom_out)
    }

    /** そのボタンの下部バー用の部品。下部バーに置けないボタンは null。 */
    fun bottomView(b: BottomActionsBinding, action: ActionId): View? = when (action) {
        ActionId.EDIT -> b.bottomEdit
        ActionId.DELETE -> b.bottomDelete
        ActionId.SHARE -> b.bottomShare
        ActionId.PROPERTIES -> b.bottomProperties
        ActionId.SLIDESHOW -> b.bottomSlideshow
        ActionId.ROTATE -> b.bottomRotate
        ActionId.RENAME -> b.bottomRename
        ActionId.COPY -> b.bottomCopy
        ActionId.MOVE -> b.bottomMove
        ActionId.SET_AS -> b.bottomSetAs
        ActionId.FAVORITE -> b.bottomFavorite
        ActionId.TOGGLE_VISIBILITY -> b.bottomToggleFileVisibility
        ActionId.RESIZE -> b.bottomResize
        ActionId.CHANGE_ORIENTATION -> b.bottomChangeOrientation
        ActionId.SHOW_ON_MAP -> b.bottomShowOnMap
        ActionId.ZOOM_IN -> b.bottomZoomIn
        ActionId.ZOOM_OUT -> b.bottomZoomOut
        else -> null
    }

    /**
     * 上部バーと︙メニューの項目を、配置の順番に並べ直す。
     * Android のメニューは後から順番を変えられないため、項目を控えてから作り直す。
     * 配置に無い項目（「名前を付けて保存」「元に戻す」など状況で出るもの）は後ろに残す。
     */
    fun reorderMenu(menu: Menu, layout: ActionLayout) {
        data class Child(val id: Int, val title: CharSequence?)
        data class Item(val id: Int, val title: CharSequence?, val icon: android.graphics.drawable.Drawable?, val children: List<Child>?)

        val items = (0 until menu.size()).map { i ->
            val it = menu.getItem(i)
            val children = it.subMenu?.let { sub -> (0 until sub.size()).map { j -> Child(sub.getItem(j).itemId, sub.getItem(j).title) } }
            Item(it.itemId, it.title, it.icon, children)
        }
        val wanted = layout.entries.flatMap { menuIds(it.action) }
        val ordered = wanted.mapNotNull { id -> items.firstOrNull { it.id == id } } + items.filter { it.id !in wanted }

        menu.clear()
        ordered.forEachIndexed { order, item ->
            if (item.children != null) {
                val sub = menu.addSubMenu(Menu.NONE, item.id, order, item.title)
                sub.item.icon = item.icon
                item.children.forEach { c -> sub.add(Menu.NONE, c.id, Menu.NONE, c.title) }
            } else {
                menu.add(Menu.NONE, item.id, order, item.title).icon = item.icon
            }
        }
    }

    /** 上部バーなら空きがある限りアイコンで出し（入りきらなければ自動で︙メニューへ）、メニューなら︙メニューに入れる。 */
    fun applyShowAsAction(menu: Menu, layout: ActionLayout) {
        for (entry in layout.entries) {
            // 上部バーに置いたボタンは必ず出す（QP-Gallery と同じく 4 つ並ぶ）。アイコンのない項目にはアイコンを付ける
            val flag = if (entry.slot == Slot.TOP) MenuItem.SHOW_AS_ACTION_ALWAYS else MenuItem.SHOW_AS_ACTION_NEVER
            menuIds(entry.action).forEach { id ->
                val item = menu.findItem(id) ?: return@forEach
                item.setShowAsAction(flag)
                if (entry.slot == Slot.TOP && item.icon == null && iconRes(entry.action) != 0) {
                    item.setIcon(iconRes(entry.action))
                    item.icon?.mutate()?.setTint(android.graphics.Color.WHITE)
                }
            }
        }
    }

    /** 下部バーの部品を配置の順番に並べ直す（表示・非表示は呼び出し側で決める）。 */
    fun reorderBottomBar(b: BottomActionsBinding, layout: ActionLayout) {
        val bar = b.root as? LinearLayout ?: return
        val order = layout.inSlot(Slot.BOTTOM).mapNotNull { bottomView(b, it) }
        val others = (0 until bar.childCount).map { bar.getChildAt(it) }.filter { it !in order }
        bar.removeAllViews()
        (order + others).forEach { bar.addView(it) }
        // ボタンが少ないとき（初期配置は情報ボタンだけ）は、QP-Gallery と同じく左に寄せる
        val few = order.size <= 2
        bar.gravity = if (few) android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.CENTER
        val pad = (16 * bar.resources.displayMetrics.density).toInt()
        (order + others).forEach { v ->
            val lp = v.layoutParams as LinearLayout.LayoutParams
            if (few) {
                lp.width = LinearLayout.LayoutParams.WRAP_CONTENT
                lp.weight = 0f
                v.setPadding(pad, v.paddingTop, pad, v.paddingBottom)
            } else {
                lp.width = 0
                lp.weight = 1f
            }
            v.layoutParams = lp
        }
    }
}
