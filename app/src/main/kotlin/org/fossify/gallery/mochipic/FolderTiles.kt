package org.fossify.gallery.mochipic

import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.gallery.adapters.GridDirectoryItemMosaicBinding
import org.fossify.gallery.databinding.DirectoryItemGridMosaicBinding
import org.fossify.gallery.databinding.DirectoryItemListBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.loadImage
import org.fossify.gallery.helpers.ROUNDED_CORNERS_NONE
import org.fossify.gallery.models.Directory

/**
 * MochiPic: アルバム一覧と同じ見た目のフォルダの 1 項目（エクスプローラーと日付表示で使う）。
 * グリッド = アルバム一覧のモザイクのタイル、リスト = アルバム一覧のリストの 1 行。
 *
 * @param count 枚数。0 以下なら出さない（画像のないフォルダ）
 * @param thumb 表紙の画像。null なら灰色の地にフォルダの絵
 * @param showDate リスト表示で 2 行目に更新日時を出すか（日付表示では出さない）
 */
data class FolderTile(
    val key: String,
    val name: String,
    val count: Int,
    val thumb: String?,
    val modified: Long,
    val path: String = key,
    val showDate: Boolean = true,
)

/**
 * @param mosaic グリッドで中の画像 4 枚を 2×2 に並べるか（エクスプローラー）。false なら表紙 1 枚（日付表示）
 * @param labelAtStart グリッドの名前を左寄せにするか（日付表示）。false なら中央（アルバム一覧と同じ）
 */
class FolderTileAdapter(
    private val activity: Activity,
    private val mosaic: Boolean,
    private val labelAtStart: Boolean,
    private val onClick: (FolderTile) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var items: List<FolderTile> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    private val isGrid get() = activity.config.viewTypeFolders == VIEW_TYPE_GRID

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = if (isGrid) TYPE_GRID else TYPE_LIST

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = if (viewType == TYPE_GRID) {
            DirectoryItemGridMosaicBinding.inflate(inflater, parent, false).root
        } else {
            DirectoryItemListBinding.inflate(inflater, parent, false).root
        }
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val tile = items[position]
        if (holder.itemViewType == TYPE_GRID) {
            bindGrid(DirectoryItemGridMosaicBinding.bind(holder.itemView), tile)
        } else {
            bindList(DirectoryItemListBinding.bind(holder.itemView), tile)
        }
        holder.itemView.setOnClickListener { onClick(tile) }
    }

    private val textColor get() = activity.getProperTextColor()

    private fun bindGrid(b: DirectoryItemGridMosaicBinding, tile: FolderTile) {
        listOf(b.dirLock, b.dirCheck, b.dirPin, b.dirDragHandleWrapper, b.dirLocation, b.photoCnt).forEach { it.beGone() }
        val wrapper = GridDirectoryItemMosaicBinding(b)
        AlbumStyles.reset(activity, wrapper)
        loadThumb(b.dirThumbnail, tile, big = true)

        if (labelAtStart) {
            // 日付表示: 表紙 1 枚と左寄せの月名（QP-Gallery と同じ）
            b.dirName.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.dirName.text = tile.name
            b.dirName.setTextColor(textColor)
            b.mosaicPlaySingle.beVisibleIf(tile.thumb?.isVideoFast() == true)
            return
        }

        // エクスプローラー: アルバム一覧と同じ表示モード（デフォルト・スタック・グリッド）
        val style = if (mosaic) activity.config.folderStyle else FOLDER_STYLE_QP_DEFAULT
        if (tile.thumb != null) {
            val dir = Directory(null, tile.path, tile.thumb, tile.name, tile.count, tile.modified, 0L, 0L, 0, 0, "")
            AlbumStyles.bind(activity, wrapper, dir, style, animateGifs = false, crop = true)
        }
        AlbumStyles.label(activity, wrapper, style, tile.name, tile.count, textColor)
        if (tile.count <= 0) b.photoCnt.beGone()
    }

    private fun bindList(b: DirectoryItemListBinding, tile: FolderTile) {
        listOf(b.dirLock, b.dirCheck, b.dirPath, b.photoCnt, b.dirIconHolder).forEach { it.beGone() }
        b.dirName.text = AlbumMosaic.label(tile.name, tile.count, textColor)
        b.dirName.setTextColor(textColor)
        b.dirDate.beVisibleIf(tile.showDate)
        b.dirDate.text = tile.modified.formatDate(activity)
        b.dirDate.setTextColor(textColor.adjustAlpha(0.7f))
        b.dirPlay.beVisibleIf(tile.thumb?.isVideoFast() == true)
        b.dirDivider.setBackgroundColor(textColor.adjustAlpha(0.15f))
        loadThumb(b.dirThumbnail, tile, big = false)
    }

    private fun loadThumb(target: ImageView, tile: FolderTile, big: Boolean) {
        Glide.with(activity).clear(target)
        val thumb = tile.thumb
        if (thumb == null) {
            // 画像のないフォルダ: QP と同じく灰色の地にフォルダの絵
            val bg = activity.getProperBackgroundColor()
            target.setBackgroundColor(ColorUtils.blendARGB(bg, bg.getContrastColor(), 0.1f))
            target.scaleType = ImageView.ScaleType.FIT_CENTER
            val pad = activity.resources.displayMetrics.density * (if (big) 36 else 16)
            target.setPadding(pad.toInt(), pad.toInt(), pad.toInt(), pad.toInt())
            target.setImageResource(org.fossify.commons.R.drawable.ic_folder_outline_vector)
            target.applyColorFilter(textColor.adjustAlpha(0.45f))
            return
        }
        target.background = null
        target.colorFilter = null
        target.setPadding(0, 0, 0, 0)
        target.scaleType = ImageView.ScaleType.CENTER_CROP
        activity.loadImage(
            type = AlbumMosaic.typeOf(thumb),
            path = thumb,
            target = target as org.fossify.commons.views.MySquareImageView,
            horizontalScroll = false,
            animateGifs = false,
            cropThumbnails = true,
            roundCorners = ROUNDED_CORNERS_NONE,
            signature = ObjectKey("$thumb|${tile.modified}"),
        )
    }

    companion object {
        private const val TYPE_GRID = 0
        private const val TYPE_LIST = 1

        /** グリッドの列数（アルバム一覧と同じ設定）。リストなら 1。 */
        fun spanCount(activity: Activity) = if (activity.config.viewTypeFolders == VIEW_TYPE_GRID) activity.config.dirColumnCnt else 1
    }
}

