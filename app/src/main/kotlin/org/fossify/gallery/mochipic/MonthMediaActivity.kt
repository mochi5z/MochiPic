package org.fossify.gallery.mochipic

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.databinding.ActivityMochiExplorerBinding
import org.fossify.gallery.extensions.config

/** MochiPic: 日付表示で月を開いた画面。「← 2026年10月 (枚数)  [スライドショー][︙]」と 3 列のサムネイル。 */
class MonthMediaActivity : SimpleActivity() {

    private lateinit var binding: ActivityMochiExplorerBinding
    private var items: List<String> = emptyList()
    private val adapter = MediaGridAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMochiExplorerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val bar = binding.explorerMenu
        bar.setupMenu()
        QpTopBar.install(this, bar, "", nav = QpTopBar.Nav.BACK, withSearch = false) { finish() }
        bar.requireToolbar().menu.add(Menu.NONE, MENU_SLIDESHOW, 0, R.string.slideshow).apply {
            setIcon(R.drawable.mochipic_ic_cat_slideshow)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        bar.requireToolbar().setOnMenuItemClickListener { item ->
            if (item.itemId == MENU_SLIDESHOW) items.firstOrNull()?.let { DateViewer.open(this, it, slideshow = true) }
            true
        }
        setupEdgeToEdge(
            padTopSystem = listOf(bar),
            padBottomImeAndSystem = listOf(binding.explorerList),
        )
        binding.explorerList.layoutManager = GridLayoutManager(this, config.mediaColumnCnt.coerceAtLeast(1))
        binding.explorerList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        config.showAll = false
        binding.explorerMenu.updateColors()
        QpTopBar.updateColors(this, binding.explorerMenu)
        val key = intent.getStringExtra(EXTRA_MONTH) ?: return finish()
        ensureBackgroundThread {
            val month = DateIndex.load(applicationContext).firstOrNull { it.key == key }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (month == null) {
                    finish()
                    return@runOnUiThread
                }
                items = month.items
                QpTopBar.setTitle(binding.explorerMenu, "${month.title} (${month.items.size})")
                adapter.notifyDataSetChanged()
            }
        }
    }

    private inner class MediaGridAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.mochipic_explorer_media, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val path = items[position]
            val v = holder.itemView
            Glide.with(this@MonthMediaActivity).load(path).centerCrop().into(v.findViewById<ImageView>(R.id.explorer_media_thumb))
            v.findViewById<View>(R.id.explorer_media_play).beVisibleIf(path.isVideoFast())
            v.setOnClickListener { DateViewer.open(this@MonthMediaActivity, path) }
        }
    }

    companion object {
        const val EXTRA_MONTH = "mochipic_month"
        private const val MENU_SLIDESHOW = 1000
    }
}
