@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1

:: ============================================================
:: ETG PluginSystem - universal plugin builder (shared core)
:: Usage:  tools\build_core.bat <project_dir>
::
:: Reads  <project_dir>\versions.txt   (per-fork version list)
:: Uses   <project_dir>\bin\classes\<fork>\<version>\*.jar
::         + <project_dir>\bin\android.jar
::
:: 1. Compiles plugin sources against each version's classes
:: 2. Converts to DEX (d8), base64+zlib encodes
:: 3. Injects payload into code\python.py
:: 4. Writes <plugin_id>.plugin
:: ============================================================

set "TOOLS_DIR=%~dp0"
set "PROJECT_DIR=%~1"
if "%PROJECT_DIR%"=="" (
    echo ERROR: project directory not specified.
    exit /b 1
)
if not exist "%PROJECT_DIR%" (
    echo ERROR: project directory not found: %PROJECT_DIR%
    exit /b 1
)
if not "%PROJECT_DIR:~-1%"=="\" set "PROJECT_DIR=%PROJECT_DIR%\"

:: ---------------- resolve config ----------------
call "%TOOLS_DIR%config.bat"

:: d8.bat needs a modern Java runtime on its JAVA_HOME
if defined EX_JBR set "JAVA_HOME=%EX_JBR%"

:: ---------------- paths ----------------
set "CODE_DIR=%PROJECT_DIR%code"
set "BIN_DIR=%PROJECT_DIR%bin"
set "JAVA_SRC=%CODE_DIR%\java.java"
set "PYTHON_SRC=%CODE_DIR%\python.py"
set "VERSIONS_FILE=%PROJECT_DIR%versions.txt"
set "TEMP_DIR=%PROJECT_DIR%temp_build"

:: Override ANDROID_JAR to project-local
set "ANDROID_JAR=%BIN_DIR%\android.jar"

:: ---------------- validate tools ----------------
if not defined EX_JAVAC (
    echo ERROR: javac not found. Install Android Studio or set JAVA_HOME.
    pause
    exit /b 1
)
if not defined EX_D8 (
    echo ERROR: d8 not found. Install Android build-tools.
    pause
    exit /b 1
)
if not exist "%ANDROID_JAR%" (
    echo ERROR: android.jar not found in bin\
    pause
    exit /b 1
)

set "JAVA_FOLDER=0"
for /r "%CODE_DIR%\java" %%f in (*.java) do set "JAVA_FOLDER=1"

if not exist "%JAVA_SRC%" if "!JAVA_FOLDER!"=="0" (
    echo ERROR: Java source not found: %JAVA_SRC% (or code\java\ folder^)
    pause
    exit /b 1
)
if not exist "%PYTHON_SRC%" (
    echo ERROR: Python source not found: %PYTHON_SRC%
    pause
    exit /b 1
)
if not exist "%VERSIONS_FILE%" (
    echo ERROR: versions.txt not found in project. Re-run create.bat.
    pause
    exit /b 1
)

:: ---------------- collect all versions from all forks ----------------
set VER_LIST=
for /f "usebackq delims=" %%l in ("%VERSIONS_FILE%") do (
    set "LINE=%%l"
    if not "!LINE!"=="" (
        set "CH=!LINE:~0,1!"
        if "!CH!"==" " (
            for %%v in (!LINE!) do set "VER_LIST=!VER_LIST! %%v"
        )
    )
)
if not defined VER_LIST (
    echo ERROR: versions.txt is empty. Re-run create.bat.
    pause
    exit /b 1
)

echo.
echo ================================================
echo   ETG PluginSystem Plugin Builder
echo ================================================
echo   Project : %PROJECT_DIR%
echo   Versions:%VER_LIST%
echo.

:: ---------------- extract plugin id from python ----------------
set "PLUGIN_ID="
for /f "tokens=2 delims==" %%a in ('findstr /r "^__id__" "%PYTHON_SRC%" 2^>nul') do (
    set "RAW=%%a"
    set "RAW=!RAW: =!"
    set "RAW=!RAW:"=!"
    set "RAW=!RAW:'=!"
    set "PLUGIN_ID=!RAW!"
)
if "%PLUGIN_ID%"=="" (
    echo ERROR: Could not extract __id__ from %PYTHON_SRC%
    pause
    exit /b 1
)
echo   Plugin ID: %PLUGIN_ID%
echo.

:: ---------------- analyze java sources ----------------
set "PACKAGE="
if "!JAVA_FOLDER!"=="1" (
    for /r "%CODE_DIR%\java" %%f in (*.java) do (
        if not defined PACKAGE (
            for /f "tokens=2 delims=; " %%p in ('findstr /r /c:"^package " "%%f" 2^>nul') do set "PACKAGE=%%p"
        )
    )
) else (
    for /f "tokens=2 delims=; " %%p in ('findstr /r /c:"^package " "%JAVA_SRC%" 2^>nul') do set "PACKAGE=%%p"
)
if "%PACKAGE%"=="" (
    echo ERROR: Could not find package declaration in the java sources
    pause
    exit /b 1
)

set "CLASS_NAME="
for /f "tokens=3 delims= " %%c in ('findstr /r /c:"^public class " "%CODE_DIR%\java\Main.java" 2^>nul') do set "CLASS_NAME=%%c"
if not defined CLASS_NAME (
    for /f "tokens=3 delims= " %%c in ('findstr /r /c:"^public class " "%JAVA_SRC%" 2^>nul') do set "CLASS_NAME=%%c"
)
if not defined CLASS_NAME set "CLASS_NAME=Main"

set "PACKAGE_DIR=%PACKAGE:.=\%"
echo   Package : %PACKAGE%  (class %CLASS_NAME%)
echo.

:: ---------------- prepare build ----------------
if exist "%TEMP_DIR%" rmdir /s /q "%TEMP_DIR%"
mkdir "%TEMP_DIR%"
mkdir "%TEMP_DIR%\src" 2>nul
if "!JAVA_FOLDER!"=="1" (
    xcopy /e /y /q /i "%CODE_DIR%\java" "%TEMP_DIR%\src" >nul
) else (
    mkdir "%TEMP_DIR%\src\%PACKAGE_DIR%" 2>nul
    copy /y "%JAVA_SRC%" "%TEMP_DIR%\src\%PACKAGE_DIR%\%CLASS_NAME%.java" >nul
)

set "SRC_ARG="
for /r "%TEMP_DIR%\src" %%f in (*.java) do set "SRC_ARG=!SRC_ARG! "%%f""
if not defined SRC_ARG (
    echo ERROR: no java sources found in the project
    rmdir /s /q "%TEMP_DIR%" 2>nul
    pause
    exit /b 1
)

:: ============================================================
:: STEP 1: per-version compatibility check
:: ============================================================
echo [1/5] Compatibility check (compiling against each version)...
echo.

set COMPAT_OK=1

for %%v in (%VER_LIST%) do call :check_version %%v

echo.

if not "%COMPAT_OK%"=="1" (
    echo ================================================
    echo   BUILD FAILED: not compatible with all versions!
    echo ================================================
    for %%v in (%VER_LIST%) do (
        if exist "%TEMP_DIR%\javac_err_%%v.txt" (
            for %%s in ("%TEMP_DIR%\javac_err_%%v.txt") do set "ERR_SIZE=%%~zs"
            if not "!ERR_SIZE!"=="0" (
                echo.
                echo   Errors for version %%v:
                for /f "delims=" %%e in ('type "%TEMP_DIR%\javac_err_%%v.txt"') do echo     %%e
            )
        )
    )
    rmdir /s /q "%TEMP_DIR%" 2>nul
    pause
    exit /b 1
)

:: ============================================================
:: STEP 2: convert compiled classes to DEX
:: ============================================================
echo [2/5] Converting to DEX...
set "DEX_SRC=%TEMP_DIR%\compile_%LAST_GOOD_VER%"

set "JAR_EXE=%EX_JBR%\bin\jar.exe"
if not exist "%JAR_EXE%" set "JAR_EXE=jar"
"%JAR_EXE%" cf "%TEMP_DIR%\classes.jar" -C "%DEX_SRC%" .
call "%EX_D8%" --lib "%ANDROID_JAR%" --output "%TEMP_DIR%" "%TEMP_DIR%\classes.jar"
if errorlevel 1 (
    echo ERROR: d8 conversion failed
    rmdir /s /q "%TEMP_DIR%" 2>nul
    pause
    exit /b 1
)
if not exist "%TEMP_DIR%\classes.dex" (
    echo ERROR: classes.dex was not created
    rmdir /s /q "%TEMP_DIR%" 2>nul
    pause
    exit /b 1
)
echo        DEX created successfully

:: ============================================================
:: STEP 3: encode DEX to base64+zlib
:: ============================================================
echo [3/5] Encoding DEX to base64...
set "TEMP_FWD=%TEMP_DIR:\=/%"
set "PY_FWD=%PYTHON_SRC:\=/%"
python -c "import base64,zlib;dex=open('%TEMP_FWD%/classes.dex','rb').read();enc=base64.b64encode(zlib.compress(dex,9)).decode();lines=['# '+enc[i:i+76] for i in range(0,len(enc),76)];open('%TEMP_FWD%/dex_payload.txt','w').write('\n'.join(lines))"
if errorlevel 1 (
    echo ERROR: base64 encoding failed - is python installed?
    rmdir /s /q "%TEMP_DIR%" 2>nul
    pause
    exit /b 1
)
echo        Encoding successful

:: ============================================================
:: STEP 4: assemble plugin file
:: ============================================================
echo [4/5] Assembling plugin file...
python -c "py=open('%PY_FWD%','r',encoding='utf-8').read();parts=py.split('# __DEX_BEGIN__');before=parts[0];after=parts[1].split('# __DEX_END__')[1];payload=open('%TEMP_FWD%/dex_payload.txt','r').read();open('%PROJECT_DIR:\=/%%PLUGIN_ID%.plugin','w',encoding='utf-8').write(before+'# __DEX_BEGIN__\n'+payload+'\n# __DEX_END__'+after)"
if errorlevel 1 (
    echo ERROR: plugin assembly failed
    rmdir /s /q "%TEMP_DIR%" 2>nul
    pause
    exit /b 1
)
echo        Assembly successful

:: ============================================================
:: STEP 5: cleanup
:: ============================================================
echo [5/5] Cleaning up...
rmdir /s /q "%TEMP_DIR%" 2>nul

echo.
echo ================================================
echo   BUILD SUCCESS: %PLUGIN_ID%.plugin
echo ================================================
echo.
for %%f in ("%PROJECT_DIR%%PLUGIN_ID%.plugin") do (
    echo   Size: %%~zf bytes
    echo   Path: %%~ff
)
echo.

endlocal
exit /b 0

:: ============================================================
:: :check_version <version>
:: Compiles plugin source against one version's classes.
:: ============================================================
:check_version
set "VER=%~1"
set "VER_CP=%ANDROID_JAR%"

:: Search for classes across all forks
for /d %%f in ("%BIN_DIR%\classes\*") do (
    if exist "%BIN_DIR%\classes\%%~nxf\%VER%\" (
        for %%j in ("%BIN_DIR%\classes\%%~nxf\%VER%\*.jar") do (
            if "!VER_CP!"=="" ( set "VER_CP=%%j" ) else ( set "VER_CP=!VER_CP!;%%j" )
        )
    )
)

if "!VER_CP!"=="%ANDROID_JAR%" (
    echo   [%VER%] SKIP - no classes extracted. Run create.bat again.
    set COMPAT_OK=0
    exit /b 0
)

set "OUT_DIR=%TEMP_DIR%\compile_%VER%"
mkdir "!OUT_DIR!" 2>nul

"%EX_JAVAC%" -source 1.8 -target 1.8 -Xlint:-options -Xlint:deprecation -d "!OUT_DIR!" -cp "!VER_CP!" %SRC_ARG% 2>"%TEMP_DIR%\javac_err_%VER%.txt"
if errorlevel 1 (
    echo   [%VER%] FAILED - incompatible or missing classes
    set COMPAT_OK=0
) else (
    echo   [%VER%] OK
    set "LAST_GOOD_VER=%VER%"
)
exit /b 0
