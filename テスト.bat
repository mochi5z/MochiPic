@echo off
rem ============================================================
rem  MochiPic テスト: 画面に依存しない部分（ボタン配置のデータなど）の自動テストを実行します
rem ============================================================
setlocal
title MochiPic テスト
cd /d "%~dp0"
call scripts\env.bat
if not exist "%JAVA_HOME%\bin\java.exe" goto no_setup
if not exist logs mkdir logs
echo テストを実行しています（記録: logs\test.log）...
call gradlew.bat testFossDebugUnitTest --console=plain>"logs\test.log" 2>&1
if errorlevel 1 goto fail
echo.
echo すべてのテストに合格しました。
pause
exit /b 0

:no_setup
echo 先に「セットアップ.bat」を実行してください。
pause
exit /b 1

:fail
echo [!] 失敗したテストがあります。logs\test.log と app\build\reports\tests\ を確認してください。
pause
exit /b 1
