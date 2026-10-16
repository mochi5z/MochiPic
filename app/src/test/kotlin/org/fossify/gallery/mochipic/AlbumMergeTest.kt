package org.fossify.gallery.mochipic

import org.junit.Assert.assertEquals
import org.junit.Test

/** v0.1.1: 一覧への反映（マージ）の動き。テスト用に「場所 + 枚数」だけのフォルダで確かめる。 */
class AlbumMergeTest {

    private data class F(val path: String, val count: Int)

    private fun merge(cached: List<F>, rebuilt: Map<String, F?>) = AlbumMerge.merge(cached, rebuilt) { it.path }

    private val cached = listOf(
        F("/storage/emulated/0/Pictures/A", 3),
        F("/storage/emulated/0/Pictures/B", 5),
        F("/storage/ABCD-1234/DCIM/C", 2),
    )

    @Test
    fun nothingChangedKeepsList() {
        assertEquals(cached, merge(cached, emptyMap()))
    }

    @Test
    fun addedFolderGoesToEnd() {
        val d = F("/storage/emulated/0/Download/D", 10)
        assertEquals(cached + d, merge(cached, mapOf(d.path to d)))
    }

    @Test
    fun updatedFolderReplacedInPlace() {
        val b = F("/storage/emulated/0/Pictures/B", 6)
        assertEquals(listOf(cached[0], b, cached[2]), merge(cached, mapOf(b.path to b)))
    }

    @Test
    fun emptiedFolderRemoved() {
        assertEquals(listOf(cached[0], cached[2]), merge(cached, mapOf("/storage/emulated/0/Pictures/B" to null)))
    }

    @Test
    fun moveUpdatesSourceAndTarget() {
        val a = F("/storage/emulated/0/Pictures/A", 2)
        val d = F("/storage/emulated/0/Pictures/New", 1)
        assertEquals(listOf(a, cached[1], cached[2], d), merge(cached, mapOf(a.path to a, d.path to d)))
    }

    @Test
    fun sameUpdateTwiceGivesSameResult() {
        val b = F("/storage/emulated/0/Pictures/B", 7)
        val d = F("/storage/emulated/0/Download/D", 1)
        val update = mapOf(b.path to b, d.path to d, "/storage/ABCD-1234/DCIM/C" to null)
        val once = merge(cached, update)
        assertEquals(once, merge(once, update))
    }

    @Test
    fun pathCaseAndTrailingSlashIgnored() {
        val b = F("/storage/emulated/0/pictures/b", 9)
        val result = merge(cached, mapOf("/storage/emulated/0/Pictures/B/" to b))
        assertEquals(listOf(cached[0], b, cached[2]), result)
    }

    @Test
    fun foldersNotInUpdateAreKept() {
        // Android の一覧に載らない隠しフォルダなどは、更新に出てこなくても消えない
        val hidden = F("/storage/emulated/0/.private", 4)
        val list = cached + hidden
        val d = F("/storage/emulated/0/Download/D", 1)
        assertEquals(list + d, merge(list, mapOf(d.path to d)))
    }

    @Test
    fun duplicateCachedEntriesCollapsed() {
        val dup = cached + F("/storage/emulated/0/Pictures/a", 3)
        assertEquals(cached, merge(dup, emptyMap()))
    }

    @Test
    fun sdCardFolderRemovedWhenEmptied() {
        val result = merge(cached, mapOf("/storage/ABCD-1234/DCIM/C" to null))
        assertEquals(listOf(cached[0], cached[1]), result)
    }
}
