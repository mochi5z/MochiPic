@echo off
rem MochiPic APK 配布: 同じ Wi-Fi のスマホのブラウザから dist\ の APK を保存できるようにします
setlocal
title MochiPic APK 配布
cd /d "%~dp0"
set "PY="
py -3.12 -c "import sys" >nul 2>nul && set "PY=py -3.12"
if not defined PY if exist "%LOCALAPPDATA%\Programs\Python\Python312\python.exe" set "PY="%LOCALAPPDATA%\Programs\Python\Python312\python.exe""
if not defined PY (
  echo Python 3.12 が見つかりません。
  pause
  exit /b 1
)
%PY% scripts\serve_apk.py
pause
