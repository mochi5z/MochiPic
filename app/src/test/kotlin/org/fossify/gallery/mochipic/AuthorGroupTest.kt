package org.fossify.gallery.mochipic

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthorGroupTest {

    @Test
    fun markerRoundTrip() {
        val text = AuthorGroup.markerText(listOf("1巻", "2巻", "番外編"))
        assertEquals("# MochiPic group\n1巻\n2巻\n番外編\n", text)
        assertEquals(listOf("1巻", "2巻", "番外編"), AuthorGroup.parseMarker(text))
    }

    @Test
    fun markerIgnoresCommentsBlankLinesBomAndDuplicates() {
        val text = "﻿# MochiPic group\r\n\r\n 2巻 \r\n# memo\r\n1巻\r\n2巻\r\n"
        assertEquals(listOf("2巻", "1巻"), AuthorGroup.parseMarker(text))
    }

    @Test
    fun volumesWithoutSavedOrderUseNaturalOrder() {
        val names = listOf("10巻", "2巻", "1巻", "番外編")
        assertEquals(listOf("1巻", "2巻", "10巻", "番外編"), AuthorGroup.orderVolumes(names, emptyList()))
    }

    @Test
    fun savedOrderFirstThenNewVolumesAtTheEnd() {
        val names = listOf("1巻", "2巻", "3巻", "10巻", "番外編")
        val saved = listOf("番外編", "2巻", "1巻", "消えた巻")
        assertEquals(listOf("番外編", "2巻", "1巻", "3巻", "10巻"), AuthorGroup.orderVolumes(names, saved))
    }

    @Test
    fun savedOrderIgnoresCase() {
        assertEquals(listOf("Vol2", "vol1"), AuthorGroup.orderVolumes(listOf("vol1", "Vol2"), listOf("vol2", "VOL1")))
    }

    @Test
    fun displayNamePrefixesVolumeName() {
        val g = "/storage/emulated/0/Pictures/作者A"
        assertEquals("1巻_001.jpg", AuthorGroup.displayName(g, "$g/1巻/001.jpg"))
        assertEquals("2巻_150.jpg", AuthorGroup.displayName("$g/", "$g/2巻/150.jpg"))
        assertEquals("cover.jpg", AuthorGroup.displayName(g, "$g/cover.jpg"))
        assertEquals("x.jpg", AuthorGroup.displayName(g, "/storage/emulated/0/Other/x.jpg"))
    }
}
