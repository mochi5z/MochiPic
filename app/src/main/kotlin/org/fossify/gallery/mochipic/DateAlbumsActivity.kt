package org.fossify.gallery.mochipic

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.recyclerview.widget.GridLayoutManager
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.SearchActivity
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.databinding.ActivityMochiExplorerBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.SHOW_ALL
import org.fossify.gallery.helpers.SLIDESHOW_START_ON_ENTER
import org.fossify.commons.helpers.IS_FROM_GALLERY

/**
 * MochiPic: 「日付」表示（QP-Gallery と同じ）。写真・動画を月ごとのアルバムとして一覧にする。
 * 上部バーは「≡ 日付  [検索][スライドショー][︙]」。グリッドは表紙 1 枚と左寄せの「2026年10月」、リストは表紙と月名だけ。
 */
class DateAlbumsActivity : SimpleActivity() {

    private lateinit var binding: ActivityMochiExplorerBinding
    private lateinit var sideMenu: SideMenu
    private lateinit var adapter: FolderTileAdapter
    private var months: List<DateIndex.Month> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMochiExplorerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sideMenu = SideMenu(this, SideMenu.Item.DATE, binding.explorerMenu) { open ->
            QpTopBar.setDrawerOpen(binding.explorerMenu, open)
        }.attach()
        setupTopBar()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.explorerMenu),
            padBottomImeAndSystem = listOf(binding.explorerList),
        )
        adapter = FolderTileAdapter(this, mosaic = false, labelAtStart = true) { tile ->
            startActivity(Intent(this, MonthMediaActivity::class.java).putExtra(MonthMediaActivity.EXTRA_MONTH, tile.key))
        }
        binding.explorerList.layoutManager = GridLayoutManager(this, FolderTileAdapter.spanCount(this))
        binding.explorerList.adapter = adapter
        MochiCache.attachLazyLoad(this, binding.explorerList)
        binding.explorerEmpty.setText(R.string.mochipic_date_empty)
    }

    override fun onResume() {
        super.onResume()
        config.showAll = false  // 表示画面を「全部の写真」で開いたのを元に戻す（DateViewer を参照）
        binding.explorerMenu.updateColors()
        binding.explorerFastscroller.updateColors(getProperPrimaryColor())
        QpTopBar.updateColors(this, binding.explorerMenu)
        sideMenu.updateColors()
        binding.explorerEmpty.setTextColor(getProperTextColor())
        applyViewType()
        ensureBackgroundThread {
            val list = DateIndex.load(applicationContext)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                months = list
                adapter.items = list.map { m ->
                    FolderTile(m.key, m.title, 0, m.items.firstOrNull(), m.newestTime, showDate = false)
                }
                binding.explorerEmpty.beVisibleIf(list.isEmpty())
            }
        }
    }

    private fun applyViewType() {
        (binding.explorerList.layoutManager as GridLayoutManager).spanCount = FolderTileAdapter.spanCount(this)
        binding.explorerList.adapter = adapter
    }

    private fun setupTopBar() {
        val bar = binding.explorerMenu
        bar.setupMenu()
        QpTopBar.install(this, bar, getString(R.string.mochipic_menu_date), withSearch = false) { sideMenu.toggle() }
        bar.requireToolbar().menu.apply {
            add(Menu.NONE, MENU_SEARCH, 0, org.fossify.commons.R.string.search).apply {
                setIcon(org.fossify.commons.R.drawable.ic_search_vector)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            add(Menu.NONE, MENU_SLIDESHOW, 1, R.string.slideshow).apply {
                setIcon(R.drawable.mochipic_ic_cat_slideshow)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            add(Menu.NONE, MENU_VIEW_TYPE, 100, org.fossify.commons.R.string.change_view_type)
            add(Menu.NONE, MENU_SETTINGS, 101, org.fossify.commons.R.string.settings)
        }
        bar.requireToolbar().setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SEARCH -> startActivity(Intent(this, SearchActivity::class.java))
                MENU_SLIDESHOW -> months.firstOrNull()?.items?.firstOrNull()?.let { DateViewer.open(this, it, slideshow = true) }
                MENU_VIEW_TYPE -> AlbumMenus.showViewType(this, bar) { applyViewType() }
                MENU_SETTINGS -> launchSettings()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    override fun onDestroy() {
        MochiCache.onAppExit(this)
        super.onDestroy()
    }

    override fun onBackPressedCompat(): Boolean = sideMenu.closeIfOpen()

    companion object {
        private const val MENU_SEARCH = 1000
        private const val MENU_SLIDESHOW = 1001
        private const val MENU_VIEW_TYPE = 1002
        private const val MENU_SETTINGS = 1003
    }
}

/**
 * 日付表示から画像を開く。Fossify の表示画面は「全部の写真を表示」の設定（config.showAll）のときだけ
 * フォルダをまたいで日付順に送れるので、開く直前だけオンにする（日付表示の画面に戻ったらオフに戻す）。
 */
object DateViewer {
    fun open(context: Context, path: String, slideshow: Boolean = false) {
        context.config.showAll = true
        context.startActivity(Intent(context, ViewPagerActivity::class.java).apply {
            putExtra(PATH, path)
            putExtra(SHOW_ALL, true)
            putExtra(IS_FROM_GALLERY, true)
            if (slideshow) putExtra(SLIDESHOW_START_ON_ENTER, true)
        })
    }
}
