@echo off
setlocal
chcp 65001 >nul 2>&1
call "%~dp0bin\tools\build_core.bat" "%~dp0"
endlocal
pause
exit /b %ERRORLEVEL%
