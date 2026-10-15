package org.fossify.gallery.mochipic

import org.fossify.gallery.R

/**
 * MochiPic: 設定の分類（QP-Gallery と同じ「分類の一覧 → 分類ごとの画面」）。
 * Fossify の 1 枚の長い設定画面を、項目の ID ごとにこの分類へ振り分けて見せる。
 * 項目をどの分類に置くかを変えるときは、下の [SECTION_OF] と [ITEM_OVERRIDE] だけ直せばよい。
 */
enum class SettingsCategory(val key: String, val titleRes: Int, val summaryRes: Int, val iconRes: Int) {
    INTERFACE("interface", R.string.mochipic_cat_interface, R.string.mochipic_cat_interface_summary, R.drawable.mochipic_ic_cat_interface),
    ALBUM("album", R.string.mochipic_cat_album, R.string.mochipic_cat_album_summary, R.drawable.mochipic_ic_albums),
    VIEWER("viewer", R.string.mochipic_cat_viewer, R.string.mochipic_cat_viewer_summary, R.drawable.mochipic_ic_cat_viewer),
    SECURITY("security", R.string.mochipic_cat_security, R.string.mochipic_cat_security_summary, R.drawable.mochipic_ic_cat_security),
    SLIDESHOW("slideshow", R.string.mochipic_cat_slideshow, R.string.mochipic_cat_slideshow_summary, R.drawable.mochipic_ic_cat_slideshow),
    CACHE("cache", R.string.mochipic_cat_cache, R.string.mochipic_cat_cache_summary, R.drawable.mochipic_ic_cat_cache),
    BACKUP("backup", R.string.mochipic_cat_backup, R.string.mochipic_cat_backup_summary, R.drawable.mochipic_ic_cat_backup);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }

        /** 見出し（settings_holder の直下にある見出しの ID）→ その見出し以降の分類。 */
        val SECTION_OF = mapOf(
            "settings_color_customization_section_label" to INTERFACE,
            "settings_general_settings_label" to ALBUM,
            "settings_videos_label" to VIEWER,
            "settings_thumbnails_label" to ALBUM,
            "settings_scrolling_label" to ALBUM,
            "settings_fullscreen_media_label" to VIEWER,
            "settings_deep_zoomable_images_label" to VIEWER,
            "settings_extended_details_label" to VIEWER,
            "settings_security_label" to SECURITY,
            "settings_file_operations_label" to ALBUM,
            "settings_bottom_actions_label" to INTERFACE,
            "settings_recycle_bin_label" to ALBUM,
            "settings_mochi_slideshow_label" to SLIDESHOW,
            "settings_migrating_label" to BACKUP,
        )

        /** 見出しの分類と違う場所に置きたい項目。 */
        val ITEM_OVERRIDE = mapOf(
            "settings_purchase_thank_you_holder" to INTERFACE,
            "settings_use_english_holder" to INTERFACE,
            "settings_language_holder" to INTERFACE,
            "settings_change_date_time_format_holder" to INTERFACE,
            "settings_clear_cache_holder" to CACHE,
        )
    }
}
