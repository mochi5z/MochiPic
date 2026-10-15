@echo off
rem ============================================================
rem  MochiPic ビルド: APK を作って dist\ に置きます
rem  初回は部品のダウンロードがあるため 10～20 分ほどかかります
rem ============================================================
setlocal
title MochiPic ビルド
cd /d "%~dp0"
call scripts\env.bat
if not exist "%JAVA_HOME%\bin\java.exe" goto no_setup
if not exist local.properties goto no_setup
if not exist logs mkdir logs
for /f "tokens=2 delims==" %%v in ('findstr /b VERSION_NAME gradle.properties') do set "VER=%%v"
echo MochiPic %VER% をビルドしています（記録: logs\build.log）...
call gradlew.bat assembleFossRelease --console=plain>"logs\build.log" 2>&1
if errorlevel 1 goto fail
if not exist dist mkdir dist
set "OUT="
for %%f in (app\build\outputs\apk\foss\release\*.apk) do set "OUT=%%f"
if not defined OUT goto fail
copy /y "%OUT%" "dist\MochiPic-%VER%.apk">nul
echo.
echo 完了: dist\MochiPic-%VER%.apk
echo スマホに入れるときは「APK配布.bat」を実行してください。
pause
exit /b 0

:no_setup
echo 先に「セットアップ.bat」を実行してください。
pause
exit /b 1

:fail
echo [!] ビルドに失敗しました。logs\build.log の最後の方を確認してください。
pause
exit /b 1
