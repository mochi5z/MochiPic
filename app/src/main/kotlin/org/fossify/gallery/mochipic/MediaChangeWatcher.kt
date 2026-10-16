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
 */
class MediaChangeWatcher(private val context: Context, private val onChange: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var firstPending = 0L
    private var started = false

    private val fire = Runnable {
        firstPending = 0L
        if (started) onChange()
    }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = schedule()
        override fun onChange(selfChange: Boolean, uri: Uri?) = schedule()
    }

    private fun schedule() {
        if (!started) return
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
    }
}
