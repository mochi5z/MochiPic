package org.fossify.gallery.mochipic

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.launchCamera
import org.fossify.gallery.helpers.DIRECTORY

/**
 * MochiPic: QP-Gallery のサイドメニュー（左端からのスワイプか ≡ で開く）。
 * 画面のレイアウトを書き換えずに済むよう、setContentView の後で画面全体を DrawerLayout で包む。
 * 項目を増やすときは [Item] に足して、[onItem] に動きを書く。
 */
class SideMenu(private val activity: Activity, private val current: Item) {

    enum class Item(val titleRes: Int, val iconRes: Int) {
        ALBUMS(R.string.mochipic_menu_albums, R.drawable.mochipic_ic_albums),
        DATE(R.string.mochipic_menu_date, R.drawable.mochipic_ic_date),
        EXPLORER(R.string.mochipic_menu_explorer, org.fossify.commons.R.drawable.ic_folder_open_vector),
        CAMERA(R.string.mochipic_menu_camera, org.fossify.commons.R.drawable.ic_camera_vector),
        WIFI(R.string.mochipic_menu_wifi, R.drawable.mochipic_ic_wifi),
        SETTINGS(R.string.mochipic_menu_settings, org.fossify.commons.R.drawable.ic_settings_cog_vector),
    }

    private lateinit var drawer: DrawerLayout
    private lateinit var panel: LinearLayout

    /** setContentView の直後に呼ぶ。 */
    fun attach(): SideMenu {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val screen = content.getChildAt(0)
        content.removeView(screen)

        drawer = DrawerLayout(activity)
        drawer.addView(screen, DrawerLayout.LayoutParams(MATCH, MATCH))
        panel = LayoutInflater.from(activity).inflate(R.layout.mochipic_side_menu, drawer, false) as LinearLayout
        val width = activity.resources.getDimensionPixelSize(R.dimen.mochipic_side_menu_width)
        drawer.addView(panel, DrawerLayout.LayoutParams(width, MATCH, Gravity.START))
        content.addView(drawer, ViewGroup.LayoutParams(MATCH, MATCH))

        // 画面の端まで描く設定（edge-to-edge）なので、ステータスバーとナビゲーションバーの分だけ内側に寄せる
        ViewCompat.setOnApplyWindowInsetsListener(panel) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, 0, bars.bottom)
            insets
        }

        panel.findViewById<View>(R.id.side_menu_back).setOnClickListener { close() }
        buildItems()
        updateColors()
        return this
    }

    private fun buildItems() {
        val holder = panel.findViewById<LinearLayout>(R.id.side_menu_items)
        holder.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        Item.entries.forEach { item ->
            val row = inflater.inflate(R.layout.mochipic_side_menu_item, holder, false)
            row.findViewById<ImageView>(R.id.side_menu_item_icon).setImageResource(item.iconRes)
            row.findViewById<TextView>(R.id.side_menu_item_title).setText(item.titleRes)
            row.tag = item
            row.setOnClickListener {
                close()
                // 閉じる動きが終わってから画面を切り替える（切り替えの方が先だと動きが途切れる）
                drawer.postDelayed({ onItem(item) }, CLOSE_DELAY_MS)
            }
            holder.addView(row)
        }
    }

    fun updateColors() {
        val bg = activity.getProperBackgroundColor()
        val text = activity.getProperTextColor()
        val primary = activity.getProperPrimaryColor()
        panel.setBackgroundColor(bg)
        panel.findViewById<ImageView>(R.id.side_menu_back).applyColorFilter(text)
        panel.findViewById<TextView>(R.id.side_menu_title).setTextColor(text)
        val holder = panel.findViewById<LinearLayout>(R.id.side_menu_items)
        for (i in 0 until holder.childCount) {
            val row = holder.getChildAt(i)
            val selected = row.tag == current
            val color = if (selected) primary else text
            row.findViewById<ImageView>(R.id.side_menu_item_icon).applyColorFilter(color)
            row.findViewById<TextView>(R.id.side_menu_item_title).setTextColor(color)
            row.setBackgroundColor(if (selected) primary.adjustAlpha(0.12f) else 0)
        }
    }

    fun open() = drawer.openDrawer(GravityCompat.START)

    fun close() = drawer.closeDrawer(GravityCompat.START)

    /** 戻るボタン用。開いていれば閉じて true。 */
    fun closeIfOpen(): Boolean {
        if (::drawer.isInitialized && drawer.isDrawerOpen(GravityCompat.START)) {
            close()
            return true
        }
        return false
    }

    private fun onItem(item: Item) {
        if (item == current && item != Item.CAMERA && item != Item.SETTINGS) return
        when (item) {
            Item.ALBUMS -> {
                activity.config.showAll = false
                activity.mochiPrefs.explorerMode = false
                restartWith(Intent(activity, MainActivity::class.java))
            }
            Item.DATE -> {
                activity.config.showAll = true
                activity.mochiPrefs.explorerMode = false
                restartWith(Intent(activity, MediaActivity::class.java).putExtra(DIRECTORY, ""))
            }
            Item.EXPLORER -> {
                activity.config.showAll = false
                activity.mochiPrefs.explorerMode = true
                restartWith(Intent(activity, ExplorerActivity::class.java))
            }
            Item.CAMERA -> activity.launchCamera()
            Item.WIFI -> activity.toast(R.string.mochipic_menu_wifi_later)
            Item.SETTINGS -> activity.startActivity(Intent(activity, SettingsCategoriesActivity::class.java))
        }
    }

    /** アルバム・日付・エクスプローラーは「起動画面」の切り替えなので、前の画面を残さない。 */
    private fun restartWith(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.overridePendingTransition(0, 0)
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val CLOSE_DELAY_MS = 220L
    }
}
