@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1

:: ============================================================
:: ETG PluginSystem - change versions (core)
:: Usage:  tools\change_versions_core.bat <project_dir>
:: ============================================================

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%~1"
if "%PROJECT_DIR%"=="" (
    echo ERROR: project directory not specified.
    pause
    exit /b 1
)
if not exist "%PROJECT_DIR%" (
    echo ERROR: project directory not found: %PROJECT_DIR%
    pause
    exit /b 1
)
if not "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR%\"

for %%i in ("%PROJECT_DIR%..\..") do set "PS_ROOT=%%~fi"

:: verify this is actually a PluginSystem root
if not exist "%PS_ROOT%\tools\config.bat" (
    if not exist "%PS_ROOT%\data\apk" (
        echo ERROR: change_versions requires the full PluginSystem folder structure.
        echo        This script cannot run from a standalone project folder.
        echo        Copy the project back into PluginSystem\projects\ to use this tool.
        pause
        exit /b 1
    )
)

call "%PS_ROOT%\tools\config.bat"
if defined EX_JBR set "JAVA_HOME=%EX_JBR%"

set "VERSIONS_FILE=%PROJECT_DIR%versions.txt"
set "BIN_DIR=%PROJECT_DIR%bin"

if not exist "%VERSIONS_FILE%" (
    echo ERROR: versions.txt not found in project.
    pause
    exit /b 1
)

echo.
echo ================================================
echo   ETG PluginSystem - Change Versions
echo ================================================
echo   Project: %PROJECT_DIR%
echo.

:: ---------------- scan forks and versions ----------------
set "FORK_COUNT=0"
set "ALL_VERS="

for /d %%f in ("%PS_ROOT%\data\apk\*") do (
    set /a FORK_COUNT+=1
    call :scan_fork "%%f"
)

if %FORK_COUNT%==0 (
    echo   NOTE: no fork folders found in data\apk\
    pause
    exit /b 1
)

echo Available forks:
call :show_forks
echo.

:: ---------------- ask min version ----------------
:ask_min
set "MIN_VER="
set /p "MIN_VER=Min version (Enter = keep current): "
if not "%MIN_VER%"=="" (
    echo %MIN_VER%| findstr /r "^[0-9][0-9.]*$" >nul
    if errorlevel 1 (
        echo   ERROR: invalid version format, expected e.g. 12.8.1
        goto ask_min
    )
)

:: ---------------- compute required versions per fork ----------------
call :compute_selected

echo.
echo Selected versions by fork:
call :show_selected
echo.

:: ---------------- extract or load classes ----------------
echo Preparing classes...
call :extract_all
echo.

:: ---------------- update project bin ----------------
echo Updating project bin...

if exist "%PS_ROOT%\data\android.jar" (
    copy /y "%PS_ROOT%\data\android.jar" "%PROJECT_DIR%\bin\android.jar" >nul
)

if exist "%PROJECT_DIR%\bin\classes" rmdir /s /q "%PROJECT_DIR%\bin\classes"
mkdir "%PROJECT_DIR%\bin\classes" 2>nul

call :copy_classes

:: update versions.txt
if exist "%VERSIONS_FILE%" del "%VERSIONS_FILE%"
call :write_versions

echo.
echo ================================================
echo   VERSIONS UPDATED
echo ================================================
echo.
echo   Updated versions.txt:
call :show_selected
echo.
echo   Classes in bin\classes\ have been updated.
echo.

endlocal
exit /b 0


:: ============================================================
:: SUBROUTINES
:: ============================================================

:scan_fork
set "FPATH=%~1"
set "FNAME=%~nx1"
set "FORK_%FORK_COUNT%=%FNAME%"
set "FORK_VERS_%FORK_COUNT%="
set "VCNT=0"
for %%f in ("%FPATH%\*.apk") do (
    set "BASE=%%~nf"
    set "VN=!BASE:*_=!"
    if not "!VN!"=="!BASE!" (
        set /a VCNT+=1
        set "FORK_VERS_%FORK_COUNT%=!FORK_VERS_%FORK_COUNT%!!VN! "
        set "ALL_VERS=!ALL_VERS! !VN!"
    )
)
set "FORK_VER_CNT_%FORK_COUNT%=%VCNT%"
exit /b 0

:show_forks
for /l %%i in (1,1,%FORK_COUNT%) do call :show_one_fork %%i
exit /b 0

:show_one_fork
set "FN=!FORK_%1!"
set "FC=!FORK_VER_CNT_%1!"
set "FV=!FORK_VERS_%1!"
echo   %FN%  (%FC% APKs: %FV%)
exit /b 0

:compute_selected
for /l %%i in (1,1,%FORK_COUNT%) do call :compute_one_fork %%i
exit /b 0

:compute_one_fork
set "IDX=%1"
set "FV=!FORK_VERS_%IDX%!"
set "SELECTED_%IDX%="
for %%v in (%FV%) do (
    if "%MIN_VER%"=="" (
        set "SELECTED_%IDX%=!SELECTED_%IDX%! %%v"
    ) else (
        call :VER_GE %%v %MIN_VER%
        if "!EX_GE!"=="1" set "SELECTED_%IDX%=!SELECTED_%IDX%! %%v"
    )
)
exit /b 0

:show_selected
for /l %%i in (1,1,%FORK_COUNT%) do call :show_one_selected %%i
exit /b 0

:show_one_selected
set "FN=!FORK_%1!"
set "SV=!SELECTED_%1!"
if defined SV (
    echo   %FN%:%SV%
) else (
    echo   %FN%: (none)
)
exit /b 0

:extract_all
for /l %%i in (1,1,%FORK_COUNT%) do call :extract_one_fork %%i
exit /b 0

:extract_one_fork
set "IDX=%1"
set "FN=!FORK_%IDX%!"
set "SV=!SELECTED_%IDX%!"
for %%v in (%SV%) do (
    set "CACHED=%PS_ROOT%\temp\classes\!FN!\%%v"
    set "APK=%PS_ROOT%\data\apk\!FN!\!FN!_%%v.apk"
    if exist "!CACHED!\*.jar" (
        echo [!FN!:%%v] Classes cached
    ) else if exist "!APK!" (
        call "%PS_ROOT%\tools\extract_classes.bat" "!FN!" "!APK!" %%v
        if errorlevel 1 echo   WARNING: extraction failed for !FN!:%%v
    ) else (
        echo   WARNING: APK not found for !FN!:%%v
    )
)
exit /b 0

:copy_classes
for /l %%i in (1,1,%FORK_COUNT%) do call :copy_one_fork %%i
exit /b 0

:copy_one_fork
set "IDX=%1"
set "FN=!FORK_%IDX%!"
set "SV=!SELECTED_%IDX%!"
for %%v in (%SV%) do (
    set "SRC=%PS_ROOT%\temp\classes\!FN!\%%v"
    set "DST=%PROJECT_DIR%\bin\classes\!FN!\%%v"
    if exist "!SRC!\*.jar" (
        mkdir "!DST!" 2>nul
        copy /y "!SRC!\*.jar" "!DST!\" >nul
    )
)
exit /b 0

:write_versions
for /l %%i in (1,1,%FORK_COUNT%) do call :write_one_fork_version %%i
exit /b 0

:write_one_fork_version
set "IDX=%1"
set "FN=!FORK_%IDX%!"
set "SV=!SELECTED_%IDX%!"
>> "%VERSIONS_FILE%" echo %FN%
for %%v in (%SV%) do >> "%VERSIONS_FILE%" echo   %%v
exit /b 0

:: ============================================================
:: :VER_GE <a> <b>   -> sets EX_GE=1 if a >= b
:: ============================================================
:VER_GE
set "EX_GE=0"
for /f "tokens=1-4 delims=." %%a in ("%~1.0.0.0") do (
    set "A1=%%a" & set "A2=%%b" & set "A3=%%c" & set "A4=%%d"
)
for /f "tokens=1-4 delims=." %%a in ("%~2.0.0.0") do (
    set "B1=%%a" & set "B2=%%b" & set "B3=%%c" & set "B4=%%d"
)
set /a A1+=0 & set /a A2+=0 & set /a A3+=0 & set /a A4+=0
set /a B1+=0 & set /a B2+=0 & set /a B3+=0 & set /a B4+=0
if %A1% gtr %B1% (set "EX_GE=1" & goto :eof)
if %A1% lss %B1% goto :eof
if %A2% gtr %B2% (set "EX_GE=1" & goto :eof)
if %A2% lss %B2% goto :eof
if %A3% gtr %B3% (set "EX_GE=1" & goto :eof)
if %A3% lss %B3% goto :eof
if %A4% gtr %B4% (set "EX_GE=1" & goto :eof)
if %A4% lss %B4% goto :eof
set "EX_GE=1"
goto :eof
