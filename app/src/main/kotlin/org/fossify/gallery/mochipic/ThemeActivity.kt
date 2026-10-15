package org.fossify.gallery.mochipic

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.databinding.ActivityMochiThemeBinding

/**
 * MochiPic: テーマの選択（QP-Gallery と同じ）。色の見本だけを 2 列に並べ、押すとすぐに適用する。
 * 見本の上半分が上部バーの色、下半分が背景の色。左の列は背景が黒、右の列は濃い灰色。
 * 色を足すときは [THEMES] に足すだけ。
 */
class ThemeActivity : SimpleActivity() {

    class Theme(val bar: Int, val background: Int)

    private val binding by lazy { ActivityMochiThemeBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padTopSystem = listOf(binding.themeAppbar), padBottomSystem = listOf(binding.themeList))
        binding.themeList.layoutManager = GridLayoutManager(this, 2)
        binding.themeList.adapter = Adapter()
        // 選択中の色が見える位置から始める
        THEMES.indexOfFirst { isCurrent(it) }.takeIf { it > 0 }?.let { binding.themeList.scrollToPosition(it) }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.themeAppbar, NavigationIcon.Arrow)
    }

    private fun isCurrent(t: Theme) = baseConfig.primaryColor == t.bar && baseConfig.backgroundColor == t.background

    private fun apply(t: Theme) {
        baseConfig.apply {
            isSystemThemeEnabled = false
            primaryColor = t.bar
            accentColor = t.bar
            backgroundColor = t.background
            textColor = TEXT
        }
        // 開き直して、上部バーなどの色を新しいテーマにする（recreate だと上部バーの位置がずれることがある）
        startActivity(Intent(this, ThemeActivity::class.java))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = THEMES.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.mochipic_theme_swatch, parent, false)
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val t = THEMES[position]
            val v = holder.itemView
            v.findViewById<View>(R.id.swatch_bar).setBackgroundColor(t.bar)
            v.findViewById<View>(R.id.swatch_background).setBackgroundColor(t.background)
            v.findViewById<View>(R.id.swatch_check).beVisibleIf(isCurrent(t))
            v.setOnClickListener { if (!isCurrent(t)) apply(t) }
        }
    }

    companion object {
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val BLACK = 0xFF000000.toInt()
        private const val DARK_GRAY = 0xFF212226.toInt()

        /** QP-Gallery の見本の色（画面写真から読み取った値）。 */
        private val BAR_COLORS = listOf(
            0xFFEE9734, 0xFFEB7830, 0xFFE9502A, 0xFFC1281F, 0xFFB52A63, 0xFFDE467D,
            0xFFBE3BF0, 0xFF9C1CF0, 0xFF7653EF, 0xFF570FE0,
            0xFF3963F5, 0xFF578AF5, 0xFF489BDD, 0xFF48A1BC, 0xFF4294A6, 0xFF429186,
            0xFF50AE9B, 0xFF51B47A, 0xFF52AE55,
        ).map { it.toInt() }

        val THEMES: List<Theme> = listOf(
            Theme(0xFF4A4A4A.toInt(), 0xFF555555.toInt()),
            Theme(0xFF54473A.toInt(), 0xFF1A1512.toInt()),
            Theme(0xFF1A1512.toInt(), 0xFF1A1512.toInt()),
            Theme(BLACK, BLACK),
        ) + BAR_COLORS.flatMap { listOf(Theme(it, BLACK), Theme(it, DARK_GRAY)) }
    }
}
