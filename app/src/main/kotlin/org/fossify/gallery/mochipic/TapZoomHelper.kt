package org.fossify.gallery.mochipic

import android.content.Context
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent

/**
 * MochiPic: 「シングルタップで拡大」用のタップ判定。
 *
 * 画像の表示部品（GestureViews と Subsampling Scale Image View）は、それぞれ自分でダブルタップ拡大を行う。
 * この設定がオンのときは、2 回目のタップを部品に渡す前にここで受け取り、
 *  ・2 回タップ → [onDoubleTap]（ツールバーの表示切り替え）
 *  ・1 回タップ → 部品の「クリック」として届くので、呼び出し側で拡大・縮小する
 * 2 回タップの 1 回目も、少し遅れて部品から「クリック」として届くので、[shouldIgnoreClick] で捨てる。
 */
class TapZoomHelper(context: Context, private val onDoubleTap: () -> Unit) {
    var lastX = 0f
        private set
    var lastY = 0f
        private set

    private var consuming = false
    private var ignoreClickUntil = 0L

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            consuming = true
            ignoreClickUntil = SystemClock.uptimeMillis() + IGNORE_MS
            onDoubleTap()
            return true
        }
    })

    /** タッチを受け取る。true を返したら、そのタッチは表示部品に渡さない。 */
    fun onTouch(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            lastX = event.x
            lastY = event.y
        }
        detector.onTouchEvent(event)
        if (consuming) {
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                consuming = false
            }
            return true
        }
        return false
    }

    fun shouldIgnoreClick() = SystemClock.uptimeMillis() < ignoreClickUntil

    companion object {
        private const val IGNORE_MS = 800L
    }
}
