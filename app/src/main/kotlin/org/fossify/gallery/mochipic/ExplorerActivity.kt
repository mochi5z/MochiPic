package org.fossify.gallery.mochipic

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import androidx.recyclerview.widget.GridLayoutManager
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.helpers.AlphanumericComparator
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_DESCENDING
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.activities.MediaActivity
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.databinding.ActivityMochiExplorerBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.helpers.DIRECTORY
import java.io.File

/**
 * MochiPic: エクスプローラー（ストレージをフォルダの階層どおりにたどる画面）。QP-Gallery と同じ動き:
 *  - 上部バーは「≡ アルバム」のまま。上の階層へは戻るボタンで戻る
 *  - 1 つの画面にはフォルダだけを並べる（見た目はアルバム一覧と同じグリッド／リスト）
 *  - サブフォルダのあるフォルダを開くと、その中のフォルダの一覧。直下にも画像があれば、そのフォルダ自身を 1 項目として加える
 *  - サブフォルダがなく画像だけのフォルダは、通常のアルバムの中身を開く
 *  - SD カードは内部ストレージのいちばん上に、フォルダの 1 つとして並ぶ
 */
class ExplorerActivity : SimpleActivity() {

    private class Entry(val tile: FolderTile, val hasSubfolders: Boolean, val isSelf: Boolean)

    private lateinit var binding: ActivityMochiExplorerBinding
    private lateinit var sideMenu: SideMenu
    private lateinit var adapter: FolderTileAdapter

    private lateinit var currentPath: String
    private var entries: List<Entry> = emptyList()
    private val scrollStates = HashMap<String, Parcelable?>()
    private var loadToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMochiExplorerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sideMenu = SideMenu(this, SideMenu.Item.EXPLORER, binding.explorerMenu) { open ->
            QpTopBar.setDrawerOpen(binding.explorerMenu, open)
        }.attach()

        setupTopBar()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.explorerMenu),
            padBottomImeAndSystem = listOf(binding.explorerList),
        )

        adapter = FolderTileAdapter(this, mosaic = true, labelAtStart = false) { tile ->
            entries.firstOrNull { it.tile.key == tile.key }?.let { onEntry(it) }
        }
        binding.explorerList.layoutManager = GridLayoutManager(this, FolderTileAdapter.spanCount(this))
        binding.explorerList.adapter = adapter
        MochiCache.attachLazyLoad(this, binding.explorerList)

        currentPath = savedInstanceState?.getString(STATE_PATH) ?: rootPath()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PATH, currentPath)
    }

    override fun onResume() {
        super.onResume()
        binding.explorerMenu.updateColors()
        QpTopBar.updateColors(this, binding.explorerMenu)
        sideMenu.updateColors()
        binding.explorerEmpty.setTextColor(getProperTextColor())
        applyViewType()
        load(keepScroll = true)  // 中身を開いて削除・移動した場合に備えて毎回読み直す
    }

    private fun applyViewType() {
        (binding.explorerList.layoutManager as GridLayoutManager).spanCount = FolderTileAdapter.spanCount(this)
        binding.explorerList.adapter = adapter  // グリッドとリストで部品が違うので作り直させる
    }

    private fun setupTopBar() {
        val bar = binding.explorerMenu
        bar.setupMenu()
        bar.requireToolbar().menu.apply {
            add(Menu.NONE, MENU_SORT, 0, org.fossify.commons.R.string.sort_by).apply {
                setIcon(org.fossify.commons.R.drawable.ic_sort_vector)
                setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            add(Menu.NONE, MENU_VIEW_TYPE, 100, org.fossify.commons.R.string.change_view_type)
            add(Menu.NONE, MENU_SETTINGS, 101, org.fossify.commons.R.string.settings)
        }
        QpTopBar.install(this, bar, getString(R.string.mochipic_album_title)) { sideMenu.toggle() }
        bar.updateHintText(getString(R.string.mochipic_explorer_search_hint))
        bar.onSearchTextChangedListener = { showEntries() }
        bar.requireToolbar().setOnMenuItemClickListener { item ->
            when {
                QpTopBar.handleMenuClick(this, bar, item) -> Unit
                item.itemId == MENU_SORT -> AlbumMenus.showSort(this, bar, onDetailed = {}, onChanged = { showEntries() }, detailed = false)
                item.itemId == MENU_VIEW_TYPE -> AlbumMenus.showViewType(this, bar, onExplorer = { backToAlbums() }, explorerChecked = true) {
                    applyViewType()
                    showEntries()
                }
                item.itemId == MENU_SETTINGS -> launchSettings()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    override fun onDestroy() {
        MochiCache.onAppExit(this)
        super.onDestroy()
    }

    override fun onBackPressedCompat(): Boolean {
        return when {
            sideMenu.closeIfOpen() -> true
            binding.explorerMenu.isSearchOpen -> { binding.explorerMenu.closeSearch(); true }
            parentOf(currentPath) != null -> { open(parentOf(currentPath)!!); true }
            else -> false
        }
    }

    // ---------------------------------------------------------------- 階層

    private fun rootPath() = internalStoragePath.trimEnd('/')

    /**
     * SD カードや USB メモリ（取り外しできるストレージ）の場所。
     * 端末のストレージ一覧・アプリ用フォルダの場所・/storage の中身から集める（機種によって見つかる方法が違うため）。
     */
    private val externalVolumes: List<String> by lazy {
        val found = linkedSetOf<String>()
        sdCardPath.trimEnd('/').takeIf { it.isNotEmpty() }?.let { found.add(it) }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            getSystemService(android.os.storage.StorageManager::class.java)?.storageVolumes
                ?.filter { it.isRemovable }
                ?.mapNotNull { it.directory?.path }
                ?.forEach { found.add(it) }
        }
        getExternalFilesDirs(null).filterNotNull().map { it.path.substringBefore("/Android/") }.forEach { found.add(it) }
        File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }?.forEach { found.add(it.path) }
        found.map { it.trimEnd('/') }.filter { it != rootPath() && !it.startsWith(rootPath() + "/") && File(it).isDirectory }.distinct()
    }

    /** 1 つ上の階層。いちばん上（内部ストレージ）なら null。SD カードの上は内部ストレージ。 */
    private fun parentOf(path: String): String? = when (path) {
        rootPath() -> null
        in externalVolumes -> rootPath()
        else -> File(path).parent
    }

    private fun open(path: String) {
        scrollStates[currentPath] = binding.explorerList.layoutManager?.onSaveInstanceState()
        if (binding.explorerMenu.isSearchOpen) binding.explorerMenu.closeSearch()
        currentPath = path
        load(keepScroll = false)
    }

    private fun onEntry(entry: Entry) {
        when {
            entry.isSelf -> openAlbum(entry.tile.path)
            entry.hasSubfolders -> open(entry.tile.path)
            entry.tile.count > 0 -> openAlbum(entry.tile.path)
            else -> open(entry.tile.path)  // 空のフォルダ
        }
    }

    private fun openAlbum(path: String) {
        startActivity(Intent(this, MediaActivity::class.java).putExtra(DIRECTORY, path))
    }

    private fun backToAlbums() {
        mochiPrefs.explorerMode = false
        config.showAll = false
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    // ---------------------------------------------------------------- 読み込み

    private fun load(keepScroll: Boolean) {
        val token = ++loadToken
        val path = currentPath
        if (keepScroll) scrollStates[path] = binding.explorerList.layoutManager?.onSaveInstanceState()
        ensureBackgroundThread {
            val list = listFolder(path)
            runOnUiThread {
                if (token != loadToken || isDestroyed) return@runOnUiThread
                entries = list
                showEntries()
                val saved = scrollStates[path]
                if (saved != null) binding.explorerList.layoutManager?.onRestoreInstanceState(saved) else binding.explorerList.scrollToPosition(0)
            }
        }
    }

    private class Scan(val mediaCount: Int, val newest: File?, val hasSubfolders: Boolean)

    private fun scan(dir: File, showHidden: Boolean): Scan {
        val children = dir.listFiles()?.filter { showHidden || !it.name.startsWith(".") }.orEmpty()
        val media = children.filter { it.isFile && it.name.isMediaFile() }
        return Scan(media.size, media.maxByOrNull { it.lastModified() }, children.any { it.isDirectory })
    }

    private fun listFolder(path: String): List<Entry> {
        val showHidden = config.shouldShowHidden
        val dir = File(path)
        val subdirs = dir.listFiles()?.filter { it.isDirectory && (showHidden || !it.name.startsWith(".")) }.orEmpty().toMutableList()
        if (path == rootPath()) externalVolumes.forEach { subdirs.add(File(it)) }

        val list = subdirs.map { sub ->
            val s = scan(sub, showHidden)
            Entry(FolderTile(sub.path, sub.name, s.mediaCount, s.newest?.path, sub.lastModified()), s.hasSubfolders, isSelf = false)
        }.toMutableList()

        // 直下にも画像があるフォルダは、そのフォルダ自身を 1 項目として加える（QP-Gallery と同じ）
        val self = scan(dir, showHidden)
        if (self.mediaCount > 0) {
            list.add(Entry(FolderTile(SELF_PREFIX + dir.path, dir.name, self.mediaCount, self.newest?.path, dir.lastModified(), path = dir.path), false, isSelf = true))
        }
        return list
    }

    private fun sorted(list: List<Entry>): List<Entry> {
        val sorting = config.directorySorting
        val cmp = AlphanumericComparator()
        val byName = Comparator<Entry> { a, b -> cmp.compare(a.tile.name.lowercase(), b.tile.name.lowercase()) }
        val base = if (sorting and SORT_BY_DATE_MODIFIED != 0) compareBy<Entry> { it.tile.modified }.then(byName) else byName
        return list.sortedWith(if (sorting and SORT_DESCENDING != 0) base.reversed() else base)
    }

    private fun showEntries() {
        val query = binding.explorerMenu.getCurrentQuery()
        val shown = sorted(entries).filter { query.isBlank() || it.tile.name.contains(query, ignoreCase = true) }
        adapter.items = shown.map { it.tile }
        binding.explorerEmpty.beVisibleIf(shown.isEmpty())
    }

    companion object {
        private const val MENU_SORT = 1000
        private const val MENU_VIEW_TYPE = 1001
        private const val MENU_SETTINGS = 1002
        private const val STATE_PATH = "mochipic_explorer_path"
        private const val SELF_PREFIX = "self:"
    }
}
