"""MochiPic の APK を同じ Wi-Fi のスマホに配る小さなサーバー（APK配布.bat から起動）。

dist\\ にある APK の一覧ページを出す。家のネットワーク（プライベート IP）からだけ受け付ける。
ポートは J:\\APP\\ports.ini の MOCHIPIC_PORT（無ければ 8194）。
"""
import html
import ipaddress
import os
import socket
import sys
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parent.parent
DIST = ROOT / "dist"


def port() -> int:
    ini = ROOT.parent / "ports.ini"
    try:
        for line in ini.read_text(encoding="utf-8").splitlines():
            k, _, v = line.partition("=")
            if k.strip() == "MOCHIPIC_PORT":
                return int(v.strip())
    except (OSError, ValueError):
        pass
    return 8194


def lan_ips() -> list[str]:
    ips = set()
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("192.168.0.1", 9))
            ips.add(s.getsockname()[0])
    except OSError:
        pass
    try:
        ips.update(ip for ip in socket.gethostbyname_ex(socket.gethostname())[2]
                   if ipaddress.ip_address(ip).is_private)
    except OSError:
        pass
    return sorted(ip for ip in ips if not ip.startswith("127."))


class Handler(SimpleHTTPRequestHandler):
    extensions_map = {**SimpleHTTPRequestHandler.extensions_map,
                      ".apk": "application/vnd.android.package-archive"}

    def __init__(self, *a, **kw):
        super().__init__(*a, directory=str(DIST), **kw)

    def _lan_only(self) -> bool:
        ip = ipaddress.ip_address(self.client_address[0])
        if ip.is_private or ip.is_loopback or ip.is_link_local:
            return True
        self.send_error(403, "LAN only")
        return False

    def do_HEAD(self):
        if self._lan_only():
            super().do_HEAD()

    def do_GET(self):
        if not self._lan_only():
            return
        if self.path in ("/", "/index.html"):
            return self._index()
        super().do_GET()

    def _index(self):
        apks = sorted(DIST.glob("*.apk"), key=lambda p: p.stat().st_mtime, reverse=True)
        rows = "".join(
            f'<li><a href="/{quote(p.name)}">{html.escape(p.name)}</a>'
            f'<small>{p.stat().st_size / 1024 ** 2:.1f} MB ・ '
            f'{time.strftime("%m/%d %H:%M", time.localtime(p.stat().st_mtime))}</small></li>'
            for p in apks) or "<li>まだ APK がありません（ビルド.bat を実行してください）</li>"
        body = f"""<!doctype html><html lang="ja"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>MochiPic</title>
<style>body{{font:16px system-ui,sans-serif;margin:24px;background:#111;color:#eee}}
a{{color:#f97316;font-size:18px}}li{{margin:14px 0}}small{{display:block;color:#999}}</style>
<h1>MochiPic</h1><p>タップして保存し、開いてインストールしてください。</p><ul>{rows}</ul>"""
        data = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    DIST.mkdir(exist_ok=True)
    p = port()
    print("MochiPic APK 配布中。スマホのブラウザで次の URL を開いてください:")
    for ip in lan_ips():
        print(f"   http://{ip}:{p}")
    print("止めるときはこの画面を閉じてください。")
    sys.stdout.flush()
    ThreadingHTTPServer(("0.0.0.0", p), Handler).serve_forever()
