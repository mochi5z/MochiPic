package org.fossify.gallery.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.extensions.updateDirectoryPath
import org.fossify.gallery.helpers.MediaFetcher
import org.fossify.gallery.mochipic.MochiPicPrefs
import org.fossify.gallery.mochipic.mochiPrefs

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MochiPic: 高速方式では起動のたびに全フォルダを読み直さない（次にアプリを開いたときの差分読み込みで足りる）
        if (context.mochiPrefs.loadMode == MochiPicPrefs.LOAD_MEDIASTORE) return
        ensureBackgroundThread {
            MediaFetcher(context).getFoldersToScan().forEach {
                context.updateDirectoryPath(it)
            }
        }
    }
}
