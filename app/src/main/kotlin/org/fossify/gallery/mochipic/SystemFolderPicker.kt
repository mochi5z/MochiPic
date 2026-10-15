package org.fossify.gallery.mochipic

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.fossify.commons.extensions.internalStoragePath

/**
 * MochiPic: Android のファイル選択画面（システムのエクスプローラー）でフォルダを選ぶ（QP-Gallery と同じ）。
 * 選択画面の右上から新しいフォルダも作れる。SD カードは左上のメニューから選ぶ。
 */
object SystemFolderPicker {
    const val REQUEST_CODE = 0x4D51

    fun intent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        putExtra("android.content.extra.SHOW_ADVANCED", true)  // 内部ストレージも選べるようにする
    }

    /** 選んだフォルダの場所（/storage/...）。内部ストレージ・SD カード以外（クラウドなど）なら null。 */
    fun pathOf(context: Context, uri: Uri?): String? {
        if (uri == null || uri.authority != "com.android.externalstorage.documents") return null
        val docId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            return null
        }
        val volume = docId.substringBefore(':')
        val relative = docId.substringAfter(':', "").trim('/')
        val base = when {
            volume.equals("primary", ignoreCase = true) -> context.internalStoragePath.trimEnd('/')
            volume.equals("home", ignoreCase = true) -> context.internalStoragePath.trimEnd('/') + "/Documents"
            else -> "/storage/$volume"
        }
        return if (relative.isEmpty()) base else "$base/$relative"
    }
}
