package org.fossify.gallery.mochipic

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.helpers.AlphanumericComparator
import org.fossify.commons.helpers.IS_FROM_GALLERY
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.MainActivity
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.activities.ViewPagerActivity
import org.fossify.gallery.databinding.ActivityMochiExplorerBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.launchSettings
import org.fossify.gallery.helpers.PATH
import org.fossify.gallery.helpers.SHOW_ALL
import java.io.File

/**
 * MochiPic: エクスプローラー（ストレージをフォルダの階層どおりにたどる画面）。
 * 上にフォルダを 1 行ずつ、その下に今のフォルダの画像・動画をグリッドで並べる。
 * SD カードがあるときだけ、いちばん上の階層に「内部ストレージ」「SD カード」を出す。
 */
class ExplorerActivity : SimpleActivity() {

    sealed class Entry {
        abstract val path: String
        data class Folder(
            override val path: String, val name: String, val mediaCount: Int,
            val thumb: String?, val modified: Long, val isVolume: Boolean,
        ) : Entry()
        data class Media(override val path: String) : Entry()
    }

    private lateinit var binding: ActivityMochiExplorerBinding
    private lateinit var sideMenu: SideMenu
    private val adapter = ExplorerAdapter()

    /** null = いちばん上（ストレージの一覧）。 */
    private var currentPath: String? = null
    private var allEntries: List<Entry> = emptyList()
    private val scrollStates = HashMap<String, Parcelable?>()
    private var loadToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMochiExplorerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        sideMenu = SideMenu(this, SideMenu.Item.EXPLORER).attach()

        setupTopBar()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.explorerMenu),
            padBottomImeAndSystem = listOf(binding.explorerList),
        )

        val span = config.mediaColumnCnt.coerceAtLeast(2)
        binding.explorerList.layoutManager = GridLayoutManager(this, span).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) =
                    if (adapter.items.getOrNull(position) is Entry.Folder) span else 1
            }
        }
        binding.explorerList.adapter = adapter
        binding.explorerPathBar.setOnClickListener { goUp() }

        currentPath = savedInstanceState?.getString(STATE_PATH) ?: startPath()
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
        val text = getProperTextColor()
        binding.explorerPath.setTextColor(text.adjustAlpha(0.75f))
        binding.explorerUp.applyColorFilter(text)
        binding.explorerEmpty.setTextColor(text)
        load(currentPath, keepScroll = true)  // 表示画面で削除・名前変更した場合に備えて毎回読み直す
    }

    private fun setupTopBar() {
        val bar = binding.explorerMenu
        bar.setupMenu()
        bar.requireToolbar().menu.apply {
            add(Menu.NONE, MENU_ALBUMS, 100, R.string.mochipic_explorer_exit)
            add(Menu.NONE, MENU_SETTINGS, 101, org.fossify.commons.R.string.settings)
        }
        QpTopBar.install(this, bar, getString(R.string.mochipic_menu_explorer)) { sideMenu.open() }
        bar.updateHintText(getString(R.string.mochipic_explorer_search_hint))
        bar.onSearchTextChangedListener = { showEntries(filter(it)) }
        bar.requireToolbar().setOnMenuItemClickListener { item ->
            when {
                QpTopBar.handleMenuClick(this, bar, item) -> Unit
                item.itemId == MENU_ALBUMS -> backToAlbums()
                item.itemId == MENU_SETTINGS -> launchSettings()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    override fun onBackPressedCompat(): Boolean {
        return when {
            sideMenu.closeIfOpen() -> true
            binding.explorerMenu.isSearchOpen -> { binding.explorerMenu.closeSearch(); true }
            parentOf(currentPath) != NO_PARENT -> { goUp(); true }
            else -> false
        }
    }

    // ---------------------------------------------------------------- 階層の移動

    private fun volumes(): List<Pair<String, String>> {
        val list = mutableListOf(internalStoragePath to getString(R.string.mochipic_explorer_internal))
        if (sdCardPath.isNotEmpty() && File(sdCardPath).canRead()) {
            list += sdCardPath to getString(R.string.mochipic_explorer_sd)
        }
        return list
    }

    /** SD カードがなければ、内部ストレージの中から始める（一覧に 1 つしかない階層は飛ばす）。 */
    private fun startPath(): String? = volumes().singleOrNull()?.first

    private fun parentOf(path: String?): String? {
        if (path == null) return NO_PARENT
        val vols = volumes()
        if (vols.any { it.first.trimEnd('/') == path.trimEnd('/') }) {
            return if (vols.size > 1) null else NO_PARENT
        }
        return File(path).parent
    }

    private fun open(path: String?) {
        rememberScroll()
        if (binding.explorerMenu.isSearchOpen) binding.explorerMenu.closeSearch()
        currentPath = path
        load(path, keepScroll = false)
    }

    private fun goUp() {
        val parent = parentOf(currentPath)
        if (parent != NO_PARENT) open(parent)
    }

    private fun backToAlbums() {
        mochiPrefs.explorerMode = false
        config.showAll = false
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }

    private fun rememberScroll() {
        scrollStates[currentPath ?: ROOT_KEY] = binding.explorerList.layoutManager?.onSaveInstanceState()
    }

    // ---------------------------------------------------------------- 読み込み

    private fun load(path: String?, keepScroll: Boolean) {
        val token = ++loadToken
        if (keepScroll) rememberScroll()
        updateHeader(path)
        ensureBackgroundThread {
            val entries = if (path == null) listVolumes() else listFolder(path)
            runOnUiThread {
                if (token != loadToken || isDestroyed) return@runOnUiThread
                allEntries = entries
                showEntries(filter(binding.explorerMenu.getCurrentQuery()))
                val saved = scrollStates[path ?: ROOT_KEY]
                if (saved != null) {
                    binding.explorerList.layoutManager?.onRestoreInstanceState(saved)
                } else {
                    binding.explorerList.scrollToPosition(0)
                }
            }
        }
    }

    private fun updateHeader(path: String?) {
        val title = when {
            path == null -> getString(R.string.mochipic_menu_explorer)
            else -> volumes().firstOrNull { it.first.trimEnd('/') == path.trimEnd('/') }?.second ?: File(path).name
        }
        QpTopBar.setTitle(binding.explorerMenu, title)
        binding.explorerPath.text = path?.let { displayPath(it) } ?: getString(R.string.mochipic_explorer_storage_list)
        binding.explorerUp.beVisibleIf(parentOf(path) != NO_PARENT)
    }

    /** /storage/emulated/0/Pictures → 内部ストレージ / Pictures */
    private fun displayPath(path: String): String {
        val vol = volumes().firstOrNull { path.startsWith(it.first) } ?: return path
        val rest = path.removePrefix(vol.first).trim('/')
        return if (rest.isEmpty()) vol.second else vol.second + " / " + rest.replace("/", " / ")
    }

    private fun listVolumes(): List<Entry> = volumes().map { (path, name) ->
        Entry.Folder(path, name, -1, null, 0L, isVolume = true)
    }

    private fun listFolder(path: String): List<Entry> {
        val showHidden = config.shouldShowHidden
        val children = File(path).listFiles()?.filter { showHidden || !it.name.startsWith(".") } ?: return emptyList()
        val cmp = AlphanumericComparator()
        val byName = Comparator<File> { a, b -> cmp.compare(a.name.lowercase(), b.name.lowercase()) }

        val folders = children.filter { it.isDirectory }.sortedWith(byName).map { dir ->
            val media = dir.listFiles()?.filter { it.isFile && it.name.isMediaFile() && (showHidden || !it.name.startsWith(".")) }.orEmpty()
            val newest = media.maxByOrNull { it.lastModified() }
            Entry.Folder(dir.path, dir.name, media.size, newest?.path, dir.lastModified(), isVolume = false)
        }
        val media = children.filter { it.isFile && it.name.isMediaFile() }.sortedWith(byName).map { Entry.Media(it.path) }
        return folders + media
    }

    private fun filter(query: String): List<Entry> =
        if (query.isBlank()) allEntries else allEntries.filter { File(it.path).name.contains(query, ignoreCase = true) }

    private fun showEntries(entries: List<Entry>) {
        adapter.items = entries
        adapter.notifyDataSetChanged()
        binding.explorerEmpty.beVisibleIf(entries.isEmpty())
    }

    private fun openMedia(path: String) {
        Intent(this, ViewPagerActivity::class.java).apply {
            putExtra(PATH, path)
            putExtra(SHOW_ALL, false)
            putExtra(IS_FROM_GALLERY, true)
            startActivity(this)
        }
    }

    // ---------------------------------------------------------------- 一覧

    private inner class ExplorerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<Entry> = emptyList()

        override fun getItemCount() = items.size

        override fun getItemViewType(position: Int) = if (items[position] is Entry.Folder) TYPE_FOLDER else TYPE_MEDIA

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layout = if (viewType == TYPE_FOLDER) R.layout.mochipic_explorer_folder else R.layout.mochipic_explorer_media
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val v = holder.itemView
            when (val e = items[position]) {
                is Entry.Folder -> bindFolder(v, e)
                is Entry.Media -> bindMedia(v, e)
            }
        }

        private fun bindFolder(v: View, e: Entry.Folder) {
            val text = getProperTextColor()
            val thumb = v.findViewById<ImageView>(R.id.explorer_folder_thumb)
            val name = v.findViewById<TextView>(R.id.explorer_folder_name)
            val info = v.findViewById<TextView>(R.id.explorer_folder_info)
            name.text = AlbumMosaic.label(e.name, if (e.mediaCount > 0) e.mediaCount else -1, text)
            name.setTextColor(text)
            info.setTextColor(text.adjustAlpha(0.6f))
            info.text = if (e.isVolume) e.path else e.modified.formatDate(this@ExplorerActivity)
            v.findViewById<View>(R.id.explorer_folder_divider).setBackgroundColor(text.adjustAlpha(0.12f))

            Glide.with(this@ExplorerActivity).clear(thumb)
            if (e.thumb != null) {
                thumb.clearColorFilter()
                thumb.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(this@ExplorerActivity).load(e.thumb).centerCrop().into(thumb)
            } else {
                thumb.scaleType = ImageView.ScaleType.CENTER
                thumb.setImageResource(if (e.isVolume) R.drawable.mochipic_ic_storage else org.fossify.commons.R.drawable.ic_folder_vector)
                thumb.applyColorFilter(text.adjustAlpha(0.7f))
            }
            v.setOnClickListener { open(e.path) }
        }

        private fun bindMedia(v: View, e: Entry.Media) {
            val thumb = v.findViewById<ImageView>(R.id.explorer_media_thumb)
            Glide.with(this@ExplorerActivity).load(e.path).centerCrop().into(thumb)
            v.findViewById<View>(R.id.explorer_media_play).beVisibleIf(e.path.isVideoFast())
            v.setOnClickListener { openMedia(e.path) }
        }
    }

    companion object {
        private const val TYPE_FOLDER = 0
        private const val TYPE_MEDIA = 1
        private const val MENU_ALBUMS = 1001
        private const val MENU_SETTINGS = 1002
        private const val STATE_PATH = "mochipic_explorer_path"
        private const val ROOT_KEY = "\u0000root"
        /** 「これ以上、上の階層がない」の印（null は「ストレージの一覧」を表すので別の値にする）。 */
        private const val NO_PARENT = "\u0000none"
    }
}
