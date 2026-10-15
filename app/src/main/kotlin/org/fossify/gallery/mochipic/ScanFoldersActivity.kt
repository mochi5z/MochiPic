package org.fossify.gallery.mochipic

import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.adapters.ManageFoldersAdapter
import org.fossify.gallery.databinding.ActivityManageFoldersBinding

/**
 * MochiPic: 探索フォルダー（QP-Gallery と同じ）。ここに入れたフォルダの中だけから画像を探す。
 * 何も入れていなければストレージ全体を探す。フォルダはシステムのエクスプローラーで選ぶ。
 */
class ScanFoldersActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityManageFoldersBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.manageFoldersToolbar.title = getString(R.string.mochipic_scan_folders)
        binding.manageFoldersToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.add_folder) {
                pickFolderWithSystem { path ->
                    mochiPrefs.scanFolders = mochiPrefs.scanFolders + path.trimEnd('/')
                    changed()
                }
                true
            } else {
                false
            }
        }
        setupEdgeToEdge(
            padTopSystem = listOf(binding.manageFoldersAppbar),
            padBottomSystem = listOf(binding.manageFoldersList)
        )
        setupMaterialScrollListener(binding.manageFoldersList, binding.manageFoldersAppbar)
        updateFolders()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.manageFoldersAppbar, NavigationIcon.Arrow)
    }

    override fun refreshItems() = updateFolders()

    private fun updateFolders() {
        val folders = ArrayList(mochiPrefs.scanFolders.sortedWith(String.CASE_INSENSITIVE_ORDER))
        binding.manageFoldersPlaceholder.apply {
            beVisibleIf(folders.isEmpty())
            setTextColor(getProperTextColor())
            text = getString(R.string.mochipic_scan_folders_empty)
        }
        binding.manageFoldersList.adapter = ManageFoldersAdapter(this, folders, false, this, binding.manageFoldersList, onRemove = { removed ->
            mochiPrefs.scanFolders = mochiPrefs.scanFolders - removed
            changed()
        }) {}
    }

    /** 一覧の表示は読み込み時の絞り込み（ScanRules）で変わるので、ここでは並びを出し直すだけ。 */
    private fun changed() = updateFolders()
}
