package org.fossify.gallery.mochipic

import android.content.Context
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.helpers.AlphanumericComparator
import org.fossify.commons.helpers.FAVORITES
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * MochiPic: 作者ごとのグループ（2026-11-11、v0.1.11）。
 *
 * スマホ内の構成は「作者フォルダ／巻フォルダ／画像」。作者フォルダに目印ファイル [MARKER] を置くとグループになる。
 *  - アルバム一覧: 巻フォルダを出さず、作者フォルダを 1 項目として出す（枚数は全巻の合計）[collapse]
 *  - グループを開く: 全巻の画像を 1 つのアルバムのように並べる。巻の順（目印ファイルに書いた順）→ 巻の中はファイル名の数字順 [loadMedia]
 *  - 表示名: 「巻フォルダ名_ファイル名」（実際のファイル名は変えない）[displayName]
 *  - 作者フォルダの直下にある画像は、巻名を付けずに先頭へ並べる
 *  - 1 段だけ（巻フォルダの中のフォルダは読まない）
 *
 * 目印ファイル（UTF-8 のテキスト）: 1 行目は [HEADER]、2 行目から巻フォルダの名前を並び順に 1 行ずつ。
 * 目印ファイルに無い巻（後から入れた巻）は最後に、名前の数字順で付く。
 * アプリを入れ直しても、PC へ持っていっても指定と順番が残る。読み書きには「すべてのファイルへのアクセス」が要る。
 */
object AuthorGroup {
    const val MARKER = ".mochigroup"
    const val HEADER = "# MochiPic group"
    const val EXTRA_GROUP = "mochipic_group"

    private class Info(val order: List<String>, val stamp: Long, val checkedAt: Long = System.currentTimeMillis())

    /** 目印ファイルの中身（場所の小文字 → 並び順）。目印ファイルの更新日時が変わったら読み直す。 */
    private val infos = ConcurrentHashMap<String, Info>()

    /** グループではないと分かったフォルダ（場所の小文字 → 確かめた時刻）。[NEGATIVE_TTL_MS] の間は確かめ直さない。 */
    private val notGroups = ConcurrentHashMap<String, Long>()
    private const val NEGATIVE_TTL_MS = 60_000L
    private const val POSITIVE_TTL_MS = 5_000L

    /** 一覧に出したグループの表紙（巻ごとの表紙、巻の順）。モザイク表示で使う。 */
    private val covers = ConcurrentHashMap<String, List<String>>()

    /** 一覧に出したグループ（場所の小文字 → 場所）。「グループへ移動」の候補。 */
    private val shownGroups = ConcurrentHashMap<String, String>()

    private val alphanumeric = AlphanumericComparator()
    val naturalOrder = Comparator<String> { a, b -> alphanumeric.compare(a.lowercase(Locale.ROOT), b.lowercase(Locale.ROOT)) }

    fun key(path: String) = path.trimEnd('/').lowercase(Locale.ROOT)

    /** 確かめた結果を忘れる（引っ張って更新したとき・自分でグループを変えたとき）。 */
    fun clearCache() {
        notGroups.clear()
        infos.clear()
        covers.clear()
    }

    // ---------------------------------------------------------------- 判定・目印ファイル

    fun isGroup(path: String): Boolean {
        if (path.isEmpty() || path == FAVORITES || path == RECYCLE_BIN) return false
        val k = key(path)
        val now = System.currentTimeMillis()
        notGroups[k]?.let { if (now - it < NEGATIVE_TTL_MS) return false }
        infos[k]?.let { if (now - it.checkedAt < POSITIVE_TTL_MS) return true }   // 画面の更新のたびにファイルを調べない
        val marker = File(path, MARKER)
        val stamp = marker.lastModified()     // 無ければ 0
        if (stamp == 0L && !marker.exists()) {
            infos.remove(k)
            notGroups[k] = System.currentTimeMillis()
            return false
        }
        notGroups.remove(k)
        val old = infos[k]
        infos[k] = if (old?.stamp == stamp) Info(old.order, stamp) else Info(parseMarker(readText(marker)), stamp)
        return true
    }

    private fun readText(file: File): String = try {
        file.readText(Charsets.UTF_8)
    } catch (e: Exception) {
        ""
    }

    /** 目印ファイルの中身から、巻の名前の並び順を取り出す（# で始まる行と空行は飛ばす）。 */
    fun parseMarker(text: String): List<String> =
        text.lineSequence().map { it.trim().removePrefix("﻿") }.filter { it.isNotEmpty() && !it.startsWith("#") }.distinct().toList()

    fun markerText(order: List<String>): String = (listOf(HEADER) + order).joinToString("\n", postfix = "\n")

    /** 目印ファイルを書く（グループに指定・並び順の保存）。成功すれば true。 */
    fun writeMarker(groupPath: String, order: List<String>): Boolean = try {
        File(groupPath, MARKER).writeText(markerText(order), Charsets.UTF_8)
        notGroups.remove(key(groupPath))
        infos.remove(key(groupPath))
        true
    } catch (e: Exception) {
        false
    }

    /** グループを解除する（目印ファイルだけを消す。フォルダと画像はそのまま）。 */
    fun removeMarker(groupPath: String): Boolean {
        val f = File(groupPath, MARKER)
        val ok = !f.exists() || f.delete()
        infos.remove(key(groupPath))
        covers.remove(key(groupPath))
        notGroups.remove(key(groupPath))
        return ok
    }

    /** 保存してある並び順（グループでなければ空）。 */
    fun savedOrder(groupPath: String): List<String> = if (isGroup(groupPath)) infos[key(groupPath)]?.order.orEmpty() else emptyList()

    // ---------------------------------------------------------------- 巻の並び

    /**
     * 巻の並び順を決める。保存した順にあるものはその順、無いもの（後から入れた巻）は最後に名前の数字順。
     * 保存した順にあっても、今は無い巻は飛ばす。名前の大文字小文字は区別しない。
     */
    fun orderVolumes(names: List<String>, saved: List<String>): List<String> {
        val byKey = names.associateBy { it.lowercase(Locale.ROOT) }
        val result = ArrayList<String>()
        val used = HashSet<String>()
        for (s in saved) {
            val k = s.lowercase(Locale.ROOT)
            val n = byKey[k] ?: continue
            if (used.add(k)) result.add(n)
        }
        names.filter { it.lowercase(Locale.ROOT) !in used }.sortedWith(naturalOrder).forEach { result.add(it) }
        return result
    }

    /** グループの巻フォルダ（並び順）。隠しフォルダは隠しフォルダを表示するときだけ。 */
    fun volumes(groupPath: String, showHidden: Boolean): List<File> {
        val dirs = File(groupPath).listFiles()?.filter { it.isDirectory && (showHidden || !it.name.startsWith(".")) }.orEmpty()
        val byName = dirs.associateBy { it.name }
        return orderVolumes(dirs.map { it.name }, savedOrder(groupPath)).mapNotNull { byName[it] }
    }

    // ---------------------------------------------------------------- 表示名

    /** グループの中で見せる名前。巻の中の画像は「巻フォルダ名_ファイル名」、作者フォルダ直下の画像はファイル名のまま。 */
    fun displayName(groupPath: String, mediumPath: String): String {
        val name = mediumPath.substringAfterLast('/')
        val parent = mediumPath.substringBeforeLast('/', "")
        if (key(parent) == key(groupPath)) return name
        val volume = parent.substringAfterLast('/')
        return if (key(parent.substringBeforeLast('/', "")) == key(groupPath)) "${volume}_$name" else name
    }

    // ---------------------------------------------------------------- アルバム一覧

    /**
     * アルバム一覧で、グループの巻フォルダ（とグループ直下の画像のフォルダ）をグループ 1 項目にまとめる。
     * 何度当てても同じ結果になる（まとめた項目をもう一度渡してもそのまま）。
     */
    fun collapse(dirs: List<Directory>): ArrayList<Directory> {
        val groupOf = java.util.IdentityHashMap<Directory, String>()   // 同じ内容の項目でも別々に扱う
        for (dir in dirs) {
            if (dir.path == FAVORITES || dir.path == RECYCLE_BIN) continue
            val parent = File(dir.path).parent ?: continue
            when {
                // 自分がグループならそちらを先に（グループの中のグループでも、まとめ方が毎回同じになるように）
                isGroup(dir.path) -> groupOf[dir] = dir.path
                isGroup(parent) -> groupOf[dir] = parent
            }
        }
        if (groupOf.isEmpty()) return ArrayList(dirs)

        val members = LinkedHashMap<String, ArrayList<Directory>>()
        groupOf.forEach { (dir, group) -> members.getOrPut(key(group)) { ArrayList() }.add(dir) }
        val result = ArrayList<Directory>(dirs.size)
        val added = HashSet<String>()
        for (dir in dirs) {
            val group = groupOf[dir]
            if (group == null) {
                result.add(dir)
                continue
            }
            val k = key(group)
            if (!added.add(k)) continue
            shownGroups[k] = group
            val list = members[k].orEmpty()
            if (list.size == 1 && key(list[0].path) == k) {      // まとめ済み（または直下の画像だけ）
                result.add(list[0].apply { name = File(group).name })
                continue
            }
            result.add(merged(group, list))
        }
        return result
    }

    private fun merged(group: String, list: List<Directory>): Directory {
        val order = orderVolumes(list.map { File(it.path).name }, savedOrder(group))
        val direct = list.firstOrNull { key(it.path) == key(group) }
        val volumes = list.filter { key(it.path) != key(group) }.sortedBy { order.indexOf(File(it.path).name) }
        val ordered = listOfNotNull(direct) + volumes
        covers[key(group)] = ordered.map { it.tmb }.filter { it.isNotEmpty() }
        val first = ordered.first()
        return Directory(
            null, group, first.tmb, File(group).name,
            list.sumOf { it.mediaCnt }, list.maxOf { it.modified }, list.maxOf { it.taken }, list.sumOf { it.size },
            first.location, list.fold(0) { acc, d -> acc or d.types }, "",
        )
    }

    /** モザイク表示用: グループの各巻の表紙（巻の順）。グループでなければ null。 */
    fun coversOf(path: String): List<String>? = covers[key(path)]?.takeIf { isGroup(path) }

    // ---------------------------------------------------------------- グループを開く

    /** グループの全画像（巻の順 → 巻の中はファイル名の数字順。作者フォルダ直下の画像が先頭）。 */
    fun loadMedia(context: Context, groupPath: String, isPickImage: Boolean, isPickVideo: Boolean, withDurations: Boolean): ArrayList<Medium> {
        val config = context.config
        val excluded = config.excludedFolders.map { key(it) }.toHashSet()
        val fetcher = MediaFetcher(context)
        val folders = listOf(File(groupPath)) + volumes(groupPath, config.shouldShowHidden)
        val result = ArrayList<Medium>()
        val showHidden = config.shouldShowHidden
        for (folder in folders) {
            if (key(folder.path) in excluded) continue
            if (!showHidden && File(folder, ".nomedia").exists()) continue   // 非表示（.nomedia）の巻は出さない
            val media = loadFolder(context, fetcher, folder.path, isPickImage, isPickVideo, withDurations)
            media.sortWith { a, b -> naturalOrder.compare(a.name, b.name) }
            result.addAll(media)
        }
        return result
    }

    private fun loadFolder(
        context: Context, fetcher: MediaFetcher, path: String, isPickImage: Boolean, isPickVideo: Boolean, withDurations: Boolean,
    ): ArrayList<Medium> {
        // 高速方式で読めればそれを使う。読めない（Android の一覧に載らない）フォルダは中身を直接見る
        val quick = if (context.mochiPrefs.loadMode == MochiPicPrefs.LOAD_MEDIASTORE) {
            QuickAlbumLoader.loadFolder(context, path, isPickImage, isPickVideo, withDurations)
        } else {
            null
        }
        if (quick != null) return quick
        val hasMedia = File(path).list()?.any { it.isMediaFile() } == true
        if (!hasMedia) return ArrayList()
        return fetcher.getFilesFrom(
            path, isPickImage, isPickVideo, false, false, false, context.getFavoritePaths(), withDurations, HashMap(), HashMap(), null,
        )
    }

    /** 一覧に出したことのあるグループ（「グループへ移動」の候補）。 */
    fun knownGroups(): List<String> = shownGroups.values.filter { isGroup(it) }.sortedWith(naturalOrder)
}
