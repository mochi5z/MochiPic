package org.fossify.gallery.mochipic

import android.app.Activity
import android.graphics.Point
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getResolution
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.models.Medium
import java.io.File
import kotlin.math.roundToInt

/**
 * MochiPic: 表示画面の左下の情報ボタンで出す詳細（QP-Gallery と同じ）。
 * タイトルは「何枚目／全体」、項目は パス・MIME タイプ・サイズ・解像度・撮影日時、ボタンは［更新日時の修正］［OK］。
 */
object MochiDetailsDialog {

    fun show(activity: Activity, medium: Medium, position: Int, total: Int, onFixDate: () -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.mochipic_details_dialog, null)
        val rows = view.findViewById<LinearLayout>(R.id.details_rows)
        val text = activity.getProperTextColor()

        fun addRow(labelRes: Int, value: String): TextView {
            val row = LayoutInflater.from(activity).inflate(R.layout.mochipic_details_row, rows, false)
            row.findViewById<TextView>(R.id.details_label).apply { setText(labelRes); setTextColor(text.adjustAlpha(0.7f)) }
            val valueView = row.findViewById<TextView>(R.id.details_value).apply { this.text = value; setTextColor(text) }
            rows.addView(row)
            return valueView
        }

        val path = medium.path
        val size = if (medium.size > 0) medium.size else File(path).length()
        addRow(R.string.mochipic_details_path, path)
        addRow(R.string.mochipic_details_mime, path.getMimeType())
        addRow(R.string.mochipic_details_size, size.formatSize())
        val resolution = addRow(R.string.mochipic_details_resolution, "…")
        val time = if (medium.taken > 0) medium.taken else medium.modified
        addRow(R.string.mochipic_details_taken, if (time > 0) time.formatDate(activity) else "-")

        // 解像度はファイルを読むので裏で調べる
        ensureBackgroundThread {
            val p = try { activity.getResolution(path) } catch (e: Exception) { null }
            activity.runOnUiThread { resolution.text = p?.let { format(it) } ?: "-" }
        }

        activity.getAlertDialogBuilder()
            .setNegativeButton(R.string.mochipic_fix_date) { _, _ -> onFixDate() }
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .apply { activity.setupDialogStuff(view, this, titleText = "${position + 1}/$total") }
    }

    /** 4000x3000=12MP */
    fun format(p: Point): String {
        val mp = p.x.toLong() * p.y / 1_000_000.0
        val mpText = if (mp >= 10) mp.roundToInt().toString() else "%.1f".format(mp).removeSuffix(".0")
        return "${p.x}x${p.y}=${mpText}MP"
    }
}
