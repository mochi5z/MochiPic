package org.fossify.gallery.mochipic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderDeleteTest {
    private val roots = listOf("/storage/emulated/0", "/storage/1234-ABCD")

    @Test
    fun storageRootsAreProtected() {
        assertTrue(FolderDelete.isProtectedPath(roots, "/storage/emulated/0"))
        assertTrue(FolderDelete.isProtectedPath(roots, "/storage/emulated/0/"))
        assertTrue(FolderDelete.isProtectedPath(roots, "/storage/1234-ABCD"))
        assertTrue(FolderDelete.isProtectedPath(roots, "/storage/emulated"))
        assertTrue(FolderDelete.isProtectedPath(roots, "/"))
    }

    @Test
    fun standardTopFoldersAreProtected() {
        assertTrue(FolderDelete.isProtectedPath(roots, "/storage/emulated/0/Download"))
        assertTrue(FolderDelete.isProtectedPath(roots, "/storage/emulated/0/DCIM"))
        assertTrue(FolderDelete.isProtectedPath(roots, "/storage/emulated/0/pictures"))
        assertTrue(FolderDelete.isProtectedPath(roots, "/storage/1234-ABCD/Movies"))
    }

    @Test
    fun normalFoldersCanBeDeletedWhole() {
        assertFalse(FolderDelete.isProtectedPath(roots, "/storage/emulated/0/Download/[2305443] JOKERKIN"))
        assertFalse(FolderDelete.isProtectedPath(roots, "/storage/emulated/0/DCIM/Camera"))
        assertFalse(FolderDelete.isProtectedPath(roots, "/storage/emulated/0/MyAlbum"))
        assertFalse(FolderDelete.isProtectedPath(roots, "/storage/1234-ABCD/MyAlbum"))
    }
}
