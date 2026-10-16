package org.fossify.gallery.mochipic

import android.app.Activity
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import com.bumptech.glide.signature.ObjectKey
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MySquareImageView
import org.fossify.gallery.adapters.GridDirectoryItemMosaicBinding
import org.fossify.gallery.extensions.loadImage
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.helpers.ROUNDED_CORNERS_NONE
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.models.Directory
import java.io.File

/** MochiPic: QP-Gallery 風のアルバム表示スタイル「モザイク」（中の画像 4 枚を 2×2 に並べ、下に名前と枚数）。 */
const val FOLDER_STYLE_MOSAIC = 3

/**
 * モザイク表示の中身を用意する。
 * 1 枚目はアルバムの表紙（Fossify の設定どおり）、2〜4 枚目はフォルダ内の新しい順。
 * フォルダ一覧の取得は裏のスレッドで行い、結果はアプリが動いている間だけ覚えておく。
 */
object AlbumMosaic {
    private const val CELLS = 4
    private val cache = LruCache<String, List<String>>(300)

    fun cacheKey(dir: Directory) = "${dir.path}|${dir.modified}|${dir.mediaCnt}|${dir.tmb}"

    /**
     * フォルダ直下のメディアを新しい順に最大 4 件（表紙を先頭に）。
     * 保存済みの画像の一覧（アプリのデータベース）から取る。まだ入っていないフォルダだけ、フォルダの中身を直接見る。
     */
    fun pickPaths(context: android.content.Context, dir: Directory): List<String> {
        val fromDb = try {
            context.mediaDB.getLatestPaths(dir.path, CELLS + 1)
                .filter { it != dir.tmb && !it.substringAfterLast('/').startsWith(".") }
                .take(CELLS - 1)
        } catch (e: Exception) {
            emptyList()
        }
        val others = if (fromDb.isNotEmpty() || dir.mediaCnt <= 1) fromDb else listFolder(dir)
        return (listOf(dir.tmb) + others).filter { it.isNotEmpty() }.take(CELLS)
    }

    private fun listFolder(dir: Directory): List<String> = try {
        File(dir.path).listFiles { f -> f.isFile && !f.name.startsWith(".") && f.absolutePath.isMediaFile() }
            ?.sortedByDescending { it.lastModified() }
            ?.asSequence()
            ?.map { it.absolutePath }
            ?.filter { it != dir.tmb }
            ?.take(CELLS - 1)
            ?.toList()
            .orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    fun typeOf(path: String) = when {
        path.isVideoFast() -> TYPE_VIDEOS
        path.isGif() -> TYPE_GIFS
        path.isRawFast() -> TYPE_RAWS
        path.isSvg() -> TYPE_SVGS
        else -> TYPE_IMAGES
    }

    /** 「名前 枚数」（枚数は少し小さく薄い色）。 */
    private class LabelState(var name: String, var count: Int, var color: Int, var width: Int = -1, var shown: String = "")

    /**
     * 「名前 枚数」を 1 行で出す。名前が長いときは、枚数を必ず見せて名前のほうを「…」で縮める。
     * 幅が決まってから（と幅が変わったとき）縮め直すので、どの表示モードでも使える。
     */
    fun setLabel(view: android.widget.TextView, name: String, count: Int, textColor: Int) {
        val state = view.getTag(org.fossify.gallery.R.id.mochi_label_state) as? LabelState
        if (state == null) {
            val created = LabelState(name, count, textColor)
            view.setTag(org.fossify.gallery.R.id.mochi_label_state, created)
            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val s = v.getTag(org.fossify.gallery.R.id.mochi_label_state) as? LabelState ?: return@addOnLayoutChangeListener
                // ほかの所で文字を変えたあと（使い回された行など）は触らない
                if (s.width != v.width && (v as android.widget.TextView).text.toString() == s.shown) v.post { fitLabel(v, s) }
            }
        } else {
            state.name = name
            state.count = count
            state.color = textColor
            state.width = -1
        }
        fitLabel(view, view.getTag(org.fossify.gallery.R.id.mochi_label_state) as LabelState)
    }

    private fun fitLabel(view: android.widget.TextView, state: LabelState) {
        val avail = view.width - view.totalPaddingLeft - view.totalPaddingRight
        state.width = view.width
        if (avail <= 0 || state.count <= 0) {
            view.text = label(state.name, state.count, state.color)
            state.shown = view.text.toString()
            return
        }
        val countPaint = android.text.TextPaint(view.paint).apply { textSize = view.textSize * 0.85f }
        val countWidth = countPaint.measureText(" ${state.count}")
        val nameWidth = (avail - countWidth - 2f).coerceAtLeast(0f)
        val shortName = android.text.TextUtils.ellipsize(state.name, view.paint, nameWidth, android.text.TextUtils.TruncateAt.END).toString()
        view.text = label(shortName, state.count, state.color)
        state.shown = view.text.toString()
    }

    fun label(name: String, count: Int, textColor: Int): CharSequence {
        val sb = SpannableStringBuilder(name)
        if (count > 0) {
            val start = sb.length
            sb.append(" ").append(count.toString())
            sb.setSpan(ForegroundColorSpan(textColor.adjustAlpha(0.6f)), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.85f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return sb
    }

    /**
     * モザイクのマスを表示する。1 枚だけならタイル全体に、2 枚以上なら 2×2 のマスに順に入れる。
     * 行が別のアルバムに使い回されたときは、遅れて届いた結果を捨てる（目印は mosaicGrid.tag）。
     */
    fun bind(
        activity: Activity,
        b: GridDirectoryItemMosaicBinding,
        dir: Directory,
        animateGifs: Boolean,
        cropThumbnails: Boolean,
    ) {
        val key = cacheKey(dir)
        b.binding.mosaicGrid.tag = key   // root.tag は Fossify が使うので、モザイク側の View に目印を付ける
        val cached = cache.get(key)
        if (cached != null) {
            show(activity, b, dir, cached, animateGifs, cropThumbnails)
            return
        }

        // 一覧が届くまでは表紙だけを全体に出しておく
        show(activity, b, dir, listOf(dir.tmb), animateGifs, cropThumbnails)
        ensureBackgroundThread {
            val paths = pickPaths(activity, dir)
            cache.put(key, paths)
            activity.runOnUiThread {
                if (b.binding.mosaicGrid.tag == key && paths.size > 1) {
                    show(activity, b, dir, paths, animateGifs, cropThumbnails)
                }
            }
        }
    }

    private fun show(
        activity: Activity,
        b: GridDirectoryItemMosaicBinding,
        dir: Directory,
        paths: List<String>,
        animateGifs: Boolean,
        cropThumbnails: Boolean,
    ) {
        val single = paths.size <= 1
        b.binding.mosaicGrid.beVisibleIf(!single)
        b.binding.dirThumbnail.alpha = if (single) 1f else 0f
        b.binding.mosaicPlaySingle.beVisibleIf(single && dir.tmb.isVideoFast())
        if (single) return

        val cells = listOf(
            b.binding.mosaicCell1 to b.binding.mosaicPlay1,
            b.binding.mosaicCell2 to b.binding.mosaicPlay2,
            b.binding.mosaicCell3 to b.binding.mosaicPlay3,
            b.binding.mosaicCell4 to b.binding.mosaicPlay4,
        )
        // 2 枚なら上の段だけ（QP-Gallery と同じ）
        b.binding.mosaicRow2.beVisibleIf(paths.size > 2)
        cells.forEachIndexed { i, (cell, play) ->
            val path = paths.getOrNull(i)
            if (path == null) {
                cell.setImageDrawable(null)
                play.beGone()
            } else {
                load(activity, cell, path, dir, animateGifs, cropThumbnails)
                play.beVisibleIf(path.isVideoFast())
            }
        }
    }

    private fun load(activity: Activity, target: MySquareImageView, path: String, dir: Directory, animateGifs: Boolean, crop: Boolean) {
        target.scaleType = ImageView.ScaleType.CENTER_CROP
        activity.loadImage(
            type = typeOf(path),
            path = path,
            target = target,
            horizontalScroll = false,
            animateGifs = animateGifs,
            cropThumbnails = crop,
            roundCorners = ROUNDED_CORNERS_NONE,
            signature = ObjectKey("$path|${dir.modified}"),
            onError = { target.setImageDrawable(null); target.setBackgroundColor(Color.DKGRAY) }
        )
    }

    /**
     * フォルダの中の画像（表紙が先頭、最大 4 件）を取り出して [onReady] に渡す。覚えていればすぐ、なければ裏で調べてから。
     * 行が別のアルバムに使い回されたときは、遅れて届いた結果を捨てる（目印は [tagView] の tag）。
     */
    fun withPaths(activity: Activity, dir: Directory, tagView: View, onReady: (List<String>) -> Unit) {
        val key = cacheKey(dir)
        tagView.tag = key
        cache.get(key)?.let { onReady(it); return }
        ensureBackgroundThread {
            val paths = pickPaths(activity, dir)
            cache.put(key, paths)
            activity.runOnUiThread { if (tagView.tag == key) onReady(paths) }
        }
    }

    /** 使わない View を隠す（リスト表示などで共通部品を使い回すとき用）。 */
    fun hide(vararg views: View) = views.forEach { it.beGone() }
}
