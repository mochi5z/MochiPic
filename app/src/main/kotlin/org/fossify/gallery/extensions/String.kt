package org.fossify.gallery.extensions

import android.os.Environment
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.commons.helpers.isRPlus
import java.io.File
import java.io.IOException
import java.util.Locale

fun String.isThisOrParentIncluded(includedPaths: MutableSet<String>) =
    includedPaths.any { equals(it, true) } || includedPaths.any { "$this/".startsWith("$it/", true) }

fun String.isThisOrParentExcluded(excludedPaths: MutableSet<String>) =
    excludedPaths.any { equals(it, true) } || excludedPaths.any { "$this/".startsWith("$it/", true) }

// cache which folders contain .nomedia files to avoid checking them over and over again
fun String.shouldFolderBeVisible(
    excludedPaths: MutableSet<String>, includedPaths: MutableSet<String>, showHidden: Boolean,
    folderNoMediaStatuses: HashMap<String, Boolean>, callback: (path: String, hasNoMedia: Boolean) -> Unit
): Boolean {
    if (isEmpty()) {
        return false
    }

    // MochiPic: 探索フォルダー・アプリ用フォルダの除外（Fossify の「読み込むフォルダ」に入れたものは除く）
    if (!isThisOrParentIncluded(includedPaths) && !org.fossify.gallery.mochipic.ScanRules.isAllowed(this)) {
        return false
    }

    val file = File(this)
    val filename = file.name
    if (filename.startsWith("img_", true) && file.isDirectory) {
        val files = file.list()
        if (files != null) {
            if (files.any { it.contains("burst", true) }) {
                return false
            }
        }
    }

    if (!showHidden && filename.startsWith('.')) {
        return false
    } else if (includedPaths.contains(this)) {
        return true
    }

    val containsNoMedia = if (showHidden) {
        false
    } else {
        folderNoMediaStatuses.getOrElse("$this/$NOMEDIA") { false } || ((!isRPlus() || isExternalStorageManager()) && File(this, NOMEDIA).exists())
    }

    return if (!showHidden && containsNoMedia) {
        false
    } else if (excludedPaths.contains(this)) {
        false
    } else if (isThisOrParentIncluded(includedPaths)) {
        true
    } else if (isThisOrParentExcluded(excludedPaths)) {
        false
    } else if (!showHidden) {
        var containsNoMediaOrDot = containsNoMedia || contains("/.")
        if (!containsNoMediaOrDot) {
            var curPath = this
            for (i in 0 until count { it == '/' } - 1) {
                curPath = curPath.substringBeforeLast('/')
                val pathToCheck = "$curPath/$NOMEDIA"
                if (folderNoMediaStatuses.contains(pathToCheck)) {
                    if (folderNoMediaStatuses[pathToCheck] == true) {
                        containsNoMediaOrDot = true
                        break
                    }
                } else {
                    val noMediaExists = folderNoMediaStatuses.getOrElse(pathToCheck, { false }) || File(pathToCheck).exists()
                    callback(pathToCheck, noMediaExists)
                    if (noMediaExists) {
                        containsNoMediaOrDot = true
                        break
                    }
                }
            }
        }
        !containsNoMediaOrDot
    } else {
        true
    }
}

// recognize /sdcard/DCIM as the same folder as /storage/emulated/0/DCIM
fun String.getDistinctPath(): String {
    // MochiPic: 一覧を作り直すたびにファイルシステムへ問い合わせないよう、結果を覚えておく
    distinctPathCache[this]?.let { return it }
    val distinct = try {
        File(this).canonicalPath.lowercase(Locale.getDefault())
    } catch (e: IOException) {
        lowercase(Locale.getDefault())
    }
    if (distinctPathCache.size > 5000) distinctPathCache.clear()
    distinctPathCache[this] = distinct
    return distinct
}

private val distinctPathCache = java.util.concurrent.ConcurrentHashMap<String, String>()

fun String.isDownloadsFolder() = equals(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString(), true)

fun String.isThisOrParentFolderHidden(): Boolean {
    var curFile = File(this)
    while (true) {
        if (curFile.isHidden) {
            return true
        }

        curFile = curFile.parentFile ?: break
        if (curFile.absolutePath == "/") {
            break
        }
    }
    return false
}
