package org.fossify.gallery.mochipic

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.otgPath
import org.fossify.commons.extensions.sdCardPath
import org.fossify.gallery.extensions.directoryDB
import org.fossify.gallery.extensions.favoritesDB
import org.fossify.gallery.extensions.mediaDB
import java.io.File

/**
 * MochiPic: フォルダを「中の画像を 1 枚ずつ」ではなく「フォルダごと」削除する（2026-11-03、v0.1.10）。
 *
 * 以前は、選んだフォルダの画像を 1 枚ずつアプリ内のごみ箱へコピーしてから消していた。枚数が多いと遅く、
 * コピー中に同じファイルが二重に処理されると「Source file wasn't copied completely」で止まっていた。
 * フォルダごと削除はごみ箱を通さない（完全に削除。確認画面でそう伝える）。
 *
 * フォルダごと削除できない場合（全ファイルへのアクセスが無い、ストレージ直下の標準フォルダ）は
 * [canDeleteWhole] が false を返し、呼び出し側は従来の「画像だけ削除」に回す。
 */
object FolderDelete {
    /** ストレージ直下にある Android 標準のフォルダ。中にアプリのデータや画像以外の物が多いので、丸ごとは消さない。 */
    private val STANDARD_TOP = setOf(
        "dcim", "download", "downloads", "pictures", "movies", "music", "documents", "podcasts",
        "ringtones", "alarms", "notifications", "audiobooks", "recordings", "screenshots", "android",
    )

    /** 全ファイルへのアクセス（Android 11 以上は「すべてのファイルへのアクセス」）があるか。 */
    fun hasFileAccess(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private fun roots(context: Context): List<String> =
        listOf(context.internalStoragePath, context.sdCardPath, context.otgPath)
            .filter { it.isNotEmpty() }.map { it.trimEnd('/') }

    /** ストレージそのもの、またはストレージ直下の標準フォルダなら true（丸ごと削除しない）。 */
    fun isProtected(context: Context, path: String): Boolean = isProtectedPath(roots(context), path)

    /** [isProtected] の本体（単体テスト用に Context を使わない形）。 */
    fun isProtectedPath(roots: List<String>, path: String): Boolean {
        val p = path.trimEnd('/')
        for (root in roots) {
            if (p.equals(root, ignoreCase = true)) return true
            if (p.startsWith("$root/", ignoreCase = true)) {
                val rest = p.substring(root.length + 1)
                if (!rest.contains('/') && rest.lowercase() in STANDARD_TOP) return true
            }
        }
        // 想定外の浅い場所（/、/storage、/storage/emulated など）も丸ごとは消さない
        return p.count { it == '/' } <= 2
    }

    /** このフォルダを丸ごと削除してよいか。 */
    fun canDeleteWhole(context: Context, path: String): Boolean =
        hasFileAccess() && !isProtected(context, path) && File(path).isDirectory

    /**
     * フォルダを丸ごと削除する（バックグラウンドのスレッドで呼ぶ）。サブフォルダとその中身も消える。
     * アプリのデータベース（画像・フォルダ・お気に入り）と Android のメディアの一覧からも消す。
     * @return 消せなかったファイル・フォルダの数（0 なら全部消えた）
     */
    fun deleteWhole(context: Context, path: String): Int {
        val root = File(path)
        val dirs = ArrayList<String>()
        val files = ArrayList<String>()
        root.walkBottomUp().forEach { if (it.isDirectory) dirs.add(it.absolutePath) else files.add(it.absolutePath) }

        var failed = 0
        for (f in files) {
            if (!File(f).delete() && File(f).exists()) failed++
        }
        for (d in dirs) {                     // 深い順（walkBottomUp）なので中身が空になってから消える
            if (!File(d).delete() && File(d).exists()) failed++
        }

        // Android のメディアの一覧（Android 11 以上はファイルを消すと自動で消えるが、古い機種や取り残し用に）
        try {
            val esc = path.trimEnd('/').replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            context.contentResolver.delete(
                MediaStore.Files.getContentUri("external"),
                "${MediaStore.MediaColumns.DATA} LIKE ? ESCAPE '\\'",
                arrayOf("$esc/%"),
            )
        } catch (ignored: Exception) {
        }

        // アプリのデータベース
        try {
            files.forEach {
                context.mediaDB.deleteMediumPath(it)
                context.favoritesDB.deleteFavoritePath(it)
            }
            dirs.forEach { context.directoryDB.deleteDirPath(it) }
        } catch (ignored: Exception) {
        }
        return failed
    }
}
