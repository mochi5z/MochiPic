package org.fossify.gallery.mochipic

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import org.fossify.commons.extensions.getDuration
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.gallery.R
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.createDirectoryFromMedia
import org.fossify.gallery.extensions.dateTakensDB
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

/**
 * MochiPic: アルバム一覧を Android の画像の一覧（MediaStore）から作る（QP-Gallery と同じ考え方）。
 *
 * - [load]: 全件を 1 回だけ読んで一覧を作る（初回・設定の変更・引っ張って更新）
 * - [loadDiff]: 前回から変わったフォルダだけを読み直して、[AlbumMerge] で一覧に反映する（v0.1.1）
 * - [loadFolder]: アルバムを開いたとき、そのフォルダの中身を番号（BUCKET_ID）で 1 回だけ問い合わせる（v0.1.1）
 *
 * Android の一覧に載らないフォルダ（中身を直接見てしか分からない隠しフォルダなど）は、前回までの一覧にあれば残す。
 */
object QuickAlbumLoader {

    class Result(
        val dirs: ArrayList<Directory>,
        /** 新しく出てきたフォルダ・中身が変わったフォルダの中身（アプリのデータベースに入れておく）。 */
        val changedMedia: Map<String, ArrayList<Medium>>,
    )

    private val PROJECTION = arrayOf(
        MediaStore.MediaColumns.DATA,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.DATE_TAKEN,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.BUCKET_ID,
    )

    /** 1 回の読み込みで使い回す設定など。 */
    private class Env(val context: Context, val isPickImage: Boolean, val isPickVideo: Boolean) {
        val config = context.config
        val filterMedia = config.filterMedia
        val fetcher = MediaFetcher(context)
        val showHidden = config.shouldShowHidden
        val favoriteSet = context.getFavoritePaths().toHashSet()
        val selection = fetcher.getSelectionQuery(filterMedia)
        val selectionArgs = fetcher.getSelectionArgsQuery(filterMedia)
        val fixedDates: Map<String, Long> by lazy {
            try {
                context.dateTakensDB.getAllDateTakens().associate { it.fullPath to it.taken }
            } catch (e: Exception) {
                emptyMap()
            }
        }
        val excluded by lazy { if (config.temporarilyShowExcluded) HashSet() else config.excludedFolders }
        val included by lazy { config.includedFolders }
        val noMediaFolders by lazy { context.getNoMediaFoldersSync() }
        val noMediaStatuses by lazy { HashMap<String, Boolean>().also { map -> noMediaFolders.forEach { map["$it/$NOMEDIA"] = true } } }
        val albumCovers by lazy { config.parseAlbumCovers() }
        val hiddenString by lazy { context.getString(R.string.hidden) }

        fun query(extraSelection: String?, extraArgs: List<String>, uri: android.net.Uri = MediaStore.Files.getContentUri("external"), projection: Array<String> = PROJECTION): Cursor? {
            val sel = if (extraSelection == null) selection else "($selection) AND $extraSelection"
            return context.contentResolver.query(uri, projection, sel, (selectionArgs + extraArgs).toTypedArray(), null)
        }

        /** 行を読んでフォルダごとに分ける。番号（BUCKET_ID）とフォルダの対応も [buckets] に集める。 */
        fun readRows(cursor: Cursor, byFolder: MutableMap<String, ArrayList<Medium>>, buckets: MutableMap<Long, String>, withDurations: Boolean = false) {
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
                // 「日時の修正」で直した日付があればそちらを使う
                val taken = fixedDates[path] ?: cursor.getLong(2).takeIf { it != 0L } ?: modified
                val parent = path.substring(0, slash)
                buckets[cursor.getLong(4)] = parent
                val duration = if (withDurations && type == TYPE_VIDEOS) context.getDuration(path) ?: 0 else 0
                byFolder.getOrPut(parent) { ArrayList() }.add(
                    Medium(null, filename, path, parent, modified, taken, cursor.getLong(3), type, duration, favoriteSet.contains(path), 0L, 0L)
                )
            }
        }

        /** 表示するフォルダならアルバムの情報を作る。表示しない（除外・隠し・探索フォルダー外）なら null。 */
        fun buildDir(folder: String, media: ArrayList<Medium>): Directory? {
            if (media.isEmpty()) return null
            if (!folder.shouldFolderBeVisible(excluded, included, showHidden, noMediaStatuses) { p, has -> noMediaStatuses[p] = has }) return null
            fetcher.sortMedia(media, config.getFolderSorting(folder))
            return context.createDirectoryFromMedia(folder, media, albumCovers, hiddenString, included, false, noMediaFolders)
        }
    }

    // ---------------------------------------------------------------- 全件

    fun load(context: Context, cached: List<Directory>, isPickImage: Boolean, isPickVideo: Boolean): Result? {
        val env = Env(context, isPickImage, isPickVideo)
        if (env.filterMedia == 0) return Result(ArrayList(), emptyMap())

        // 1. Android の画像の一覧を 1 回だけ読んで、フォルダごとに分ける
        val byFolder = LinkedHashMap<String, ArrayList<Medium>>()
        val buckets = HashMap<Long, String>()
        try {
            env.query(null, emptyList())?.use { env.readRows(it, byFolder, buckets) }
        } catch (e: Exception) {
            return null  // 読めなければ従来どおりの読み込みに任せる
        }
        BucketMap.replaceAll(context, buckets)

        // 2. 表示するフォルダだけに絞る（除外・隠しフォルダ・探索フォルダーの設定）
        val cachedByPath = cached.associateBy { AlbumMerge.key(it.path) }
        val dirs = ArrayList<Directory>()
        val changed = HashMap<String, ArrayList<Medium>>()
        val seen = HashSet<String>()
        for ((folder, media) in byFolder) {
            val key = AlbumMerge.key(folder)
            if (!seen.add(key)) continue
            val dir = env.buildDir(folder, media) ?: continue
            dirs.add(dir)
            if (cachedByPath[key]?.mediaCnt != media.size) changed[folder] = media
        }

        // 3. Android の一覧に載らないフォルダ（隠しフォルダなど）は、前回までの一覧にあって今もあれば残す
        cached.forEach { dir ->
            val key = AlbumMerge.key(dir.path)
            if (key in seen || dir.path == FAVORITES || dir.path == RECYCLE_BIN) return@forEach
            if (File(dir.path).isDirectory && (dir.path.contains("/.") || File(dir.path, NOMEDIA).exists() || env.included.contains(dir.path))) {
                dirs.add(dir)
                seen.add(key)
            }
        }

        // 4. お気に入りとごみ箱（どちらも数が少ないので中身を直接見る）
        listOf(FAVORITES, RECYCLE_BIN).forEach { special ->
            if (special == RECYCLE_BIN && !env.config.showRecycleBinAtFolders) return@forEach
            val media = env.fetcher.getFilesFrom(
                special, isPickImage, isPickVideo, false, false, false, ArrayList(env.favoriteSet), false, HashMap(), HashMap(), null
            )
            if (media.isNotEmpty()) {
                dirs.add(context.createDirectoryFromMedia(special, media, env.albumCovers, env.hiddenString, env.included, false, env.noMediaFolders))
            }
        }
        return Result(dirs, changed)
    }

    // ---------------------------------------------------------------- 差分（v0.1.1）

    /**
     * 前回から変わったフォルダだけ読み直す。null なら全件読み（[load]）に切り替える。
     *  1. 追加・変更された行（GENERATION_MODIFIED が前回より大きい行）のフォルダ
     *  2. フォルダごとの枚数が保存済みと違うフォルダ（削除・移動元。移動元は 1 に出てこない）
     *  3. 1 と 2 のフォルダだけ、番号（BUCKET_ID）で中身を読み直して一覧に反映する
     */
    fun loadDiff(
        context: Context, cached: List<Directory>, old: MediaFetcher.ScanState, now: MediaFetcher.ScanState,
        isPickImage: Boolean, isPickVideo: Boolean,
    ): Result? {
        if (old.version != now.version || old.settings != now.settings || old.volumes.keys != now.volumes.keys) return null
        val env = Env(context, isPickImage, isPickVideo)
        if (env.filterMedia == 0) return null
        val changed = LinkedHashMap<Long, String>()  // 番号 → フォルダの場所
        try {
            // 1. 追加・変更された行
            for ((volume, generation) in now.volumes) {
                val oldGeneration = old.volumes[volume] ?: return null
                if (oldGeneration == generation) continue
                env.query(
                    "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ?", listOf(oldGeneration.toString()),
                    uri = MediaStore.Files.getContentUri(volume),
                    projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.BUCKET_ID),
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(0) ?: continue
                        changed[cursor.getLong(1)] = path.substringBeforeLast('/')
                    }
                }
            }
            BucketMap.addAll(context, changed)

            // 2. フォルダごとの枚数（番号だけの軽い問い合わせ）
            val counts = HashMap<Long, Int>()
            env.query(null, emptyList(), projection = arrayOf(MediaStore.MediaColumns.BUCKET_ID))?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    counts[id] = (counts[id] ?: 0) + 1
                }
            }
            cached.forEach { dir ->
                if (dir.path == FAVORITES || dir.path == RECYCLE_BIN) return@forEach
                val id = BucketMap.idOf(context, dir.path) ?: return@forEach  // Android の一覧に載らないフォルダは対象外
                if ((counts[id] ?: 0) != dir.mediaCnt) changed[id] = dir.path
            }
        } catch (e: Exception) {
            return null
        }

        if (changed.isEmpty()) return Result(ArrayList(cached), emptyMap())

        // 3. 変わったフォルダだけ読み直す
        val rebuilt = HashMap<String, Directory?>()
        val changedMedia = HashMap<String, ArrayList<Medium>>()
        try {
            for ((id, folder) in changed) {
                val byFolder = LinkedHashMap<String, ArrayList<Medium>>()
                env.query("${MediaStore.MediaColumns.BUCKET_ID} = ?", listOf(id.toString()))?.use { env.readRows(it, byFolder, HashMap()) }
                val media = byFolder.entries.firstOrNull { AlbumMerge.key(it.key) == AlbumMerge.key(folder) }?.value ?: ArrayList()
                val dir = env.buildDir(folder, media)
                rebuilt[folder] = dir
                if (dir != null) changedMedia[folder] = media
            }
        } catch (e: Exception) {
            return null
        }
        return Result(AlbumMerge.merge(cached, rebuilt) { it.path }, changedMedia)
    }

    // ---------------------------------------------------------------- アルバムを開くとき（v0.1.1）

    /**
     * フォルダの中身を番号（BUCKET_ID）で 1 回だけ問い合わせる。日付・サイズも同じ行から取る。
     * 番号が分からない・0 件（Android の一覧に載らないフォルダなど）なら null（従来どおりフォルダを直接見る）。
     */
    fun loadFolder(context: Context, path: String, isPickImage: Boolean, isPickVideo: Boolean, withDurations: Boolean): ArrayList<Medium>? {
        if (path.isEmpty() || path == FAVORITES || path == RECYCLE_BIN) return null
        val id = BucketMap.idOf(context, path) ?: return null
        val env = Env(context, isPickImage, isPickVideo)
        if (env.filterMedia == 0) return null
        return try {
            val byFolder = LinkedHashMap<String, ArrayList<Medium>>()
            env.query("${MediaStore.MediaColumns.BUCKET_ID} = ?", listOf(id.toString()))?.use { env.readRows(it, byFolder, HashMap(), withDurations) }
            val media = byFolder.entries.firstOrNull { AlbumMerge.key(it.key) == AlbumMerge.key(path) }?.value
            media?.takeIf { it.isNotEmpty() }?.also { env.fetcher.sortMedia(it, env.config.getFolderSorting(path)) }
        } catch (e: Exception) {
            null
        }
    }

    private fun typeOf(path: String) = when {
        path.isVideoFast() -> TYPE_VIDEOS
        path.isGif() -> TYPE_GIFS
        path.isRawFast() -> TYPE_RAWS
        path.isSvg() -> TYPE_SVGS
        else -> TYPE_IMAGES
    }
}
