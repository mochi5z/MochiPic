package org.fossify.gallery.mochipic

import android.app.Activity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.R

/** MochiPic: 「このアプリについて」。QP-Gallery と同じく、アプリ名・アイコン・バージョンだけを出す。 */
object MochiAbout {
    fun show(activity: Activity) {
        val view = LayoutInflater.from(activity).inflate(R.layout.mochipic_about, null)
        view.findViewById<ImageView>(R.id.about_icon).setImageResource(R.mipmap.ic_launcher)
        view.findViewById<TextView>(R.id.about_name).setText(R.string.app_launcher_name)
        view.findViewById<TextView>(R.id.about_version).text = activity.getString(R.string.mochipic_about_version, BuildConfig.VERSION_NAME)
        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .apply { activity.setupDialogStuff(view, this) }
    }
}
