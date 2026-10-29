# MochiPic 開発ガイド（Claude 用）

Android ギャラリーアプリ **MochiPic**（`com.mochipic.gallery`）。QP-Gallery 風の見た目・操作で、Fossify Gallery（GPL-3.0）を fork して作っている。
必須要件は「QP と同じ機能」「軽量・省電力」「多形式対応」。対応 Android は 8 以上。

## 最初に守ること
- **返答は日本語。** 完了報告は「何ができたか」を簡潔に（手順の再説明は不要）。
- 担当者は **GitHub に詳しくない**。GitHub の操作を頼むときは、**手順を 1 つずつ**伝える。**注意点・制約は先に**伝え、後出しにしない。パスワードやトークンの入力は代行できない（本人が GitHub の画面で行う）。
- 仕様書は Claude Docs の「MochiPic 技術仕様書」（project `75169a02-ecb7-490c-9d4b-5792400468f6`）。作業前に読み、変更したら版と変更履歴を更新する。
- PC 側の引継書は `J:\APP\Hikitsugi\MochiPic_引継ぎ書_*.md`（PC にリンクしたセッションでのみ読める）。

## クラウドセッションでできること・できないこと
- できる：コードの調査・編集、commit、GitHub への push（`main` に直接 push してよい。ブランチの**削除は拒否される**ので、不要なブランチを作らない）。
- **できない：ビルドとテスト。** クラウドからは Gradle・Google Maven・Android SDK の配布元に接続できない（403）。`ビルド.bat` `テスト.bat` は **PC（Windows）で実行する**。
- そのため、クラウドで変更したら**未ビルドであることを明記**し、PC で「GitHubから取得.bat」→「テスト.bat」→「ビルド.bat」の順に確認してもらう。ビルドが通るまで、タグ `v<版>` と `releases\` への APK 保存は行わない。
- push 前に `git fetch origin main` を行い、`main` に追いついてから push する（浅い複製のまま古い状態で push しない）。

## 標準の作業の流れ
1. 仕様書とユーザーの指示を確認する（仕様を集めている段階では実装しない。「作業開始」で着手）。
2. `app/src/main/...` を編集し、`gradle.properties` の `VERSION_NAME` / `VERSION_CODE` を上げる。
3. commit して `main` に push する（末尾に、そのセッションで指定された `Co-Authored-By` と `Claude-Session` の行を付ける）。
4. PC で取得・テスト・ビルドしてもらう（上記）。通ったら、PC 側で APK を `releases\` にコピーし、タグ `v<版>` を付ける。
5. 仕様書の版・変更履歴を更新する。

## コード構成
- パッケージ：`app/src/main/kotlin/org/fossify/gallery/`。MochiPic 独自のコードは `…/mochipic/` に集約（`QuickAlbumLoader`、`LoadCache`、`MediaChangeWatcher`、`CopyTargetActivity`、`MoveTouch` など）。
- Fossify 本体を変えた箇所は `…/extensions/`（`Activity.kt` `Context.kt` `String.kt`）、`…/activities/`（`MainActivity` `MediaActivity` `SettingsActivity`）など。変更するときは、MochiPic 独自のコメント（`MochiPic:`）を付けて分かるようにする。
- 共通ライブラリは `commons-lib/`（ソースごと同梱）。独自設定は `mochipic_settings`（`MochiPicPrefs`）に保存する。
- 単体テストは `app/src/test/kotlin/org/fossify/gallery/mochipic/`（PC の `テスト.bat` で実行）。

## 触ってはいけないもの
- **署名鍵 `_tools\mochipic.jks` と `keystore.properties`**（PC のみ。git 管理外）。失うとスマホのアプリを上書き更新できなくなる。
- ファイアウォールと Hyper-V の設定。
- `releases\`（全版の APK）。`Delete\` はユーザーが手動で消す物。
- `.gitignore` で除外している物（`_tools/` `dist/` `logs/` `releases/` `*.jks` `keystore.properties` `testdata/`）を commit しない。

## 参考
- 読み込み方式は 2 つ：クイック（MediaStore 1 クエリ＋世代差分。既定）と全フォルダ走査（設定で選択）。省電力・高速化は主にクイック側。
- 同じ端末内の移動ではフォルダの更新日時が変わらない → `MoveTouch` で補っている。
- 次の作業候補（仕様書の手順）：8 更新確認（QP の「アプリの更新」画面のスクリーンショットと GitHub リポジトリ名が必要。リポジトリは `mochi5z/MochiPic`）、9 Aves 由来の多形式対応、10 速度確認と公開用ビルド（Baseline Profile）、11 Wi-Fi 転送。
