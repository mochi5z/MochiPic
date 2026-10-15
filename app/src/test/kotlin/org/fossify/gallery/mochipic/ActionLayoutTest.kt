package org.fossify.gallery.mochipic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLayoutTest {

    /** テスト用の配置（初期配置が変わってもテストが変わらないように固定）。 */
    private val defaults = ActionLayout(
        listOf(
            ActionEntry(ActionId.EDIT, Slot.TOP),
            ActionEntry(ActionId.DELETE, Slot.TOP),
            ActionEntry(ActionId.SHARE, Slot.TOP),
            ActionEntry(ActionId.PROPERTIES, Slot.BOTTOM),
            ActionEntry(ActionId.ZOOM_OUT, Slot.BOTTOM),
            ActionEntry(ActionId.ZOOM_IN, Slot.BOTTOM),
            ActionEntry(ActionId.SLIDESHOW, Slot.MENU),
            ActionEntry(ActionId.ROTATE, Slot.MENU),
            ActionEntry(ActionId.RENAME, Slot.MENU),
            ActionEntry(ActionId.COPY, Slot.MENU),
            ActionEntry(ActionId.MOVE, Slot.MENU),
            ActionEntry(ActionId.SET_AS, Slot.MENU),
            ActionEntry(ActionId.FAVORITE, Slot.MENU),
            ActionEntry(ActionId.TOGGLE_VISIBILITY, Slot.MENU),
            ActionEntry(ActionId.RESIZE, Slot.MENU),
            ActionEntry(ActionId.CHANGE_ORIENTATION, Slot.MENU),
            ActionEntry(ActionId.SHOW_ON_MAP, Slot.MENU),
            ActionEntry(ActionId.OPEN_WITH, Slot.MENU),
            ActionEntry(ActionId.COPY_TO_CLIPBOARD, Slot.MENU),
            ActionEntry(ActionId.CREATE_SHORTCUT, Slot.MENU),
            ActionEntry(ActionId.PRINT, Slot.MENU),
            ActionEntry(ActionId.SETTINGS, Slot.MENU),
        )
    )

    @Test
    fun defaultLayoutMatchesQpGallery() {
        assertEquals(listOf(ActionId.EDIT, ActionId.DELETE, ActionId.SHARE), defaults.inSlot(Slot.TOP))
        assertEquals(listOf(ActionId.PROPERTIES, ActionId.ZOOM_OUT, ActionId.ZOOM_IN), defaults.inSlot(Slot.BOTTOM))
        assertEquals(Slot.MENU, defaults.slotOf(ActionId.SLIDESHOW))
        // すべてのボタンがちょうど 1 回ずつ入っている
        assertEquals(ActionId.entries.toSet(), defaults.entries.map { it.action }.toSet())
        assertEquals(ActionId.entries.size, defaults.entries.size)
    }

    @Test
    fun serializeAndParseRoundTrip() {
        val text = defaults.serialize()
        assertEquals(defaults, ActionLayout.parse(text, defaults))
    }

    @Test
    fun emptyOrNullTextGivesDefaults() {
        assertEquals(defaults, ActionLayout.parse(null, defaults))
        assertEquals(defaults, ActionLayout.parse("  ", defaults))
    }

    @Test
    fun brokenPartsAreIgnoredAndMissingActionsAreAdded() {
        val layout = ActionLayout.parse("SLIDESHOW:T,UNKNOWN:T,DELETE:X,EDIT,SHARE:B,SHARE:T,DELETE:B", defaults)
        // EDIT は壊れていたので初期配置（上部バー）で後ろに足される
        assertEquals(listOf(ActionId.SLIDESHOW, ActionId.EDIT), layout.inSlot(Slot.TOP))
        assertEquals(Slot.BOTTOM, layout.slotOf(ActionId.SHARE))   // 2 回目の SHARE は無視
        assertEquals(Slot.BOTTOM, layout.slotOf(ActionId.DELETE))  // DELETE:X は無視、DELETE:B を採用
        assertEquals(ActionId.entries.size, layout.entries.size)
    }

    @Test
    fun actionsThatCannotBeInBottomGoToMenu() {
        val parsed = ActionLayout.parse("SETTINGS:B", defaults)
        assertEquals(Slot.MENU, parsed.slotOf(ActionId.SETTINGS))
        val moved = defaults.move(ActionId.PRINT, Slot.BOTTOM, 0)
        assertEquals(Slot.MENU, moved.slotOf(ActionId.PRINT))
    }

    @Test
    fun moveDeleteToBottomAndSlideshowToTop() {
        val layout = defaults
            .move(ActionId.DELETE, Slot.BOTTOM, 0)
            .move(ActionId.SLIDESHOW, Slot.TOP, 0)
        assertEquals(listOf(ActionId.SLIDESHOW, ActionId.EDIT, ActionId.SHARE), layout.inSlot(Slot.TOP))
        assertEquals(listOf(ActionId.DELETE, ActionId.PROPERTIES, ActionId.ZOOM_OUT, ActionId.ZOOM_IN), layout.inSlot(Slot.BOTTOM))
        assertEquals(ActionId.entries.size, layout.entries.size)
    }

    @Test
    fun moveWithinSameSlotChangesOrder() {
        val layout = defaults.move(ActionId.SHARE, Slot.TOP, 0)
        assertEquals(listOf(ActionId.SHARE, ActionId.EDIT, ActionId.DELETE), layout.inSlot(Slot.TOP))
        val last = defaults.move(ActionId.EDIT, Slot.TOP, 99)
        assertEquals(listOf(ActionId.DELETE, ActionId.SHARE, ActionId.EDIT), last.inSlot(Slot.TOP))
    }

    @Test
    fun moveIntoEmptySlotKeepsSlotGrouping() {
        val layout = defaults.move(ActionId.RENAME, Slot.HIDDEN, 0)
        assertEquals(listOf(ActionId.RENAME), layout.inSlot(Slot.HIDDEN))
        val noTop = ActionLayout(defaults.entries.filter { it.slot != Slot.TOP })
            .move(ActionId.EDIT, Slot.TOP, 0)
        assertEquals(ActionId.EDIT, noTop.entries.first().action)
    }

    @Test
    fun withoutBottomBarMovesBottomToMenu() {
        val layout = defaults.withoutBottomBar()
        assertFalse(layout.hasAny(Slot.BOTTOM))
        assertEquals(Slot.MENU, layout.slotOf(ActionId.PROPERTIES))
        assertTrue(defaults.hasAny(Slot.BOTTOM))
    }

    @Test
    fun qpDefaultLayout() {
        val d = ViewerActions.DEFAULT_LAYOUT
        assertEquals(listOf(ActionId.DELETE, ActionId.MOVE, ActionId.COPY, ActionId.RENAME), d.inSlot(Slot.TOP))
        assertEquals(listOf(ActionId.PROPERTIES), d.inSlot(Slot.BOTTOM))
        assertEquals(
            listOf(ActionId.SLIDESHOW, ActionId.SET_AS, ActionId.ROTATE, ActionId.SHOW_ON_MAP, ActionId.EDIT, ActionId.SHARE, ActionId.ZOOM_IN, ActionId.ZOOM_OUT),
            d.inSlot(Slot.MENU),
        )
        assertEquals(ActionId.entries.toSet(), d.entries.map { it.action }.toSet())
    }
}
