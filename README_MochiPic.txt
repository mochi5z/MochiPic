MochiPic（開発用フォルダ）   J:\APP\MochiPic
=====================================================
（2026-10-15 作成）

QP-Gallery と同じ見た目・使い心地のギャラリーアプリ。Fossify Gallery（GPL-3.0）を元に作っています。
仕様書: claude.ai の「MochiPic 仕様書」

■ 初回だけ
  1. セットアップ.bat          JDK 21 と Android SDK を _tools\ に入れ、署名鍵を作ります（約 2GB）
  2. スマホ接続を許可.bat      APK 配布ページをスマホから開けるようにします（管理者の許可画面で「はい」）

■ 毎回
  ビルド.bat     APK を作って dist\MochiPic-<版>.apk に置きます（初回は 10〜20 分、2 回目からは数分）
  APK配布.bat    画面に出る http://192.168.x.x:8194 をスマホのブラウザで開き、APK をタップして保存→インストール
                 ※ 初回はスマホで「この提供元のアプリを許可」をオンにする必要があります

■ 大事なファイル（必ずバックアップ）
  _tools\mochipic.jks と keystore.properties
  アプリの署名鍵です。無くすと、スマホに入れた MochiPic を上書き更新できなくなります（入れ直しになります）

■ フォルダ
  app\        アプリ本体（Fossify Gallery を元に変更していく）
  scripts\    bat から使う小さなスクリプト
  _tools\     JDK・Android SDK・Gradle の部品（git には入れない）
  dist\       できた APK
  logs\       ビルドの記録

■ git
  upstream = Fossify Gallery。Fossify の更新は git fetch upstream で取り込めます

■ ポート
  APK 配布: J:\APP\ports.ini の MOCHIPIC_PORT（標準 8194）
