@echo off
chcp 65001 >nul
net session >nul 2>&1
if %errorlevel% neq 0 (
  echo Requesting administrator permission...
  powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
  exit /b
)
netsh advfirewall firewall delete rule name="MochiPic Receiver TCP" >nul 2>&1
netsh advfirewall firewall delete rule name="MochiPic Receiver UDP" >nul 2>&1
netsh advfirewall firewall add rule name="MochiPic Receiver TCP" dir=in action=allow protocol=TCP localport=48765 profile=private,domain
netsh advfirewall firewall add rule name="MochiPic Receiver UDP" dir=in action=allow protocol=UDP localport=48765 profile=private,domain
echo.
echo Done. (Allowed port 48765 on Private/Domain networks only.)
pause
