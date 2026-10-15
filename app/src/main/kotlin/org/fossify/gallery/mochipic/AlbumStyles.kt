package org.fossify.gallery.mochipic

import android.app.Activity
import android.view.Gravity
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.graphics.ColorUtils
import com.bumptech.glide.Glide
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.helpers.VIEW_TYPE_GRID
import org.fossify.commons.helpers.VIEW_TYPE_LIST
import org.fossify.gallery.adapters.GridDirectoryItemMosaicBinding
import org.fossify.gallery.helpers.Config
import org.fossify.gallery.models.Directory

/** MochiPic: QP-Gallery の表示モード「デフォルト」（表紙 1 枚、下に名前と枚数の 2 行）。 */
const val FOLDER_STYLE_QP_DEFAULT = 4

/** MochiPic: QP-Gallery の表示モード「スタック」（表紙の右に 2・3 枚目が重なってのぞく）。 */
const val FOLDER_STYLE_STACK = 5

/**
 * MochiPic: アルバム一覧の表示モード（QP-Gallery と同じ 4 種類）。
 * グリッド系の 3 つ（デフォルト・スタック・グリッド＝モザイク）は同じ部品（directory_item_grid_mosaic.xml）を使い、
 * 表示のときに見た目を切り替える。新しいモードを足すときは [Mode] と [bind]・[label] に足す。
 */
object AlbumStyles {

    enum class Mode { DEFAULT, STACK, GRID, LIST }

    fun usesTile(folderStyle: Int) =
        folderStyle == FOLDER_STYLE_MOSAIC || folderStyle == FOLDER_STYLE_QP_DEFAULT || folderStyle == FOLDER_STYLE_STACK

    fun current(config: Config): Mode = when {
        config.viewTypeFolders == VIEW_TYPE_LIST -> Mode.LIST
        config.folderStyle == FOLDER_STYLE_QP_DEFAULT -> Mode.DEFAULT
        config.folderStyle == FOLDER_STYLE_STACK -> Mode.STACK
        else -> Mode.GRID
    }

    fun set(config: Config, mode: Mode) {
        when (mode) {
            Mode.LIST -> config.viewTypeFolders = VIEW_TYPE_LIST
            Mode.DEFAULT -> { config.viewTypeFolders = VIEW_TYPE_GRID; config.folderStyle = FOLDER_STYLE_QP_DEFAULT }
            Mode.STACK -> { config.viewTypeFolders = VIEW_TYPE_GRID; config.folderStyle = FOLDER_STYLE_STACK }
            Mode.GRID -> { config.viewTypeFolders = VIEW_TYPE_GRID; config.folderStyle = FOLDER_STYLE_MOSAIC }
        }
    }

    /** 表紙（dir_thumbnail）を読み込んだ後に呼ぶ。モードごとの見た目にする。 */
    fun bind(activity: Activity, b: GridDirectoryItemMosaicBinding, dir: Directory, folderStyle: Int, animateGifs: Boolean, crop: Boolean) {
        reset(activity, b)
        when (folderStyle) {
            FOLDER_STYLE_MOSAIC -> AlbumMosaic.bind(activity, b, dir, animateGifs, crop)
            FOLDER_STYLE_QP_DEFAULT -> bindDefault(activity, b, dir)
            FOLDER_STYLE_STACK -> bindStack(activity, b, dir)
        }
    }

    /** 名前と枚数。デフォルトは左寄せの 2 行、スタックとグリッドは中央に「名前 枚数」。 */
    fun label(activity: Activity, b: GridDirectoryItemMosaicBinding, folderStyle: Int, name: String, count: Int, textColor: Int) {
        val v = b.binding
        if (folderStyle == FOLDER_STYLE_QP_DEFAULT) {
            v.dirName.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            v.dirName.text = name
            v.dirName.setTextColor(textColor)
            v.dirName.setPadding(dp(activity, 6), dp(activity, 4), dp(activity, 6), 0)
            v.photoCnt.beVisible()
            v.photoCnt.text = count.toString()
            v.photoCnt.setTextColor(textColor.adjustAlpha(0.7f))
            v.photoCnt.textSize = 14f
            v.photoCnt.setPadding(dp(activity, 6), 0, dp(activity, 6), dp(activity, 6))
            (v.photoCnt.layoutParams as RelativeLayout.LayoutParams).apply {
                addRule(RelativeLayout.BELOW, v.dirName.id)
                addRule(RelativeLayout.ALIGN_PARENT_START)
            }
        } else {
            v.dirName.gravity = Gravity.CENTER
            v.dirName.text = AlbumMosaic.label(name, count, textColor)
            v.dirName.setTextColor(textColor)
            val small = dp(activity, 4)
            v.dirName.setPadding(small, small, small, dp(activity, 8))
            v.photoCnt.beGone()
        }
    }

    /** 使い回された部品を、どのモードにも寄らない状態に戻す。 */
    fun reset(activity: Activity, b: GridDirectoryItemMosaicBinding) {
        val v = b.binding
        v.dirHolder.background = null
        v.stackLayer1.beGone()
        v.stackLayer2.beGone()
        v.stackLayer1.tag = null
        setMargins(v.dirThumbnail, 0, 0, 0)
        v.mosaicGrid.beGone()
        v.mosaicGrid.tag = null
        v.dirThumbnail.alpha = 1f
        v.mosaicPlaySingle.beGone()
        Glide.with(activity).clear(v.stackLayer1)
        Glide.with(activity).clear(v.stackLayer2)
    }

    private fun bindDefault(activity: Activity, b: GridDirectoryItemMosaicBinding, dir: Directory) {
        val v = b.binding
        val bg = activity.getProperBackgroundColor()
        // 名前と枚数の帯（とタイルの細い枠）は背景より少し明るい灰色
        v.dirHolder.setBackgroundColor(ColorUtils.blendARGB(bg, bg.getContrastColor(), 0.08f))
        v.mosaicPlaySingle.beVisibleIf(dir.tmb.isVideoFast())
    }

    private fun bindStack(activity: Activity, b: GridDirectoryItemMosaicBinding, dir: Directory) {
        val v = b.binding
        val start = dp(activity, 14)
        val end = dp(activity, 30)
        val top = dp(activity, 6)
        val step = dp(activity, 8)
        setMargins(v.dirThumbnail, start, end, top)
        v.mosaicPlaySingle.beVisibleIf(dir.tmb.isVideoFast())
        AlbumMosaic.withPaths(activity, dir, v.stackLayer1) { paths ->
            val layers = listOf(v.stackLayer1 to paths.getOrNull(1), v.stackLayer2 to paths.getOrNull(2))
            layers.forEachIndexed { i, (layer, path) ->
                if (path == null) {
                    layer.beGone()
                } else {
                    setMargins(layer, start + step * (i + 1), end - step * (i + 1), top)
                    layer.beVisible()
                    layer.scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(activity).load(path).centerCrop().into(layer)
                }
            }
        }
    }

    private fun setMargins(view: android.view.View, start: Int, end: Int, top: Int) {
        val lp = view.layoutParams as RelativeLayout.LayoutParams
        if (lp.marginStart != start || lp.marginEnd != end || lp.topMargin != top) {
            lp.marginStart = start
            lp.marginEnd = end
            lp.topMargin = top
            view.layoutParams = lp
        }
    }

    private fun dp(activity: Activity, v: Int) = (v * activity.resources.displayMetrics.density).toInt()
}
