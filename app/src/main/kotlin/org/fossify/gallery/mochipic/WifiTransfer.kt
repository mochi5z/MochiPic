package org.fossify.gallery.mochipic

import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

/**
 * MochiPic: Wi-Fi 送信（スマホ → PC）の通信部分。PC 側は tools/pc-receiver の受信ソフト。
 * 通信は同じ Wi-Fi 内の HTTP。PC の探索は UDP の呼びかけ（ブロードキャスト）、
 * 送信は 1 ファイルずつ PUT /file?dir=…&name=…&mtime=…（本文はファイルそのまま）。
 * 確認コードなし（同じ Wi-Fi 内なら誰でも送れる、という仕様）。
 */
object WifiTransfer {
    const val PORT = 48765
    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 60000
    private const val BUFFER_SIZE = 64 * 1024

    /** 直近の接続失敗の理由（画面に出して原因を調べやすくする）。 */
    @Volatile var lastError: String = ""

    data class Pc(val name: String, val host: String, val port: Int = PORT)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** 同じ Wi-Fi 内の受信ソフトを探す。見つかった順に返す。 */
    fun discover(timeoutMs: Int = 2500): List<Pc> {
        val found = LinkedHashMap<String, Pc>()
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 400
                val msg = "MOCHIPIC?".toByteArray()
                val deadline = System.currentTimeMillis() + timeoutMs
                var lastSend = 0L
                val buf = ByteArray(512)
                while (System.currentTimeMillis() < deadline) {
                    if (System.currentTimeMillis() - lastSend > 800) {
                        for (addr in broadcastAddresses()) {
                            try {
                                socket.send(DatagramPacket(msg, msg.size, addr, PORT))
                            } catch (_: Exception) {
                            }
                        }
                        lastSend = System.currentTimeMillis()
                    }
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        socket.receive(p)
                        val parts = String(p.data, 0, p.length).split("|")
                        if (parts.size >= 3 && parts[0] == "MOCHIPIC") {
                            val host = p.address.hostAddress ?: continue
                            found[host] = Pc(parts[1], host, parts[2].toIntOrNull() ?: PORT)
                        }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        return found.values.toList()
    }

    /** 呼びかけの送り先: 端末の各ネットワークのブロードキャスト（192.168.x.255 など）と 255.255.255.255。 */
    private fun broadcastAddresses(): List<InetAddress> {
        val list = LinkedHashSet<InetAddress>()
        try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { nif ->
                if (nif.isUp && !nif.isLoopback) {
                    nif.interfaceAddresses.forEach { ia -> ia.broadcast?.let { list.add(it) } }
                }
            }
        } catch (_: Exception) {
        }
        try {
            list.add(InetAddress.getByName("255.255.255.255"))
        } catch (_: Exception) {
        }
        return list.toList()
    }

    /** 指定の PC に繋がるか確かめ、繋がれば PC の名前を返す。 */
    fun ping(host: String, port: Int = PORT): Pc? = try {
        val text = get(host, port, "/ping", 2500)
        val parts = text.trim().split("|")
        if (parts.size >= 3 && parts[0] == "MOCHIPIC") Pc(parts[1], host, port) else {
            lastError = "bad reply: ${text.take(40)}"
            null
        }
    } catch (e: Exception) {
        lastError = "${e.javaClass.simpleName}: ${e.message}"
        null
    }

    /** 受信フォルダの直下にあるフォルダ名の一覧。 */
    fun listDirs(pc: Pc): List<String> =
        get(pc.host, pc.port, "/dirs", CONNECT_TIMEOUT_MS).split("\n").map { it.trim() }.filter { it.isNotEmpty() }.sortedWith(String.CASE_INSENSITIVE_ORDER)

    private fun get(host: String, port: Int, path: String, timeout: Int): String {
        val conn = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 1 ファイルを送る。成功なら true。途中でやめたとき・失敗したときは false
     * （PC 側は受け取りきれなかったファイルを残さない）。[onBytes] は送った量の累計。
     */
    fun upload(pc: Pc, dir: String, file: File, cancelled: () -> Boolean, onBytes: (Long) -> Unit): Boolean {
        val url = URL("http://${pc.host}:${pc.port}/file?dir=${enc(dir)}&name=${enc(file.name)}&mtime=${file.lastModified()}")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setFixedLengthStreamingMode(file.length())
            var sent = 0L
            file.inputStream().use { input ->
                conn.outputStream.use { out ->
                    val buf = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (cancelled()) return false
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        sent += n
                        onBytes(sent)
                    }
                }
            }
            return conn.responseCode == 200
        } catch (_: Exception) {
            return false
        } finally {
            conn.disconnect()
        }
    }
}
