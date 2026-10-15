package org.fossify.gallery.mochipic

/**
 * MochiPic: 表示画面の下部に出す詳細情報の追加項目。
 * QP-Gallery と同じく「解像度 ・ 日時」を 1 行で出す（日時は EXIF の撮影日時、無ければ更新日時）。
 * Fossify の EXT_* と重ならないビットを使う。
 */
const val EXT_MOCHI_SUMMARY = 4096

object ViewerDetails {
    fun summary(resolution: String?, dateTaken: String, lastModified: String): String =
        listOf(resolution.orEmpty(), dateTaken.ifEmpty { lastModified })
            .filter { it.isNotBlank() }
            .joinToString("  ・  ")
}
