package org.fossify.gallery.mochipic

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore

/**
 * MochiPic v0.1.1: Android の画像の一覧（MediaStore）が変わったら知らせる。画面が見えている間だけ動かす（start / stop）。
 *
 * - 通知は「きっかけ」にだけ使い、読み込み自体は差分読み込みに任せる
 * - 最後の通知から [QUIET_MS]（0.2 秒）待ってから 1 回だけ [onChange] を呼ぶ（解凍などで大量に届いてもまとめる）
 * - 通知が途切れなくても、最初の通知から [MAX_WAIT_MS]（1 秒）たったら 1 回は呼ぶ
 * - v0.1.2: まとめた通知が画像・動画以外のファイル（ダウンロード中のファイル、音楽など）だけなら呼ばない
 */
class MediaChangeWatcher(private val context: Context, private val onChange: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var firstPending = 0L
    private var started = false
    private var relevant = false  // まとめている通知に、画像・動画に関係しそうなものがあったか

    private val fire = Runnable {
        firstPending = 0L
        val run = relevant
        relevant = false
        if (started && run) onChange()
    }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = schedule(null)
        override fun onChange(selfChange: Boolean, uri: Uri?) = schedule(uri)
    }

    private fun schedule(uri: Uri?) {
        if (!started) return
        if (isRelevant(uri)) relevant = true
        val now = SystemClock.uptimeMillis()
        if (firstPending == 0L) firstPending = now
        handler.removeCallbacks(fire)
        val delay = minOf(QUIET_MS, (firstPending + MAX_WAIT_MS - now).coerceAtLeast(0L))
        handler.postDelayed(fire, delay)
    }

    fun start() {
        if (started) return
        started = true
        try {
            // content://media の下すべて（内部ストレージも SD カードも）
            context.contentResolver.registerContentObserver(MediaStore.AUTHORITY_URI, true, observer)
        } catch (ignored: Exception) {
        }
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(fire)
        firstPending = 0L
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (ignored: Exception) {
        }
    }

    companion object {
        const val QUIET_MS = 200L
        const val MAX_WAIT_MS = 1000L

        /**
         * 画像・動画に関係しそうな通知か。分からないもの（場所のない通知、一覧全体の通知など）は関係ありとする。
         * 画像・動画が変わると images/video の通知も届くので、file/番号 だけの通知は画像以外のファイルとみなす。
         */
        fun isRelevant(uri: Uri?): Boolean {
            val segments = uri?.pathSegments ?: return true
            if (segments.contains("images") || segments.contains("video")) return true
            if (segments.contains("audio") || segments.contains("downloads") || segments.contains("playlists")) return false
            val last = segments.lastOrNull() ?: return true
            return !(segments.contains("file") && last.all { it.isDigit() })
        }
    }
}
