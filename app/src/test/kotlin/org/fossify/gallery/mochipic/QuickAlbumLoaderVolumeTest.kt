package org.fossify.gallery.mochipic

import org.junit.Assert.assertEquals
import org.junit.Test

/** v0.1.2: 差分読み込みで、フォルダの場所からボリューム（内部ストレージ・SD カード）を見分ける。 */
class QuickAlbumLoaderVolumeTest {

    @Test
    fun internalStorageIsPrimary() {
        assertEquals("external_primary", QuickAlbumLoader.volumeOf("/storage/emulated/0/Pictures/A"))
    }

    @Test
    fun sdCardIsLowercaseId() {
        assertEquals("abcd-1234", QuickAlbumLoader.volumeOf("/storage/ABCD-1234/DCIM/C"))
    }

    @Test
    fun unknownPlaceIsEmpty() {
        assertEquals("", QuickAlbumLoader.volumeOf("/mnt/usb/photos"))
    }
}
