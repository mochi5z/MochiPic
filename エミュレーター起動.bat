@echo off
rem ============================================================
rem  エミュレーター起動: 仮想端末を起動し、最新の MochiPic と確認用の画像を入れて起動します
rem  （起動済みなら入れ直すだけ。ビルドした後に実行すると最新版になります）
rem ============================================================
setlocal
title MochiPic エミュレーター起動
cd /d "%~dp0"
call scripts\env.bat
if not exist "%ANDROID_AVD_HOME%\%AVD_NAME%.avd" goto no_avd
if not exist logs mkdir logs
set "LOG=%MP%\logs\emulator.log"
echo ==== run %DATE% %TIME% ====>"%LOG%"

adb start-server>>"%LOG%" 2>&1
adb get-state 1>nul 2>nul
if not errorlevel 1 goto booted
echo 仮想端末を起動しています（初回は 2～3 分かかります）...
start "MochiPic Emulator" /min "%ANDROID_HOME%\emulator\emulator.exe" -avd %AVD_NAME% -no-boot-anim -no-audio -gpu swiftshader_indirect
adb wait-for-device>>"%LOG%" 2>&1
:wait_boot
timeout /t 3 /nobreak>nul
for /f "delims=" %%b in ('adb shell getprop sys.boot_completed 2^>nul') do set "BOOT=%%b"
if not "%BOOT%"=="1" goto wait_boot
:booted
echo 仮想端末の準備ができました。

set "APK="
for /f "delims=" %%f in ('dir /b /o:d "dist\*.apk" 2^>nul') do set "APK=dist\%%f"
if not defined APK goto no_apk
echo MochiPic を入れています: %APK%
adb install -r -g "%APK%">>"%LOG%" 2>&1
if errorlevel 1 goto fail
adb shell appops set %APP_ID% MANAGE_EXTERNAL_STORAGE allow>>"%LOG%" 2>&1

if not exist testdata goto launch
echo 確認用の画像を入れています...
adb shell mkdir -p /sdcard/Pictures>>"%LOG%" 2>&1
adb push testdata\. /sdcard/Pictures/>>"%LOG%" 2>&1
adb shell content call --method scan_volume --uri content://media --arg external_primary>>"%LOG%" 2>&1

:launch
adb shell am force-stop %APP_ID%>>"%LOG%" 2>&1
adb shell monkey -p %APP_ID% -c android.intent.category.LAUNCHER 1 >>"%LOG%" 2>&1
echo MochiPic を起動しました。
timeout /t 3 >nul
exit /b 0

:no_avd
echo 先に「エミュレーター準備.bat」を実行してください。
pause
exit /b 1

:no_apk
echo dist\ に APK がありません。先に「ビルド.bat」を実行してください。
pause
exit /b 1

:fail
echo [!] 失敗しました。logs\emulator.log を確認してください。
pause
exit /b 1
