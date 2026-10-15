@echo off
rem MochiPic の開発ツールの場所（ほかの bat から call して使う）。すべて _tools\ の中に置き、C ドライブは使わない
for %%i in ("%~dp0..") do set "MP=%%~fi"
set "T=%MP%\_tools"
set "JAVA_HOME=%T%\jdk"
set "ANDROID_HOME=%T%\android-sdk"
set "ANDROID_SDK_ROOT=%T%\android-sdk"
set "ANDROID_USER_HOME=%T%\android-user"
set "GRADLE_USER_HOME=%T%\gradle-home"
set "PATH=%T%\jdk\bin;%T%\android-sdk\platform-tools;%PATH%"
