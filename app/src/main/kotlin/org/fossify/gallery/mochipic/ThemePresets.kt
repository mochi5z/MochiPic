package org.fossify.gallery.mochipic

import android.app.Activity
import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.models.RadioItem
import org.fossify.gallery.R

/**
 * MochiPic: QP-Gallery と同じ「色 × 暗い／明るい」のテーマ。
 * 選ぶと Fossify の色設定（文字・背景・アクセント）をまとめて書き換える。細かい調整は「色のカスタマイズ」で。
 * 色を足すときは [COLORS] に 1 行足すだけ。
 */
object ThemePresets {

    private class Hue(val nameRes: Int, val accent: Int, val darkBackground: Int)

    private val COLORS = listOf(
        Hue(R.string.mochipic_theme_gray, 0xFF78909C.toInt(), 0xFF212121.toInt()),
        Hue(R.string.mochipic_theme_violet, 0xFF7E57C2.toInt(), 0xFF1F1B2E.toInt()),
        Hue(R.string.mochipic_theme_black, 0xFF9E9E9E.toInt(), 0xFF000000.toInt()),
        Hue(R.string.mochipic_theme_orange, 0xFFFB8C00.toInt(), 0xFF26201A.toInt()),
        Hue(R.string.mochipic_theme_red, 0xFFE53935.toInt(), 0xFF2A1B1B.toInt()),
        Hue(R.string.mochipic_theme_pink, 0xFFEC407A.toInt(), 0xFF2A1B23.toInt()),
    )

    private const val DARK_TEXT = 0xFFFFFFFF.toInt()
    private const val LIGHT_BACKGROUND = 0xFFFFFFFF.toInt()
    private const val LIGHT_TEXT = 0xFF212121.toInt()

    /** 項目番号: 色の番号 × 2 + (0 = 暗い版, 1 = 明るい版) */
    fun show(activity: Activity, onApplied: () -> Unit) {
        val items = ArrayList<RadioItem>()
        COLORS.forEachIndexed { i, hue ->
            val name = activity.getString(hue.nameRes)
            items.add(RadioItem(i * 2, activity.getString(R.string.mochipic_theme_dark_format, name)))
            items.add(RadioItem(i * 2 + 1, activity.getString(R.string.mochipic_theme_light_format, name)))
        }
        RadioGroupDialog(activity, items, currentIndex(activity)) {
            apply(activity, it as Int)
            onApplied()
        }
    }

    private fun currentIndex(activity: Activity): Int {
        val c = activity.baseConfig
        for (i in COLORS.indices) for (light in 0..1) {
            if (c.primaryColor == COLORS[i].accent && c.backgroundColor == backgroundOf(i, light == 1)) return i * 2 + light
        }
        return -1
    }

    private fun backgroundOf(i: Int, light: Boolean) = if (light) LIGHT_BACKGROUND else COLORS[i].darkBackground

    private fun apply(activity: Activity, index: Int) {
        val hue = COLORS[index / 2]
        val light = index % 2 == 1
        activity.baseConfig.apply {
            isSystemThemeEnabled = false
            backgroundColor = backgroundOf(index / 2, light)
            textColor = if (light) LIGHT_TEXT else DARK_TEXT
            primaryColor = hue.accent
            accentColor = hue.accent
        }
    }
}
