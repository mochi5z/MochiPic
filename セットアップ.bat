@echo off
rem ============================================================
rem  MochiPic 開発環境セットアップ（初回だけ。もう一度実行すると足りない物だけ入れます）
rem  JDK 21 と Android SDK を J:\APP\MochiPic\_tools\ に入れます（約 2GB）
rem ============================================================
setlocal
title MochiPic 開発環境セットアップ
cd /d "%~dp0"
call scripts\env.bat
if not exist "%T%" mkdir "%T%"
set "LOG=%T%\setup.log"
echo ==== setup %DATE% %TIME% ====>>"%LOG%"

echo [1/5] JDK 21 を用意しています...
if exist "%JAVA_HOME%\bin\java.exe" goto jdk_ok
curl -L --fail -o "%T%\jdk.zip" "https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse">>"%LOG%" 2>&1
if errorlevel 1 goto fail
tar -xf "%T%\jdk.zip" -C "%T%">>"%LOG%" 2>&1
if errorlevel 1 goto fail
for /d %%d in ("%T%\jdk-21*") do move "%%d" "%JAVA_HOME%">nul
del "%T%\jdk.zip"
if not exist "%JAVA_HOME%\bin\java.exe" goto fail
:jdk_ok
echo   OK

echo [2/5] Android SDK のコマンドラインツールを用意しています...
set "SDKM=%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat"
if exist "%SDKM%" goto cl_ok
curl -L --fail -o "%T%\cmdline.zip" "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip">>"%LOG%" 2>&1
if errorlevel 1 goto fail
if not exist "%ANDROID_HOME%\cmdline-tools" mkdir "%ANDROID_HOME%\cmdline-tools"
tar -xf "%T%\cmdline.zip" -C "%ANDROID_HOME%\cmdline-tools">>"%LOG%" 2>&1
if errorlevel 1 goto fail
move "%ANDROID_HOME%\cmdline-tools\cmdline-tools" "%ANDROID_HOME%\cmdline-tools\latest">nul
del "%T%\cmdline.zip"
if not exist "%SDKM%" goto fail
:cl_ok
echo   OK

echo [3/5] Android SDK の利用規約の確認と、部品の取得（数分）...
rem 利用規約への同意はユーザーが承認済み（2026-10-15）。y を 40 回分ファイルに書いて答えとして渡す
>"%T%\yes.txt" (for /l %%i in (1,1,40) do @echo y)
call "%SDKM%" --sdk_root="%ANDROID_HOME%" --licenses <"%T%\yes.txt">>"%LOG%" 2>&1
call "%SDKM%" --sdk_root="%ANDROID_HOME%" "platform-tools" "platforms;android-36" "build-tools;36.0.0">>"%LOG%" 2>&1
if errorlevel 1 goto fail
echo   OK

echo [4/5] SDK の場所を記録しています...
set "SDKP=%ANDROID_HOME:\=\\%"
>local.properties echo sdk.dir=%SDKP%
echo   OK

echo [5/5] アプリの署名鍵を用意しています...
if exist keystore.properties goto key_ok
for /f %%p in ('powershell -NoProfile -Command "[guid]::NewGuid().ToString('N')"') do set "PW=%%p"
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -keystore "%T%\mochipic.jks" -alias mochipic -keyalg RSA -keysize 4096 -validity 36500 -storepass %PW% -keypass %PW% -dname "CN=MochiPic">>"%LOG%" 2>&1
if errorlevel 1 goto fail
set "KS=%T%\mochipic.jks"
set "KS=%KS:\=/%"
>keystore.properties echo storeFile=%KS%
>>keystore.properties echo storePassword=%PW%
>>keystore.properties echo keyAlias=mochipic
>>keystore.properties echo keyPassword=%PW%
:key_ok
echo   OK
echo.
echo セットアップが終わりました。続けて「ビルド.bat」を実行してください。
echo ※ _tools\mochipic.jks と keystore.properties は必ずバックアップしてください。
echo    無くすと、スマホに入れた MochiPic を上書き更新できなくなります。
pause
exit /b 0

:fail
echo [!] 失敗しました。_tools\setup.log を確認してください。
pause
exit /b 1
