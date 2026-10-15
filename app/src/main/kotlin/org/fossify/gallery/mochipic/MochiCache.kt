package org.fossify.gallery.mochipic

import android.app.Activity
import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.fossify.commons.helpers.ensureBackgroundThread
import java.io.File

/**
 * MochiPic: キャッシュの設定（設定 → キャッシュ）の中身。
 * 画像の読み込み部品 Glide は、サムネイルとプレビュー（表示画面の画像）を同じ置き場（cache/image_manager_disk_cache）に保存する。
 *  - プレビューのキャッシュ消去 … Glide の置き場を消す
 *  - サムネイルのキャッシュ消去 … Glide の置き場とメモリ上の画像、モザイク用に覚えた一覧を消す
 *  - アプリ終了時 … それ以外の一時ファイルも含めてキャッシュフォルダ全体を消す
 */
object MochiCache {

    private fun glideDir(context: Context) = File(context.cacheDir, "image_manager_disk_cache")

    fun clearPreviews(activity: Activity, done: () -> Unit) {
        ensureBackgroundThread {
            Glide.get(activity.applicationContext).clearDiskCache()
            activity.runOnUiThread { done() }
        }
    }

    fun clearThumbnails(activity: Activity, done: () -> Unit) {
        Glide.get(activity.applicationContext).clearMemory()
        ensureBackgroundThread {
            Glide.get(activity.applicationContext).clearDiskCache()
            glideDir(activity).deleteRecursively()
            activity.runOnUiThread { done() }
        }
    }

    /** 起動画面（アルバム一覧・エクスプローラー・日付）が閉じられるときに呼ぶ。 */
    fun onAppExit(activity: Activity) {
        if (!activity.isFinishing || !activity.mochiPrefs.clearCacheOnExit) return
        val context = activity.applicationContext
        ensureBackgroundThread {
            try {
                Glide.get(context).clearDiskCache()
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            } catch (ignored: Exception) {
            }
        }
    }

    /** 「画像を遅延読み込みする」がオンなら、勢いよくスクロールしている間はサムネイルの読み込みを止める。 */
    fun attachLazyLoad(activity: Activity, list: RecyclerView) {
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (!activity.mochiPrefs.lazyLoad || activity.isDestroyed) return
                val glide = Glide.with(activity)
                if (newState == RecyclerView.SCROLL_STATE_SETTLING) glide.pauseRequests() else glide.resumeRequests()
            }
        })
    }
}
