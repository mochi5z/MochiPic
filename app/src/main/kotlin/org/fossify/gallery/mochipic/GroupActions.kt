package org.fossify.gallery.mochipic

import android.annotation.SuppressLint
import android.app.Activity
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isAValidFilename
import org.fossify.commons.extensions.scanPathsRecursively
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.RadioItem
import org.fossify.commons.views.MyEditText
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.directoryDB
import java.io.File

/**
 * MochiPic: 作者ごとのグループの操作（2026-11-11、v0.1.11）。仕組みは [AuthorGroup]。
 *  - グループにまとめる: 選んだフォルダ（巻）を、親フォルダをグループにする／新しいグループを作って移動／既存のグループへ移動
 *  - グループに指定・解除: 目印ファイルを置く・消す（エクスプローラーから）
 *  - 巻の並べ替え: ドラッグで並べて目印ファイルに保存
 * どれも「すべてのファイルへのアクセス」が要る（無ければメニューに出さない）。
 */
object GroupActions {

    fun canUse(): Boolean = FolderDelete.hasFileAccess()

    // ---------------------------------------------------------------- グループにまとめる

    private const val OPT_PARENT = 1
    private const val OPT_NEW = 2
    private const val OPT_EXISTING = 100

    /** アルバム一覧で選んだフォルダをグループにまとめる。終わったら [done]（一覧の読み直し）。 */
    fun groupInto(activity: Activity, paths: List<String>, done: () -> Unit) {
        val folders = paths.map { it.trimEnd('/') }.filter { File(it).isDirectory }
        if (folders.isEmpty()) return
        val parents = folders.mapNotNull { File(it).parent }.distinct()
        val items = ArrayList<RadioItem>()
        val parent = parents.singleOrNull()
        if (parent != null && !FolderDelete.isProtected(activity, parent) && !AuthorGroup.isGroup(parent)) {
            items.add(RadioItem(OPT_PARENT, activity.getString(R.string.mochipic_group_use_parent, File(parent).name)))
        }
        items.add(RadioItem(OPT_NEW, activity.getString(R.string.mochipic_group_new)))
        val groups = AuthorGroup.knownGroups().filter { g -> folders.none { AuthorGroup.key(it) == AuthorGroup.key(g) } }
        groups.forEachIndexed { i, g -> items.add(RadioItem(OPT_EXISTING + i, activity.getString(R.string.mochipic_group_move_to, File(g).name))) }

        RadioGroupDialog(activity, items, titleId = R.string.mochipic_group_into) { chosen ->
            when (val id = chosen as Int) {
                OPT_PARENT -> markAsGroup(activity, parent!!, done)
                OPT_NEW -> askName(activity) { name -> createAndMove(activity, File(folders.first()).parent!!, name, folders, done) }
                else -> moveInto(activity, groups[id - OPT_EXISTING], folders, done)
            }
        }
    }

    /** フォルダをグループに指定する（目印ファイルを置く。今ある巻の並びは名前の数字順で書いておく）。 */
    fun markAsGroup(activity: Activity, groupPath: String, done: () -> Unit) {
        ensureBackgroundThread {
            val names = File(groupPath).listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.map { it.name }.orEmpty()
            val ok = AuthorGroup.writeMarker(groupPath, AuthorGroup.orderVolumes(names, emptyList()))
            activity.runOnUiThread {
                activity.toast(if (ok) R.string.mochipic_group_done else R.string.mochipic_group_failed)
                done()
            }
        }
    }

    fun unmarkGroup(activity: Activity, groupPath: String, done: () -> Unit) {
        ConfirmationDialog(activity, activity.getString(R.string.mochipic_group_unset_confirm, File(groupPath).name)) {
            ensureBackgroundThread {
                val ok = AuthorGroup.removeMarker(groupPath)
                activity.runOnUiThread {
                    if (!ok) activity.toast(R.string.mochipic_group_failed)
                    done()
                }
            }
        }
    }

    private fun askName(activity: Activity, callback: (String) -> Unit) {
        val pad = dp(activity, 20)
        val edit = MyEditText(activity).apply {
            setSingleLine()
            hint = activity.getString(R.string.mochipic_group_name_hint)
        }
        val box = LinearLayout(activity).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(box, this, R.string.mochipic_group_new) { dialog ->
                    dialog.showKeyboard(edit)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = edit.value.trim()
                        when {
                            name.isEmpty() -> activity.toast(org.fossify.commons.R.string.empty_name)
                            !name.isAValidFilename() || name.startsWith(".") -> activity.toast(org.fossify.commons.R.string.invalid_name)
                            else -> {
                                dialog.dismiss()
                                callback(name)
                            }
                        }
                    }
                }
            }
    }

    /** 新しいグループ（[parent] の下に [name]）を作って、[folders] をその中へ移動する。同じ名前のフォルダがあればそれを使う。 */
    private fun createAndMove(activity: Activity, parent: String, name: String, folders: List<String>, done: () -> Unit) {
        val group = File(parent, name)
        if (group.exists() && !group.isDirectory) {
            activity.toast(org.fossify.commons.R.string.name_taken)
            return
        }
        ensureBackgroundThread {
            if (!group.exists() && !group.mkdirs()) {
                activity.runOnUiThread { activity.toast(R.string.mochipic_group_failed) }
                return@ensureBackgroundThread
            }
            if (!AuthorGroup.isGroup(group.path)) AuthorGroup.writeMarker(group.path, emptyList())
            moveSync(activity, group.path, folders, done)
        }
    }

    private fun moveInto(activity: Activity, group: String, folders: List<String>, done: () -> Unit) {
        ensureBackgroundThread { moveSync(activity, group, folders, done) }
    }

    /**
     * フォルダを名前の変更（同じストレージの中の移動）でグループの中へ移す。コピーはしない。
     * 新しく入った巻は並び順の最後に足す。移せなかった（同じ名前がある・別のストレージ）数を知らせる。
     */
    private fun moveSync(activity: Activity, group: String, folders: List<String>, done: () -> Unit) {
        val moved = ArrayList<String>()
        var failed = 0
        for (path in folders) {
            val src = File(path)
            if (AuthorGroup.key(src.parent.orEmpty()) == AuthorGroup.key(group)) continue   // もう入っている
            val dst = File(group, src.name)
            if (dst.exists() || AuthorGroup.key(group).startsWith(AuthorGroup.key(path) + "/") || !src.renameTo(dst)) {
                failed++
                continue
            }
            moved.add(dst.path)
            try {
                activity.directoryDB.deleteDirPath(path)
            } catch (ignored: Exception) {
            }
        }
        if (moved.isNotEmpty()) {
            val order = AuthorGroup.savedOrder(group).toMutableList()
            moved.map { File(it).name }.filter { n -> order.none { it.equals(n, true) } }.sortedWith(AuthorGroup.naturalOrder).forEach { order.add(it) }
            AuthorGroup.writeMarker(group, order)
            MoveTouch.touch(activity, group)   // 更新順で並べたとき、まとめたグループが上に来るように
        }
        val finish = {
            activity.runOnUiThread {
                activity.toast(
                    if (failed == 0) activity.getString(R.string.mochipic_group_done)
                    else activity.getString(R.string.mochipic_group_move_partial, failed)
                )
                done()
            }
        }
        // Android 10 以前は、移動したことを画像の一覧（MediaStore）に知らせる。11 以上はファイルの移動で自動的に変わる
        if (moved.isNotEmpty() && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            activity.scanPathsRecursively(moved) { finish() }
        } else {
            finish()
        }
    }

    // ---------------------------------------------------------------- 巻の並べ替え

    /** 巻の並べ替え（ドラッグ）。保存すると目印ファイルに書く。 */
    @SuppressLint("ClickableViewAccessibility")
    fun showOrder(activity: Activity, groupPath: String, done: () -> Unit) {
        val names = AuthorGroup.volumes(groupPath, activity.config.shouldShowHidden).map { it.name }.toMutableList()
        if (names.size < 2) {
            activity.toast(R.string.mochipic_group_order_few)
            return
        }
        val textColor = activity.getProperTextColor()
        val list = RecyclerView(activity).apply { layoutManager = LinearLayoutManager(activity) }
        lateinit var helper: ItemTouchHelper

        class Holder(val row: LinearLayout, val title: MyTextView, val handle: MyTextView) : RecyclerView.ViewHolder(row)

        val adapter = object : RecyclerView.Adapter<Holder>() {
            override fun getItemCount() = names.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
                val pad = dp(activity, 16)
                val handle = MyTextView(activity).apply {
                    text = "≡"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    setTextColor(textColor)
                    setPadding(pad, 0, pad, 0)
                    contentDescription = activity.getString(R.string.mochipic_button_layout_drag)
                }
                val title = MyTextView(activity).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(textColor)
                }
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(activity, 52)
                    setPadding(pad, 0, 0, 0)
                    addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(handle, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                return Holder(row, title, handle)
            }

            override fun onBindViewHolder(holder: Holder, position: Int) {
                holder.title.text = names[position]
                holder.handle.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) helper.startDrag(holder)
                    false
                }
            }
        }
        helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(recyclerView: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val a = from.bindingAdapterPosition
                val b = to.bindingAdapterPosition
                if (a == RecyclerView.NO_POSITION || b == RecyclerView.NO_POSITION) return false
                names.add(b, names.removeAt(a))
                adapter.notifyItemMoved(a, b)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
        list.adapter = adapter
        helper.attachToRecyclerView(list)
        list.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                ensureBackgroundThread {
                    val ok = AuthorGroup.writeMarker(groupPath, names.toList())
                    activity.runOnUiThread {
                        if (!ok) activity.toast(R.string.mochipic_group_failed)
                        done()
                    }
                }
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply { activity.setupDialogStuff(list, this, R.string.mochipic_group_order) }
    }

    // ---------------------------------------------------------------- エクスプローラー

    /** エクスプローラーでフォルダを長押ししたときのメニュー。 */
    fun showExplorerMenu(activity: Activity, anchor: View, path: String, openAsAlbum: (String) -> Unit, done: () -> Unit) {
        if (!canUse()) return
        val isGroup = AuthorGroup.isGroup(path)
        if (!isGroup && FolderDelete.isProtected(activity, path)) return
        val popup = androidx.appcompat.widget.PopupMenu(
            androidx.appcompat.view.ContextThemeWrapper(activity, activity.getPopupMenuTheme()), anchor, Gravity.END,
        )
        if (isGroup) {
            popup.menu.add(0, 1, 0, R.string.mochipic_group_open)
            popup.menu.add(0, 2, 1, R.string.mochipic_group_order)
            popup.menu.add(0, 3, 2, R.string.mochipic_group_unset)
        } else {
            popup.menu.add(0, 4, 0, R.string.mochipic_group_set)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> openAsAlbum(path)
                2 -> showOrder(activity, path, done)
                3 -> unmarkGroup(activity, path, done)
                4 -> markAsGroup(activity, path, done)
            }
            true
        }
        popup.show()
    }

    private fun dp(activity: Activity, v: Int) = (v * activity.resources.displayMetrics.density).toInt()
}
