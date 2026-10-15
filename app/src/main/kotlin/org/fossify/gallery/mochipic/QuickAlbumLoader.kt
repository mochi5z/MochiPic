package org.fossify.gallery.mochipic

import android.content.Context
import android.provider.MediaStore
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.createDirectoryFromMedia
import org.fossify.gallery.extensions.getFavoritePaths
import org.fossify.gallery.extensions.getNoMediaFoldersSync
import org.fossify.gallery.extensions.shouldFolderBeVisible
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.models.Directory
import org.fossify.gallery.models.Medium
import java.io.File
import java.util.Locale

/**
 * MochiPic: アルバム一覧を Android の画像の一覧（MediaStore）を 1 回読むだけで作る（QP-Gallery と同じ考え方）。
 * フォルダを 1 つずつ開いて調べないので、zip を解凍したばかりのフォルダもすぐに出る。
 * フォルダの中身（日付など）は、そのフォルダを開いたときに読む。
 *
 * Android の一覧に載らないフォルダ（中身を直接見てしか分からない隠しフォルダなど）は、前回までの一覧に残っていれば残す。
 */
object QuickAlbumLoader {

    class Result(
        val dirs: ArrayList<Directory>,
        /** 新しく出てきたフォルダ・枚数が変わったフォルダの中身（アプリのデータベースに入れておく）。 */
        val changedMedia: Map<String, ArrayList<Medium>>,
    )

    fun load(context: Context, cached: List<Directory>, isPickImage: Boolean, isPickVideo: Boolean): Result? {
        val config = context.config
        val filterMedia = config.filterMedia
        if (filterMedia == 0) return Result(ArrayList(), emptyMap())
        val fetcher = MediaFetcher(context)
        val showHidden = config.shouldShowHidden
        val favoriteSet = context.getFavoritePaths().toHashSet()

        // 1. Android の画像の一覧を 1 回だけ読んで、フォルダごとに分ける
        val byFolder = LinkedHashMap<String, ArrayList<Medium>>()
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.SIZE,
        )
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"), projection,
                fetcher.getSelectionQuery(filterMedia), fetcher.getSelectionArgsQuery(filterMedia).toTypedArray(), null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val path = cursor.getString(0) ?: continue
                    val slash = path.lastIndexOf('/')
                    if (slash <= 0) continue
                    val filename = path.substring(slash + 1)
                    if (!showHidden && filename.startsWith('.')) continue
                    val type = typeOf(path)
                    if (type == TYPE_VIDEOS && (isPickImage || filterMedia and TYPE_VIDEOS == 0)) continue
                    if (type == TYPE_IMAGES && (isPickVideo || filterMedia and TYPE_IMAGES == 0)) continue
                    if (type == TYPE_GIFS && filterMedia and TYPE_GIFS == 0) continue
                    if (type == TYPE_RAWS && filterMedia and TYPE_RAWS == 0) continue
                    if (type == TYPE_SVGS && filterMedia and TYPE_SVGS == 0) continue
                    val modified = cursor.getLong(1) * 1000
                    val taken = cursor.getLong(2).takeIf { it != 0L } ?: modified
                    val parent = path.substring(0, slash)
                    byFolder.getOrPut(parent) { ArrayList() }.add(
                        Medium(null, filename, path, parent, modified, taken, cursor.getLong(3), type, 0, favoriteSet.contains(path), 0L, 0L)
                    )
                }
            }
        } catch (e: Exception) {
            return null  // 読めなければ従来どおりの読み込みに任せる
        }

        // 2. 表示するフォルダだけに絞る（除外・隠しフォルダ・探索フォルダーの設定）
        val excluded = if (config.temporarilyShowExcluded) HashSet() else config.excludedFolders
        val included = config.includedFolders
        val noMediaFolders = context.getNoMediaFoldersSync()
        val noMediaStatuses = HashMap<String, Boolean>()
        noMediaFolders.forEach { noMediaStatuses["$it/$NOMEDIA"] = true }
        val albumCovers = config.parseAlbumCovers()
        val hiddenString = context.getString(R.string.hidden)
        val cachedByPath = cached.associateBy { it.path.lowercase(Locale.ROOT) }

        val dirs = ArrayList<Directory>()
        val changed = HashMap<String, ArrayList<Medium>>()
        val seen = HashSet<String>()
        for ((folder, media) in byFolder) {
            val key = folder.lowercase(Locale.ROOT)
            if (!seen.add(key)) continue
            if (!folder.shouldFolderBeVisible(excluded, included, showHidden, noMediaStatuses) { p, has -> noMediaStatuses[p] = has }) continue
            fetcher.sortMedia(media, config.getFolderSorting(folder))
            val dir = context.createDirectoryFromMedia(folder, media, albumCovers, hiddenString, included, false, noMediaFolders)
            dirs.add(dir)
            if (cachedByPath[key]?.mediaCnt != media.size) changed[folder] = media
        }

        // 3. Android の一覧に載らないフォルダ（隠しフォルダなど）は、前回までの一覧にあって今もあれば残す
        cached.forEach { dir ->
            val key = dir.path.lowercase(Locale.ROOT)
            if (key in seen || dir.path == FAVORITES || dir.path == RECYCLE_BIN) return@forEach
            if (File(dir.path).isDirectory && (dir.path.contains("/.") || File(dir.path, NOMEDIA).exists() || included.contains(dir.path))) {
                dirs.add(dir)
                seen.add(key)
            }
        }

        // 4. お気に入りとごみ箱（どちらも数が少ないので中身を直接見る）
        listOf(FAVORITES, RECYCLE_BIN).forEach { special ->
            if (special == RECYCLE_BIN && !config.showRecycleBinAtFolders) return@forEach
            val media = fetcher.getFilesFrom(special, isPickImage, isPickVideo, false, false, false, ArrayList(favoriteSet), false, HashMap(), HashMap(), null)
            if (media.isNotEmpty()) {
                dirs.add(context.createDirectoryFromMedia(special, media, albumCovers, hiddenString, included, false, noMediaFolders))
            }
        }
        return Result(dirs, changed)
    }

    private fun typeOf(path: String) = when {
        path.isVideoFast() -> TYPE_VIDEOS
        path.isGif() -> TYPE_GIFS
        path.isRawFast() -> TYPE_RAWS
        path.isSvg() -> TYPE_SVGS
        else -> TYPE_IMAGES
    }
}
