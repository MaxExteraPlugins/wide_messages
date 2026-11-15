@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: ETG PluginSystem - shared tool configuration
:: Detects javac / d8 / 7z / python / dex2jar
:: Expected to be called as: call tools\config.bat
:: Exposes: EX_ROOT, JAVAC, D8, ANDROID_JAR, D2J, SEVENZ
:: ============================================================

set "EX_ROOT=%~dp0.."
for %%i in ("%EX_ROOT%") do set "EX_ROOT=%%~fi"

set "EX_JAVAC="
set "EX_JBR="

:: ---------- javac ----------
if exist "C:\Program Files\Android\Android Studio\jbr\bin\javac.exe" (
    set "EX_JAVAC=C:\Program Files\Android\Android Studio\jbr\bin\javac.exe"
)
if "%EX_JAVAC%"=="" if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\javac.exe" set "EX_JAVAC=%JAVA_HOME%\bin\javac.exe"
)
if "%EX_JAVAC%"=="" for /f "delims=" %%j in ('where javac 2^>nul') do if not defined EX_JAVAC set "EX_JAVAC=%%j"

:: ---------- Java runtime root (for d8) ----------
if defined EX_JAVAC (
    for %%i in ("%EX_JAVAC%\..\..") do set "EX_JBR=%%~fi"
)
if not defined EX_JBR if defined JAVA_HOME set "EX_JBR=%JAVA_HOME%"

:: ---------- d8 (latest build-tools) ----------
set "EX_D8="
set "EX_BUILD_TOOLS="
set "EX_SDK_ROOT="
if defined ANDROID_SDK_ROOT set "EX_SDK_ROOT=%ANDROID_SDK_ROOT%"
if not defined EX_SDK_ROOT if defined ANDROID_HOME set "EX_SDK_ROOT=%ANDROID_HOME%"
if not defined EX_SDK_ROOT if exist "%LOCALAPPDATA%\Android\Sdk" set "EX_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
if defined EX_SDK_ROOT (
    if exist "%EX_SDK_ROOT%\build-tools" (
        for /f "delims=" %%d in ('dir /b /ad /on "%EX_SDK_ROOT%\build-tools" 2^>nul') do set "EX_BUILD_TOOLS=%%d"
    )
)
if defined EX_BUILD_TOOLS set "EX_D8=%EX_SDK_ROOT%\build-tools\%EX_BUILD_TOOLS%\d8.bat"
if not defined EX_D8 for /f "delims=" %%d in ('where d8.bat 2^>nul') do if not defined EX_D8 set "EX_D8=%%d"

:: ---------- android.jar ----------
set "ANDROID_JAR=%EX_ROOT%\data\android.jar"

:: ---------- dex2jar ----------
set "D2J="
if exist "%EX_ROOT%\tools\dex2jar\d2j-dex2jar.bat" set "D2J=%EX_ROOT%\tools\dex2jar\d2j-dex2jar.bat"

:: ---------- 7z ----------
set "SEVENZ="
for /f "delims=" %%z in ('where 7z 2^>nul') do if not defined SEVENZ set "SEVENZ=%%z"
if not defined SEVENZ if exist "C:\Program Files\7-Zip\7z.exe" set "SEVENZ=C:\Program Files\7-Zip\7z.exe"

endlocal & (
    set "EX_ROOT=%EX_ROOT%"
    set "EX_JAVAC=%EX_JAVAC%"
    set "EX_JBR=%EX_JBR%"
    set "EX_D8=%EX_D8%"
    set "EX_BUILD_TOOLS=%EX_BUILD_TOOLS%"
    set "ANDROID_JAR=%ANDROID_JAR%"
    set "D2J=%D2J%"
    set "SEVENZ=%SEVENZ%"
)
exit /b 0
