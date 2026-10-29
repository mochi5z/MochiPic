@echo off
chcp 65001 >nul
title MochiPic PC receiver
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\pc-receiver\MochiPicReceiver.ps1" %*
pause
