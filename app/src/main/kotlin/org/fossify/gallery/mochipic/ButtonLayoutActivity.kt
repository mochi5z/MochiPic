package org.fossify.gallery.mochipic

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.databinding.ActivityButtonLayoutBinding
import org.fossify.gallery.databinding.ItemButtonLayoutActionBinding
import org.fossify.gallery.databinding.ItemButtonLayoutHeaderBinding

/**
 * MochiPic: 「ボタンの配置」画面。
 * 置き場所ごとの見出しの下にボタンを並べ、つまみのドラッグで並び順と置き場所を変える。
 * 右端の置き場所の名前をタップすると、選んだ置き場所の末尾へ直接移す。変更はすぐ保存する。
 */
class ButtonLayoutActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityButtonLayoutBinding::inflate)
    private val screen = LayoutScreen.VIEWER
    private lateinit var layout: ActionLayout
    private val adapter = RowsAdapter()

    private sealed class Row {
        data class Header(val slot: Slot) : Row()
        data class Item(val action: ActionId) : Row()
    }

    private var rows = mutableListOf<Row>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.buttonLayoutToolbar.title = getString(R.string.mochipic_button_layout_viewer)
        binding.buttonLayoutHint.setTextColor(getProperTextColor())
        setupEdgeToEdge(
            padTopSystem = listOf(binding.buttonLayoutAppbar),
            padBottomSystem = listOf(binding.buttonLayoutList)
        )

        binding.buttonLayoutToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.button_layout_reset) {
                confirmReset()
                true
            } else {
                false
            }
        }

        layout = ActionLayoutStore.load(this, screen)
        rows = buildRows(layout)
        binding.buttonLayoutList.adapter = adapter
        touchHelper.attachToRecyclerView(binding.buttonLayoutList)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.buttonLayoutAppbar, NavigationIcon.Arrow)
    }

    // ---------------------------------------------------------------- 一覧 ⇔ 配置

    private fun buildRows(layout: ActionLayout): MutableList<Row> {
        val result = mutableListOf<Row>()
        for (slot in Slot.entries) {
            result += Row.Header(slot)
            layout.inSlot(slot).forEach { result += Row.Item(it) }
        }
        return result
    }

    /** 一覧の並び（見出しで区切られた順番）から配置を作り直して保存する。 */
    private fun saveFromRows() {
        val entries = mutableListOf<ActionEntry>()
        var slot = Slot.TOP
        var rejected = false
        for (row in rows) {
            when (row) {
                is Row.Header -> slot = row.slot
                is Row.Item -> {
                    val s = if (slot == Slot.BOTTOM && !row.action.canBeInBottom) {
                        rejected = true
                        Slot.MENU
                    } else {
                        slot
                    }
                    entries += ActionEntry(row.action, s)
                }
            }
        }
        // 置き場所ごとにまとめ直す（下部バーに置けないボタンを︙メニューへ移したときも並びが崩れない）
        layout = ActionLayout(Slot.entries.flatMap { s -> entries.filter { it.slot == s } })
        ActionLayoutStore.save(this, screen, layout)
        if (rejected) {
            toast(R.string.mochipic_button_layout_not_bottom)
            rows = buildRows(layout)
        }
    }

    private fun moveTo(action: ActionId, slot: Slot) {
        if (slot == Slot.BOTTOM && !action.canBeInBottom) {
            toast(R.string.mochipic_button_layout_not_bottom)
            return
        }
        layout = layout.move(action, slot, Int.MAX_VALUE)
        ActionLayoutStore.save(this, screen, layout)
        rows = buildRows(layout)
        adapter.notifyDataSetChanged()
    }

    // ダイアログは Fossify の部品を使う（テーマの色に合わせて表示される）
    private fun confirmReset() {
        ConfirmationDialog(this, messageId = R.string.mochipic_button_layout_reset_confirm) {
            ActionLayoutStore.reset(this, screen)
            layout = ActionLayoutStore.load(this, screen)
            rows = buildRows(layout)
            adapter.notifyDataSetChanged()
        }
    }

    private fun slotName(slot: Slot): String = getString(
        when (slot) {
            Slot.TOP -> R.string.mochipic_slot_top
            Slot.BOTTOM -> R.string.mochipic_slot_bottom
            Slot.MENU -> R.string.mochipic_slot_menu
            Slot.HIDDEN -> R.string.mochipic_slot_hidden
        }
    )

    private fun chooseSlot(action: ActionId) {
        val items = Slot.entries
            .filter { it != Slot.BOTTOM || action.canBeInBottom }
            .mapTo(ArrayList()) { RadioItem(it.ordinal, slotName(it), it) }
        RadioGroupDialog(this, items, layout.slotOf(action).ordinal, ViewerActions.titleRes(action)) {
            moveTo(action, it as Slot)
        }
    }

    // ---------------------------------------------------------------- ドラッグ

    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
            if (viewHolder is ItemHolder) super.getMovementFlags(recyclerView, viewHolder) else 0

        override fun isLongPressDragEnabled() = false

        override fun onMove(recyclerView: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            val a = from.bindingAdapterPosition
            val b = to.bindingAdapterPosition
            if (a == RecyclerView.NO_POSITION || b <= 0) return false   // 先頭の見出しより上には置けない
            val row = rows.removeAt(a)
            rows.add(b, row)
            adapter.notifyItemMoved(a, b)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            saveFromRows()
            adapter.notifyDataSetChanged()   // 右端の置き場所の名前を更新
        }
    })

    // ---------------------------------------------------------------- 一覧の表示

    private inner class HeaderHolder(val b: ItemButtonLayoutHeaderBinding) : RecyclerView.ViewHolder(b.root)
    private inner class ItemHolder(val b: ItemButtonLayoutActionBinding) : RecyclerView.ViewHolder(b.root)

    private inner class RowsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size

        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) {
                HeaderHolder(ItemButtonLayoutHeaderBinding.inflate(inflater, parent, false))
            } else {
                ItemHolder(ItemButtonLayoutActionBinding.inflate(inflater, parent, false))
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val textColor = getProperTextColor()
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).b.buttonLayoutHeader.apply {
                    text = slotName(row.slot)
                    setTextColor(getProperPrimaryColor())
                }

                is Row.Item -> (holder as ItemHolder).b.apply {
                    val slot = currentSlotAt(position)
                    buttonLayoutTitle.setText(ViewerActions.titleRes(row.action))
                    buttonLayoutTitle.setTextColor(textColor)
                    val icon = ViewerActions.iconRes(row.action)
                    if (icon != 0) {
                        buttonLayoutIcon.setImageResource(icon)
                        buttonLayoutIcon.applyColorFilter(textColor)
                        buttonLayoutIcon.visibility = View.VISIBLE
                    } else {
                        buttonLayoutIcon.visibility = View.INVISIBLE
                    }
                    buttonLayoutDrag.applyColorFilter(textColor)
                    buttonLayoutSlot.text = slotName(slot)
                    buttonLayoutSlot.setTextColor(getProperPrimaryColor())
                    buttonLayoutSlot.setOnClickListener { chooseSlot(row.action) }
                    buttonLayoutItem.setOnClickListener { chooseSlot(row.action) }
                    buttonLayoutDrag.setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                            touchHelper.startDrag(holder)
                        }
                        false
                    }
                }
            }
        }
    }

    /** 一覧の [position] 番目のボタンが属する置き場所（直前の見出し）。 */
    private fun currentSlotAt(position: Int): Slot {
        for (i in position downTo 0) {
            val row = rows[i]
            if (row is Row.Header) return row.slot
        }
        return Slot.TOP
    }
}
