package org.fossify.gallery.svg

import android.content.Context
import android.graphics.drawable.PictureDrawable

import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.caverock.androidsvg.SVG

import java.io.InputStream

@GlideModule
class SvgModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.register(SVG::class.java, PictureDrawable::class.java, SvgDrawableTranscoder()).append(InputStream::class.java, SVG::class.java, SvgDecoder())
        // MochiPic: 一覧のサムネイルは Android が持っているサムネイルから読む
        registry.prepend(
            org.fossify.gallery.mochipic.MediaStoreThumb::class.java,
            android.graphics.Bitmap::class.java,
            org.fossify.gallery.mochipic.MediaStoreThumbLoader.Factory(context)
        )
    }

    override fun isManifestParsingEnabled() = false
}
