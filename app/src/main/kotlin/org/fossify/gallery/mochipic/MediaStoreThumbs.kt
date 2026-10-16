package org.fossify.gallery.mochipic

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.util.Size
import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.bumptech.glide.signature.ObjectKey
import java.io.FileNotFoundException

/**
 * MochiPic: 一覧のサムネイルを Android が持っているサムネイル（MediaStore）から読み込むための目印。
 * 元の画像を開いて縮小するより速い。Android の一覧に載っていない画像（隠しフォルダなど）は読み込めないので、
 * 呼び出し側で元の画像からの読み込みに切り替える。
 */
data class MediaStoreThumb(val path: String) {
    companion object {
        val isSupported get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }
}

class MediaStoreThumbLoader(private val context: Context) : ModelLoader<MediaStoreThumb, Bitmap> {

    override fun buildLoadData(model: MediaStoreThumb, width: Int, height: Int, options: Options): ModelLoader.LoadData<Bitmap> =
        ModelLoader.LoadData(ObjectKey(model), Fetcher(context, model.path, width, height))

    override fun handles(model: MediaStoreThumb) = MediaStoreThumb.isSupported

    class Factory(private val context: Context) : ModelLoaderFactory<MediaStoreThumb, Bitmap> {
        override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<MediaStoreThumb, Bitmap> =
            MediaStoreThumbLoader(context.applicationContext)

        override fun teardown() {}
    }

    companion object {
        private const val TIMEOUT_MS = 3000L
    }

    private class Fetcher(
        private val context: Context,
        private val path: String,
        private val width: Int,
        private val height: Int,
    ) : DataFetcher<Bitmap> {
        private val signal = CancellationSignal()

        override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in Bitmap>) {
            if (!MediaStoreThumb.isSupported) {
                callback.onLoadFailed(UnsupportedOperationException())
                return
            }
            try {
                val uri = findUri() ?: throw FileNotFoundException(path)
                // 切り抜いても粗くならないよう、表示の大きさの 2 倍で頼む（大きすぎる指定は 1024 まで）
                val side = (maxOf(width, height).takeIf { it > 0 } ?: 256).times(2).coerceIn(128, 1024)
                // 時間がかかりすぎるときはあきらめて、元の画像から作る
                val timeout = android.os.Handler(android.os.Looper.getMainLooper())
                val giveUp = Runnable { signal.cancel() }
                timeout.postDelayed(giveUp, TIMEOUT_MS)
                val bitmap = try {
                    context.contentResolver.loadThumbnail(uri, Size(side, side), signal)
                } finally {
                    timeout.removeCallbacks(giveUp)
                }
                callback.onDataReady(bitmap)
            } catch (e: Exception) {
                callback.onLoadFailed(e)
            }
        }

        private fun findUri(): android.net.Uri? {
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE)
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"), projection,
                "${MediaStore.Files.FileColumns.DATA} = ?", arrayOf(path), null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val id = cursor.getLong(0)
                return when (cursor.getInt(1)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> ContentUris.withAppendedId(MediaStore.Images.Media.getContentUri("external"), id)
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> ContentUris.withAppendedId(MediaStore.Video.Media.getContentUri("external"), id)
                    else -> null
                }
            }
            return null
        }

        override fun cleanup() {}

        override fun cancel() = signal.cancel()

        override fun getDataClass() = Bitmap::class.java

        override fun getDataSource() = DataSource.LOCAL
    }
}
