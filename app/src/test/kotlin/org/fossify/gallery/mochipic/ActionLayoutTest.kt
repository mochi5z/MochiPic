package org.fossify.gallery.mochipic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionLayoutTest {

    private val defaults = ViewerActions.DEFAULT_LAYOUT

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
}
