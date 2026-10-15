package org.fossify.gallery.mochipic

/**
 * MochiPic: ボタンの「置き場所」と「並び順」のデータ。
 *
 * ボタンそのもの（アイコン・名前・処理）は画面ごとの一覧（例: [ViewerActions]）で定義し、
 * ここではどこに何番目に置くかだけを持つ。Android に依存しないので単体テストできる。
 *
 * 保存形式（1 画面 1 行）: "EDIT:T,DELETE:T,SHARE:T,PROPERTIES:B,SLIDESHOW:M,..."
 *   T = 上部バー / B = 下部バー / M = ︙メニュー / H = 非表示
 */
enum class Slot(val code: Char) {
    TOP('T'), BOTTOM('B'), MENU('M'), HIDDEN('H');

    companion object {
        fun fromCode(c: Char): Slot? = entries.firstOrNull { it.code == c }
    }
}

/** 画面に置けるボタンの種類。[canBeInBottom] が false のものは下部バーに置けない（下部バー用の部品が無い）。 */
enum class ActionId(val canBeInBottom: Boolean = true) {
    EDIT, DELETE, SHARE, PROPERTIES, SLIDESHOW, ROTATE, RENAME, COPY, MOVE, SET_AS,
    FAVORITE, TOGGLE_VISIBILITY, RESIZE, CHANGE_ORIENTATION, SHOW_ON_MAP,
    OPEN_WITH(false), COPY_TO_CLIPBOARD(false), CREATE_SHORTCUT(false), PRINT(false), SETTINGS(false);

    companion object {
        fun fromName(name: String): ActionId? = entries.firstOrNull { it.name == name }
    }
}

data class ActionEntry(val action: ActionId, val slot: Slot)

/** 1 画面分の配置。[entries] の順番がそのまま表示順。 */
data class ActionLayout(val entries: List<ActionEntry>) {

    fun slotOf(action: ActionId): Slot = entries.firstOrNull { it.action == action }?.slot ?: Slot.HIDDEN

    fun inSlot(slot: Slot): List<ActionId> = entries.filter { it.slot == slot }.map { it.action }

    fun hasAny(slot: Slot): Boolean = entries.any { it.slot == slot }

    /** 下部バーを使わない設定のとき: 下部バーのボタンは︙メニューに回す。 */
    fun withoutBottomBar(): ActionLayout =
        ActionLayout(entries.map { if (it.slot == Slot.BOTTOM) it.copy(slot = Slot.MENU) else it })

    /** [action] を [slot] の中の [index] 番目（その置き場所の中での位置）に移す。 */
    fun move(action: ActionId, slot: Slot, index: Int): ActionLayout {
        val target = if (slot == Slot.BOTTOM && !action.canBeInBottom) Slot.MENU else slot
        val rest = entries.filter { it.action != action }
        val sameSlot = rest.withIndex().filter { it.value.slot == target }
        val insertAt = when {
            sameSlot.isEmpty() -> insertPositionForEmptySlot(rest, target)
            index <= 0 -> sameSlot.first().index
            index >= sameSlot.size -> sameSlot.last().index + 1
            else -> sameSlot[index].index
        }
        val result = rest.toMutableList()
        result.add(insertAt, ActionEntry(action, target))
        return ActionLayout(result)
    }

    fun serialize(): String = entries.joinToString(",") { "${it.action.name}:${it.slot.code}" }

    companion object {
        private val SLOT_ORDER = listOf(Slot.TOP, Slot.BOTTOM, Slot.MENU, Slot.HIDDEN)

        /** 空の置き場所に入れるときは、置き場所の並び（上部→下部→メニュー→非表示）が崩れない位置に入れる。 */
        private fun insertPositionForEmptySlot(rest: List<ActionEntry>, target: Slot): Int {
            val after = SLOT_ORDER.drop(SLOT_ORDER.indexOf(target) + 1).toSet()
            val firstAfter = rest.indexOfFirst { it.slot in after }
            return if (firstAfter >= 0) firstAfter else rest.size
        }

        /**
         * 保存された文字列を読む。壊れた部分・知らないボタンは無視し、
         * 保存に無いボタン（アプリの更新で増えたもの）は [defaults] の置き場所で後ろに足す。
         */
        fun parse(text: String?, defaults: ActionLayout): ActionLayout {
            if (text.isNullOrBlank()) return defaults
            val seen = mutableSetOf<ActionId>()
            val parsed = mutableListOf<ActionEntry>()
            for (part in text.split(',')) {
                val name = part.substringBefore(':').trim()
                val code = part.substringAfter(':', "").trim()
                val action = ActionId.fromName(name) ?: continue
                if (code.length != 1 || action in seen) continue
                if (action !in defaults.entries.map { it.action }) continue
                var slot = Slot.fromCode(code[0]) ?: continue
                if (slot == Slot.BOTTOM && !action.canBeInBottom) slot = Slot.MENU
                seen += action
                parsed += ActionEntry(action, slot)
            }
            var layout = ActionLayout(parsed)
            for (d in defaults.entries) {
                if (d.action !in seen) {
                    layout = layout.move(d.action, d.slot, Int.MAX_VALUE)
                }
            }
            return layout
        }
    }
}
