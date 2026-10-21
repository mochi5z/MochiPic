package org.fossify.gallery.mochipic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.AlphanumericComparator
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.databinding.ActivityMochiExplorerBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getCachedDirectories
import org.fossify.gallery.extensions.movePinnedDirectoriesToFront
import org.fossify.gallery.models.Directory
import java.io.File

/**
 * MochiPic: コピー・移動先を選ぶ画面（QP-Gallery と同じ）。
 *  - 上部バーは「← コピー：」または「← 移動：」と [並べ替え][＋][︙]
 *  - アルバムをリストで並べる（表紙・名前 枚数・場所・日付）。初めは新しい順
 *  - アルバムを押すと、確認なしですぐにコピー・移動を始める
 *  - ＋ はシステムのエクスプローラーを開く（新しいフォルダも作れる。SD カードも選べる）。選んだらすぐに始める
 *  - ︙ の中は隠しフォルダの表示切り替えだけ
 *  - ピン留めしたアルバムは、アルバム一覧と同じく並べ替えに関係なく一番上
 */
class CopyTargetActivity : SimpleActivity() {

    private lateinit var binding: ActivityMochiExplorerBinding
    private lateinit var adapter: FolderTileAdapter
    private var source = ""
    private var dirs: List<Pair<Directory, Boolean>> = emptyList()  // アルバムと、隠しフォルダか
    private var showHidden = true
    private var delivered = false
    private var deliveredPath: String? = null  // 選んだアルバム。次に開いたときの表示位置の目印にはしない（移動先は上に移るため）
    private var scrollRestored = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (pending == null) {  // アプリが作り直されて、コピー・移動の続きがなくなった
            finish()
            return
        }
        binding = ActivityMochiExplorerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        source = intent.getStringExtra(EXTRA_SOURCE).orEmpty().trimEnd('/')
        showHidden = mochiPrefs.showHiddenFolders

        setupTopBar(intent.getBooleanExtra(EXTRA_IS_COPY, true))
        setupEdgeToEdge(
            padTopSystem = listOf(binding.explorerMenu),
            padBottomImeAndSystem = listOf(binding.explorerList),
        )
        adapter = FolderTileAdapter(this, mosaic = false, labelAtStart = false, forceList = true) { deliver(it.path) }
        binding.explorerList.layoutManager = LinearLayoutManager(this)
        binding.explorerList.adapter = adapter
        load()
    }

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        binding.explorerMenu.updateColors()
        binding.explorerFastscroller.updateColors(getProperPrimaryColor())
        QpTopBar.updateColors(this, binding.explorerMenu)
        binding.explorerEmpty.setTextColor(getProperTextColor())
    }

    override fun onPause() {
        super.onPause()
        if (::binding.isInitialized) saveScroll()
        // 位置は（開き直しても残る）下の companion に覚える
    }

    /** 一番上に見えているアルバムと、その位置のずれを覚える（選んだアルバム自身は目印にしない）。 */
    private fun saveScroll() {
        val lm = binding.explorerList.layoutManager as? LinearLayoutManager ?: return
        val items = adapter.items
        var pos = lm.findFirstVisibleItemPosition()
        if (pos < 0 || items.isEmpty()) return
        if (items.getOrNull(pos)?.key?.trimEnd('/') == deliveredPath && pos + 1 < items.size) pos++
        val top = lm.findViewByPosition(pos)?.top ?: 0
        anchorKey = items.getOrNull(pos)?.key
        anchorPos = pos
        anchorOffset = if (pos == lm.findFirstVisibleItemPosition()) top - binding.explorerList.paddingTop else 0
    }

    /** 前回の表示位置に戻す（アルバムの並びが変わっていても、目印のアルバムの今の場所へ）。 */
    private fun restoreScroll(items: List<FolderTile>) {
        if (items.isEmpty() || anchorPos < 0) return
        val index = items.indexOfFirst { it.key == anchorKey }.takeIf { it >= 0 } ?: anchorPos.coerceIn(0, items.lastIndex)
        (binding.explorerList.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, anchorOffset)
    }

    override fun onDestroy() {
        if (isFinishing && !delivered) pending = null  // 戻るでやめた
        super.onDestroy()
    }

    private fun setupTopBar(isCopy: Boolean) {
        val bar = binding.explorerMenu
        bar.setupMenu()
        bar.requireToolbar().menu.apply {
            add(Menu.NONE, MENU_SORT, 0, org.fossify.commons.R.string.sort_by).apply {
                setIcon(org.fossify.commons.R.drawable.ic_sort_vector)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            add(Menu.NONE, MENU_NEW, 1, R.string.mochipic_copy_new_folder).apply {
                setIcon(org.fossify.commons.R.drawable.ic_plus_vector)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
            add(Menu.NONE, MENU_HIDDEN, 100, R.string.mochipic_copy_show_hidden).apply {
                isCheckable = true
                isChecked = showHidden
            }
        }
        val title = getString(if (isCopy) R.string.mochipic_copy_to else R.string.mochipic_move_to)
        QpTopBar.install(this, bar, title, nav = QpTopBar.Nav.BACK, withSearch = false) { finish() }
        bar.requireToolbar().setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SORT -> showSortMenu()
                MENU_NEW -> pickFolderWithSystem { deliver(it) }
                MENU_HIDDEN -> {
                    showHidden = !showHidden
                    item.isChecked = showHidden
                    showList()
                }
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    private fun showSortMenu() {
        val anchor = binding.explorerMenu.requireToolbar().findViewById<android.view.View>(MENU_SORT) ?: binding.explorerMenu
        PopupMenu(this, anchor).apply {
            SORTS.forEachIndexed { i, res -> menu.add(Menu.NONE, i, i, res) }
            menu.setGroupCheckable(Menu.NONE, true, true)
            menu.findItem(sortMode)?.isChecked = true
            setOnMenuItemClickListener {
                sortMode = it.itemId
                // ここは PopupMenu.apply の中なので、show() と書くとメニューを開き直すだけで一覧が並び替わらなかった
                showList()
                true
            }
            show()
        }
    }

    private fun load() {
        getCachedDirectories(forceShowHidden = true) { list ->
            val result = list
                .filter { !it.areFavorites() && !it.isRecycleBin() && !it.path.equals(source, ignoreCase = true) }
                .map { it to isHidden(it.path) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                dirs = result
                showList()
            }
        }
    }

    private fun isHidden(path: String): Boolean =
        path.split('/').any { it.startsWith('.') } || File(path, ".nomedia").exists()

    private fun showList() {
        val visible = dirs.filter { showHidden || !it.second }.map { it.first }
        // 名前はアルバム一覧と同じ正準の順（0→9、A→Z。数字は値の大きさで比べる）。同じ日付どうしも名前順
        val byName = Comparator<Directory> { a, b -> AlphanumericComparator().compare(a.name.lowercase(), b.name.lowercase()) }
        val ordered = when (sortMode) {
            SORT_DATE_ASC -> visible.sortedWith(compareBy<Directory> { MoveTouch.effectiveModified(this, it) }.then(byName))
            SORT_NAME_ASC -> visible.sortedWith(byName)
            SORT_NAME_DESC -> visible.sortedWith(byName.reversed())
            else -> visible.sortedWith(compareByDescending<Directory> { MoveTouch.effectiveModified(this, it) }.then(byName))
        }
        // ピン留めしたアルバムを上に（アルバム一覧と同じ処理）
        val sorted = movePinnedDirectoriesToFront(ArrayList(ordered))
        val pinned = config.pinnedFolders
        adapter.items = sorted.map {
            FolderTile(it.path, it.name, it.mediaCnt, it.tmb.takeIf { t -> t.isNotEmpty() }, it.modified, it.path, showDate = true, subtitle = it.path, pinned = pinned.contains(it.path))
        }
        binding.explorerEmpty.beVisibleIf(sorted.isEmpty())
        if (!scrollRestored && sorted.isNotEmpty()) {
            scrollRestored = true
            restoreScroll(adapter.items)
        }
    }

    private fun deliver(path: String) {
        if (delivered) return
        val destination = path.trimEnd('/')
        if (destination.equals(source, ignoreCase = true)) {
            toast(org.fossify.commons.R.string.source_and_destination_same)
            return
        }
        delivered = true
        deliveredPath = destination
        val callback = pending
        pending = null
        finish()
        // 元の画面に戻ってから始める（進み具合や確認の表示は元の画面に出す）
        Handler(Looper.getMainLooper()).postDelayed({ callback?.invoke(destination) }, 150)
    }

    companion object {
        private const val EXTRA_SOURCE = "mochi_source"
        private const val EXTRA_IS_COPY = "mochi_is_copy"
        private const val MENU_SORT = 1
        private const val MENU_NEW = 2
        private const val MENU_HIDDEN = 3

        private const val SORT_DATE_DESC = 0
        private const val SORT_DATE_ASC = 1
        private const val SORT_NAME_ASC = 2
        private const val SORT_NAME_DESC = 3
        private val SORTS = listOf(
            R.string.mochipic_sort_date_desc,
            R.string.mochipic_sort_date_asc,
            R.string.mochipic_sort_name_asc,
            R.string.mochipic_sort_name_desc,
        )

        /** 一覧の表示位置（アプリを閉じるまで覚えておく）。目印のアルバム・その並びの番号・上端からのずれ。 */
        private var anchorKey: String? = null
        private var anchorPos = -1
        private var anchorOffset = 0

        /** 並べ替え（アプリを閉じるまで覚えておく）。初めは新しい順。 */
        private var sortMode = SORT_DATE_DESC

        /** 選んだ先を受け取る処理（元の画面のもの）。 */
        private var pending: ((String) -> Unit)? = null

        fun open(activity: Activity, source: String, isCopy: Boolean, onPicked: (String) -> Unit) {
            pending = onPicked
            activity.startActivity(
                Intent(activity, CopyTargetActivity::class.java)
                    .putExtra(EXTRA_SOURCE, source)
                    .putExtra(EXTRA_IS_COPY, isCopy)
            )
        }
    }
}
