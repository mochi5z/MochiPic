package org.fossify.gallery.mochipic

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.launchCamera

/**
 * MochiPic: QP-Gallery のサイドメニュー（左端からのスワイプか ≡ で開く）。
 * QP と同じく上部バーの下から開き、開いている間は上部バーが「← ギャラリー」になる（[QpTopBar.setDrawerOpen]）。
 * 画面のレイアウトを書き換えずに済むよう、setContentView の後で画面全体を DrawerLayout で包む。
 * 項目を増やすときは [Item] に足して、[onItem] に動きを書く。
 */
class SideMenu(
    private val activity: Activity,
    private val current: Item,
    private val topBar: View?,
    private val onOpenChanged: (Boolean) -> Unit = {},
) {

    /** inMenu = サイドメニューに出すか（エクスプローラーは「表示形式」メニューから開く。QP と同じ）。 */
    enum class Item(val titleRes: Int, val iconRes: Int, val inMenu: Boolean = true) {
        ALBUMS(R.string.mochipic_menu_albums, R.drawable.mochipic_ic_cat_viewer),
        DATE(R.string.mochipic_menu_date, R.drawable.mochipic_ic_date),
        EXPLORER(R.string.mochipic_menu_explorer, org.fossify.commons.R.drawable.ic_folder_open_vector, inMenu = false),
        CAMERA(R.string.mochipic_menu_camera, org.fossify.commons.R.drawable.ic_camera_vector),
        WIFI(R.string.mochipic_menu_wifi, R.drawable.mochipic_ic_wifi),
        SETTINGS(R.string.mochipic_menu_settings, org.fossify.commons.R.drawable.ic_settings_cog_vector),
    }

    private lateinit var drawer: DrawerLayout
    private lateinit var panel: LinearLayout
    private lateinit var dim: View

    /** setContentView の直後に呼ぶ。 */
    fun attach(): SideMenu {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val screen = content.getChildAt(0)
        content.removeView(screen)

        // 画面 ＋ 上部バーより下だけを暗くする幕（DrawerLayout の標準の幕は上部バーまで暗くするので使わない）
        val holder = FrameLayout(activity)
        holder.addView(screen, FrameLayout.LayoutParams(MATCH, MATCH))
        dim = View(activity).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            visibility = View.GONE
        }
        holder.addView(dim, FrameLayout.LayoutParams(MATCH, MATCH))

        drawer = DrawerLayout(activity)
        drawer.setScrimColor(Color.TRANSPARENT)
        drawer.addView(holder, DrawerLayout.LayoutParams(MATCH, MATCH))
        panel = LayoutInflater.from(activity).inflate(R.layout.mochipic_side_menu, drawer, false) as LinearLayout
        val width = (activity.resources.displayMetrics.widthPixels * WIDTH_RATIO).toInt()
        drawer.addView(panel, DrawerLayout.LayoutParams(width, MATCH, Gravity.START))
        content.addView(drawer, ViewGroup.LayoutParams(MATCH, MATCH))

        ViewCompat.setOnApplyWindowInsetsListener(panel) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, 0, 0, bars.bottom)
            insets
        }

        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                dim.visibility = if (slideOffset > 0f) View.VISIBLE else View.GONE
                dim.alpha = slideOffset * DIM_ALPHA
            }

            override fun onDrawerStateChanged(newState: Int) {
                if (newState == DrawerLayout.STATE_DRAGGING || newState == DrawerLayout.STATE_SETTLING) syncTop()
            }

            override fun onDrawerOpened(drawerView: View) = onOpenChanged(true)
            override fun onDrawerClosed(drawerView: View) = onOpenChanged(false)
        })
        topBar?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncTop() }

        buildItems()
        updateColors()
        return this
    }

    /** メニューと幕の上端を、上部バーの下端にそろえる。 */
    private fun syncTop() {
        val bar = topBar ?: return
        val barLoc = IntArray(2).also { bar.getLocationInWindow(it) }
        val drawerLoc = IntArray(2).also { drawer.getLocationInWindow(it) }
        val top = (barLoc[1] + bar.height - drawerLoc[1]).coerceAtLeast(0)
        listOf(panel, dim).forEach { v ->
            val lp = v.layoutParams as ViewGroup.MarginLayoutParams
            if (lp.topMargin != top) {
                lp.topMargin = top
                v.layoutParams = lp
            }
        }
    }

    private fun buildItems() {
        val holder = panel.findViewById<LinearLayout>(R.id.side_menu_items)
        holder.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        Item.entries.filter { it.inMenu }.forEach { item ->
            val row = inflater.inflate(R.layout.mochipic_side_menu_item, holder, false)
            row.findViewById<ImageView>(R.id.side_menu_item_icon).setImageResource(item.iconRes)
            row.findViewById<TextView>(R.id.side_menu_item_title).setText(item.titleRes)
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
        // QP と同じく、画面の背景より少しだけ明るい（明るいテーマでは少しだけ暗い）灰色
        val panelColor = ColorUtils.blendARGB(bg, bg.getContrastColor(), PANEL_TINT)
        panel.setBackgroundColor(panelColor)
        val holder = panel.findViewById<LinearLayout>(R.id.side_menu_items)
        for (i in 0 until holder.childCount) {
            val row = holder.getChildAt(i)
            row.findViewById<ImageView>(R.id.side_menu_item_icon).applyColorFilter(text.adjustAlpha(ICON_ALPHA))
            row.findViewById<TextView>(R.id.side_menu_item_title).setTextColor(text)
        }
    }

    fun isOpen() = ::drawer.isInitialized && drawer.isDrawerOpen(GravityCompat.START)

    fun open() {
        syncTop()
        drawer.openDrawer(GravityCompat.START)
    }

    fun close() = drawer.closeDrawer(GravityCompat.START)

    fun toggle() = if (isOpen()) close() else open()

    /** 戻るボタン用。開いていれば閉じて true。 */
    fun closeIfOpen(): Boolean {
        if (isOpen()) {
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
                activity.config.showAll = false
                activity.mochiPrefs.explorerMode = false
                restartWith(Intent(activity, DateAlbumsActivity::class.java))
            }
            Item.EXPLORER -> {
                activity.config.showAll = false
                activity.mochiPrefs.explorerMode = true
                restartWith(Intent(activity, ExplorerActivity::class.java))
            }
            Item.CAMERA -> activity.launchCamera()
            Item.WIFI -> WifiSendActivity.start(activity, emptyList())
            Item.SETTINGS -> activity.startActivity(Intent(activity, SettingsCategoriesActivity::class.java))
        }
    }

    /** アルバム・日付・エクスプローラーは「起動画面」の切り替えなので、前の画面を残さない。 */
    private fun restartWith(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        @Suppress("DEPRECATION")
        activity.overridePendingTransition(0, 0)
    }

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val CLOSE_DELAY_MS = 220L
        private const val WIDTH_RATIO = 0.58f
        private const val DIM_ALPHA = 0.6f
        private const val PANEL_TINT = 0.04f
        private const val ICON_ALPHA = 0.75f
    }
}
