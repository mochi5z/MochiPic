package org.fossify.gallery.mochipic

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerDetailsTest {
    @Test
    fun usesDateTakenWhenPresent() {
        assertEquals("4000 x 3000  ・  2026/10/15 9:00", ViewerDetails.summary("4000 x 3000", "2026/10/15 9:00", "2026/10/16 10:00"))
    }

    @Test
    fun fallsBackToLastModified() {
        assertEquals("640 x 480  ・  2026/10/16 10:00", ViewerDetails.summary("640 x 480", "", "2026/10/16 10:00"))
    }

    @Test
    fun skipsMissingParts() {
        assertEquals("2026/10/16 10:00", ViewerDetails.summary(null, "", "2026/10/16 10:00"))
        assertEquals("", ViewerDetails.summary(null, "", ""))
    }
}
