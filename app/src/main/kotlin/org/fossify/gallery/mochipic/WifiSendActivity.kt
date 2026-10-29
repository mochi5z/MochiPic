package org.fossify.gallery.mochipic

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isMediaFile
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import java.io.File

/**
 * MochiPic: Wi-Fi でスマホから PC へ送る画面（QP-Gallery の「Wi-Fi ファイル送受信」に当たる）。
 * 選択した画像・動画（またはアルバム）を、PC の受信ソフト（tools/pc-receiver）の受信フォルダへ送る。
 * 送信中はこの画面を開いたままにする（画面を閉じると中止）。画面は消えないようにしてある。
 */
class WifiSendActivity : SimpleActivity() {

    private lateinit var infoText: TextView
    private lateinit var pcText: TextView
    private lateinit var hostEdit: EditText
    private lateinit var dirText: TextView
    private lateinit var sendButton: Button
    private lateinit var cancelButton: Button
    private lateinit var progressText: TextView
    private lateinit var progressBar: ProgressBar

    private var pc: WifiTransfer.Pc? = null
    private var dir: String = ""
    private var sources: List<String> = emptyList()
    @Volatile private var cancelled = false
    @Volatile private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        title = getString(R.string.mochipic_menu_wifi)
        sources = intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty()
        dir = mochiPrefs.wifiDir
        buildUi()
        updateInfo()
        savedPc()?.let { saved ->
            pcText.text = getString(R.string.mochipic_wifi_connecting)
            Thread {
                val ok = WifiTransfer.ping(saved.host, saved.port)
                runOnUiThread { if (!isDestroyed) if (ok != null) setPc(ok) else findPc() }
            }.start()
        } ?: findPc()
    }

    override fun onDestroy() {
        cancelled = true
        super.onDestroy()
    }

    private fun savedPc(): WifiTransfer.Pc? {
        val host = mochiPrefs.wifiHost
        return if (host.isEmpty()) null else WifiTransfer.Pc(mochiPrefs.wifiName, host)
    }

    private fun buildUi() {
        val color = getProperTextColor()
        val pad = (resources.displayMetrics.density * 16).toInt()
        fun label(text: String = "") = TextView(this).apply { this.text = text; setTextColor(color); textSize = 16f }
        fun button(textRes: Int, onClick: () -> Unit) = Button(this).apply { setText(textRes); setOnClickListener { onClick() } }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        infoText = label()
        pcText = label()
        hostEdit = EditText(this).apply {
            hint = getString(R.string.mochipic_wifi_manual_hint)
            inputType = InputType.TYPE_CLASS_PHONE
            setTextColor(color)
            setText(mochiPrefs.wifiHost)
        }
        dirText = label()
        sendButton = button(R.string.mochipic_wifi_send) { startSend() }
        cancelButton = button(R.string.mochipic_wifi_cancel) { cancelled = true }.apply { visibility = View.GONE }
        progressText = label()
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; visibility = View.GONE }

        root.addView(infoText)
        root.addView(space(pad))
        root.addView(pcText)
        root.addView(button(R.string.mochipic_wifi_find) { findPc() })
        root.addView(hostEdit)
        root.addView(button(R.string.mochipic_wifi_connect_ip) { connectManual() })
        root.addView(space(pad))
        root.addView(dirText)
        root.addView(button(R.string.mochipic_wifi_choose_dir) { chooseDir() })
        root.addView(space(pad))
        root.addView(sendButton)
        root.addView(cancelButton)
        root.addView(progressBar)
        root.addView(progressText)
        val scroll = ScrollView(this).apply { addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        setContentView(scroll)
        setupEdgeToEdge(padTopSystem = listOf(scroll), padBottomSystem = listOf(scroll))
        updateDirText()
    }

    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, h) }

    private fun updateInfo() {
        infoText.text = if (sources.isEmpty()) getString(R.string.mochipic_wifi_nothing_selected)
        else getString(R.string.mochipic_wifi_selected, sources.size)
        sendButton.isEnabled = sources.isNotEmpty()
    }

    private fun updateDirText() {
        dirText.text = getString(R.string.mochipic_wifi_dir, if (dir.isEmpty()) getString(R.string.mochipic_wifi_dir_root) else dir)
    }

    private fun setPc(found: WifiTransfer.Pc) {
        pc = found
        mochiPrefs.wifiHost = found.host
        mochiPrefs.wifiName = found.name
        hostEdit.setText(found.host)
        pcText.text = getString(R.string.mochipic_wifi_pc, found.name, found.host)
    }

    private fun findPc() {
        if (sending) return
        pcText.text = getString(R.string.mochipic_wifi_searching)
        Thread {
            val list = WifiTransfer.discover()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                when {
                    list.isEmpty() -> {
                        pc = null
                        pcText.text = getString(R.string.mochipic_wifi_not_found)
                    }
                    list.size == 1 -> setPc(list[0])
                    else -> AlertDialog.Builder(this)
                        .setTitle(R.string.mochipic_wifi_choose_pc)
                        .setItems(list.map { "${it.name}  (${it.host})" }.toTypedArray()) { _, i -> setPc(list[i]) }
                        .show()
                }
            }
        }.start()
    }

    private fun connectManual() {
        val host = hostEdit.text.toString().trim()
        if (host.isEmpty()) return
        pcText.text = getString(R.string.mochipic_wifi_connecting)
        Thread {
            val ok = WifiTransfer.ping(host)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (ok != null) setPc(ok) else {
                    pc = null
                    pcText.text = getString(R.string.mochipic_wifi_connect_failed, host)
                }
            }
        }.start()
    }

    private fun chooseDir() {
        val target = pc ?: return toast(R.string.mochipic_wifi_need_pc)
        Thread {
            val dirs = try { WifiTransfer.listDirs(target) } catch (_: Exception) { null }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                if (dirs == null) return@runOnUiThread toast(R.string.mochipic_wifi_list_failed)
                val rootLabel = getString(R.string.mochipic_wifi_dir_root)
                val newLabel = getString(R.string.mochipic_wifi_dir_new)
                val items = (listOf(rootLabel) + dirs + newLabel).toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle(R.string.mochipic_wifi_choose_dir)
                    .setItems(items) { _, i ->
                        when (i) {
                            0 -> setDir("")
                            items.size - 1 -> askNewDir()
                            else -> setDir(items[i])
                        }
                    }
                    .show()
            }
        }.start()
    }

    private fun askNewDir() {
        val edit = EditText(this).apply { setSingleLine() }
        AlertDialog.Builder(this)
            .setTitle(R.string.mochipic_wifi_dir_new)
            .setView(edit)
            .setPositiveButton(android.R.string.ok) { _, _ -> setDir(edit.text.toString().trim().replace('/', '_').replace('\\', '_')) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setDir(name: String) {
        dir = name
        mochiPrefs.wifiDir = name
        updateDirText()
    }

    /** 選択したパス（ファイルまたはフォルダ）を、送るファイルの一覧にする。フォルダは直下の画像・動画だけ。 */
    private fun expand(paths: List<String>): List<File> {
        val out = ArrayList<File>()
        for (p in paths) {
            val f = File(p)
            if (f.isDirectory) {
                f.listFiles()?.filter { it.isFile && it.name.isMediaFile() }?.sortedBy { it.name.lowercase() }?.let { out.addAll(it) }
            } else if (f.isFile) {
                out.add(f)
            }
        }
        return out
    }

    private fun startSend() {
        val target = pc ?: return toast(R.string.mochipic_wifi_need_pc)
        if (sending || sources.isEmpty()) return
        sending = true
        cancelled = false
        sendButton.isEnabled = false
        cancelButton.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        val sendDir = dir
        Thread {
            val files = expand(sources)
            val total = files.sumOf { it.length() }.coerceAtLeast(1L)
            var done = 0L
            var ok = 0
            var failed = 0
            for ((i, f) in files.withIndex()) {
                if (cancelled) break
                runOnUiThread { progressText.text = getString(R.string.mochipic_wifi_progress, i + 1, files.size, f.name) }
                val base = done
                val success = WifiTransfer.upload(target, sendDir, f, { cancelled }) { sent ->
                    val p = ((base + sent) * 1000L / total).toInt()
                    runOnUiThread { progressBar.progress = p }
                }
                if (success) ok++ else if (!cancelled) failed++
                done += f.length()
            }
            val wasCancelled = cancelled
            runOnUiThread {
                sending = false
                if (isDestroyed) return@runOnUiThread
                cancelButton.visibility = View.GONE
                progressBar.visibility = View.GONE
                sendButton.isEnabled = true
                progressText.text = if (wasCancelled) getString(R.string.mochipic_wifi_cancelled, ok)
                else getString(R.string.mochipic_wifi_done, ok, failed)
            }
        }.start()
    }

    companion object {
        const val EXTRA_PATHS = "mochipic_wifi_paths"

        /** 選択した画像・動画、またはアルバム（フォルダ）の一覧を渡して画面を開く。 */
        fun start(context: Context, paths: List<String>) {
            context.startActivity(Intent(context, WifiSendActivity::class.java).putStringArrayListExtra(EXTRA_PATHS, ArrayList(paths)))
        }
    }
}
