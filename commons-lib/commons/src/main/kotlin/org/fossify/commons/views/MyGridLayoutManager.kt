package org.fossify.commons.views

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

open class MyGridLayoutManager : GridLayoutManager {
    constructor(context: Context, spanCount: Int) : super(context, spanCount)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, spanCount: Int, orientation: Int, reverseLayout: Boolean) : super(context, spanCount, orientation, reverseLayout)

    // fixes crash java.lang.IndexOutOfBoundsException: Inconsistency detected...
    // taken from https://stackoverflow.com/a/33985508/1967672
    override fun supportsPredictiveItemAnimations() = false

    // MochiPic: 画面の外（画面の半分）の項目も先に用意して、サムネイルを先読みする。
    // v0.1.2: 先読みはスクロールしている方向だけ（戻る方向の分は読まない。端末の負担を減らすため）。最初は下（先）方向
    private var scrollingBack = false

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?): Int {
        if (dy != 0) scrollingBack = dy < 0
        return super.scrollVerticallyBy(dy, recycler, state)
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: RecyclerView.Recycler?, state: RecyclerView.State?): Int {
        if (dx != 0) scrollingBack = dx < 0
        return super.scrollHorizontallyBy(dx, recycler, state)
    }

    override fun calculateExtraLayoutSpace(state: RecyclerView.State, extraLayoutSpace: IntArray) {
        super.calculateExtraLayoutSpace(state, extraLayoutSpace)
        val extra = (if (orientation == VERTICAL) height else width) / 2
        val side = if (scrollingBack) 0 else 1  // 0 = 前（上・左）、1 = 先（下・右）
        extraLayoutSpace[side] = maxOf(extraLayoutSpace[side], extra)
    }
}
