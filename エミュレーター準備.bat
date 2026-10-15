@echo off
rem ============================================================
rem  エミュレーター準備（初回だけ）: Android 公式エミュレーターと仮想端末 MochiPic を _tools\ に作ります（約 4GB）
rem ============================================================
setlocal
title MochiPic エミュレーター準備
cd /d "%~dp0"
call scripts\env.bat
if not exist "%JAVA_HOME%\bin\java.exe" goto no_setup
set "LOG=%T%\emulator_setup.log"
set "SDKM=%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat"
set "AVDM=%ANDROID_HOME%\cmdline-tools\latest\bin\avdmanager.bat"
echo ==== emulator setup %DATE% %TIME% ====>>"%LOG%"

echo [1/4] エミュレーターと Android 15 の仮想端末用システムを取得しています（数分～十数分）...
rem 利用規約への同意はユーザーが承認済み（2026-10-15）
>"%T%\yes.txt" (for /l %%i in (1,1,40) do @echo y)
call "%SDKM%" --sdk_root="%ANDROID_HOME%" --licenses <"%T%\yes.txt">>"%LOG%" 2>&1
call "%SDKM%" --sdk_root="%ANDROID_HOME%" "emulator" "%AVD_IMAGE%">>"%LOG%" 2>&1
if errorlevel 1 goto fail
if not exist "%ANDROID_HOME%\emulator\emulator.exe" goto fail
echo   OK

echo [2/4] 仮想化（ハイパーバイザー）が使えるか確認しています...
"%ANDROID_HOME%\emulator\emulator.exe" -accel-check>>"%LOG%" 2>&1
"%ANDROID_HOME%\emulator\emulator.exe" -accel-check>"%T%\accel_check.txt" 2>&1
type "%T%\accel_check.txt"

echo [3/4] 仮想端末 %AVD_NAME% を作っています...
if not exist "%ANDROID_AVD_HOME%" mkdir "%ANDROID_AVD_HOME%"
echo no| call "%AVDM%" create avd -n %AVD_NAME% -k "%AVD_IMAGE%" -d pixel_6 --force>>"%LOG%" 2>&1
if errorlevel 1 goto fail
echo   OK

echo [4/4] 仮想端末の設定（日本語・メモリ）...
set "CFG=%ANDROID_AVD_HOME%\%AVD_NAME%.avd\config.ini"
>>"%CFG%" echo hw.ramSize=3072
>>"%CFG%" echo hw.keyboard=yes
>>"%CFG%" echo disk.dataPartition.size=8G
echo   OK
echo.
echo 準備が終わりました。「エミュレーター起動.bat」で起動します。
pause
exit /b 0

:no_setup
echo 先に「セットアップ.bat」を実行してください。
pause
exit /b 1

:fail
echo [!] 失敗しました。_tools\emulator_setup.log を確認してください。
pause
exit /b 1
