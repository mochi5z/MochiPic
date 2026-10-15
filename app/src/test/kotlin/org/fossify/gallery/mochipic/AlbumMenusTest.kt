package org.fossify.gallery.mochipic

import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_NAME
import org.fossify.commons.helpers.SORT_BY_PATH
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.SORT_USE_NUMERIC_VALUE
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumMenusTest {
    @Test
    fun choosingSameKindTogglesDirection() {
        val asc = SORT_BY_DATE_MODIFIED
        assertEquals(SORT_BY_DATE_MODIFIED or SORT_DESCENDING, AlbumMenus.nextSorting(asc, SORT_BY_DATE_MODIFIED))
        assertEquals(SORT_BY_DATE_MODIFIED, AlbumMenus.nextSorting(asc or SORT_DESCENDING, SORT_BY_DATE_MODIFIED))
    }

    @Test
    fun choosingOtherKindKeepsDirectionAndOptions() {
        val current = SORT_BY_NAME or SORT_DESCENDING or SORT_USE_NUMERIC_VALUE
        assertEquals(SORT_BY_PATH or SORT_DESCENDING or SORT_USE_NUMERIC_VALUE, AlbumMenus.nextSorting(current, SORT_BY_PATH))
    }

    @Test
    fun randomOrCustomCountsAsAnotherKind() {
        val random = org.fossify.commons.helpers.SORT_BY_RANDOM
        assertEquals(SORT_BY_NAME, AlbumMenus.nextSorting(random, SORT_BY_NAME))
    }
}
