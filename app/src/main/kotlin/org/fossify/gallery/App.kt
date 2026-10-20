package org.fossify.gallery

import com.github.ajalt.reprint.core.Reprint
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.Request
import okhttp3.Response
import org.fossify.commons.FossifyApp

class App : FossifyApp() {

    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        org.fossify.gallery.mochipic.ScanRules.load(this)
        // MochiPic: 前回までに調べたフォルダの「実際の場所」を裏で読んでおく
        org.fossify.commons.helpers.ensureBackgroundThread { org.fossify.gallery.mochipic.LoadCache.loadDistinct(this) }
        Reprint.initialize(this)
        Picasso.setSingletonInstance(Picasso.Builder(this).downloader(object : Downloader {
            override fun load(request: Request) = Response.Builder().build()

            override fun shutdown() {}
        }).build())
    }
}
