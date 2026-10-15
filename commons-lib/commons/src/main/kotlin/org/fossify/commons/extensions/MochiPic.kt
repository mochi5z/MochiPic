package org.fossify.commons.extensions

import android.content.Context

/**
 * MochiPic: Fossify Commons は、アプリ ID が org.fossify. で始まらないアプリ（改造版）に
 * 警告を出したり一部の機能（SD カードの書き込み許可、テーマ色の選択）を止めたりする。
 * MochiPic は Fossify Gallery の GPL-3.0 に基づくフォークなので、その判定に MochiPic の ID を加える。
 */
fun Context.isFossifyOrMochiPic(): Boolean =
    packageName.startsWith("org.fossify.", true) || packageName.startsWith("com.mochipic.", true)
