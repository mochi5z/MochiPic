package org.fossify.gallery.mochipic

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.gallery.R
import org.fossify.gallery.activities.SettingsActivity
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.databinding.ActivityMochiSettingsTopBinding
import org.fossify.gallery.extensions.launchAbout

/** MochiPic: 設定のいちばん上の画面（分類の一覧）。分類を選ぶと [SettingsActivity] がその分類の項目だけを出す。 */
class SettingsCategoriesActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityMochiSettingsTopBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(
            padTopSystem = listOf(binding.settingsTopAppbar),
            padBottomSystem = listOf(binding.settingsTopScroll)
        )
        setupMaterialScrollListener(binding.settingsTopScroll, binding.settingsTopAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsTopAppbar, NavigationIcon.Arrow)
        val text = getProperTextColor()
        val inflater = LayoutInflater.from(this)
        val list = binding.settingsTopList
        list.removeAllViews()

        fun addRow(titleRes: Int, summaryRes: Int, iconRes: Int, onClick: () -> Unit) {
            val row = inflater.inflate(R.layout.mochipic_settings_category_item, list, false)
            row.findViewById<ImageView>(R.id.category_icon).apply { setImageResource(iconRes); applyColorFilter(text) }
            row.findViewById<TextView>(R.id.category_title).apply { setText(titleRes); setTextColor(text) }
            row.findViewById<TextView>(R.id.category_summary).apply { setText(summaryRes); setTextColor(text.adjustAlpha(0.6f)) }
            row.setOnClickListener { onClick() }
            list.addView(row)
        }

        SettingsCategory.entries.forEach { cat ->
            addRow(cat.titleRes, cat.summaryRes, cat.iconRes) {
                startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_CATEGORY, cat.key))
            }
        }
        addRow(R.string.mochipic_cat_about, R.string.mochipic_cat_about_summary, R.drawable.mochipic_ic_cat_about) { launchAbout() }
    }
}
