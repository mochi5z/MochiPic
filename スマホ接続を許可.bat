@echo off
rem ============================================================
rem  スマホ接続を許可: 同じ Wi-Fi のスマホから APK 配布ページを開けるようにします
rem  Windows ファイアウォールに「プライベート ネットワークのみ」の許可ルールを追加します
rem ============================================================
setlocal
net session >nul 2>nul
if errorlevel 1 (
  echo 管理者の許可が必要です。表示される画面で「はい」を押してください...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
for /f "usebackq eol=; tokens=1,2 delims==" %%a in ("%~dp0..\ports.ini") do set "%%a=%%b"
if not defined MOCHIPIC_PORT set "MOCHIPIC_PORT=8194"
netsh advfirewall firewall delete rule name="MochiPic APK" >nul 2>nul
netsh advfirewall firewall add rule name="MochiPic APK" dir=in action=allow protocol=TCP localport=%MOCHIPIC_PORT% profile=private
if errorlevel 1 (
  echo [!] ルールを追加できませんでした。
  pause
  exit /b 1
)
echo 許可しました（ポート %MOCHIPIC_PORT%。自宅などの「プライベート ネットワーク」のときだけ有効です）。
pause
