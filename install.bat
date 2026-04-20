@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "NATIVE_BINARY=%SCRIPT_DIR%hubbers-distribution\target\hubbers.exe"
set "JAR_FILE=%SCRIPT_DIR%hubbers-distribution\target\hubbers.jar"
set "INSTALL_DIR=%ProgramFiles%\Hubbers"
set "INSTALL_PATH=%INSTALL_DIR%\hubbers.exe"
set "JAR_INSTALL_PATH=%INSTALL_DIR%\hubbers.jar"

REM Determine what to install
if exist "%NATIVE_BINARY%" (
    set "BINARY_PATH=%NATIVE_BINARY%"
    set "BINARY_TYPE=native"
) else if exist "%JAR_FILE%" (
    set "BINARY_PATH=%JAR_FILE%"
    set "BINARY_TYPE=jar"
) else (
    echo ERROR: No executable found!
    echo.
    echo Please build first:
    echo   For native: build-native.bat
    echo   For JAR:    mvn clean package
    echo.
    exit /b 1
)

echo =========================================
echo Hubbers Installation Script
echo =========================================
echo.
echo Binary type: %BINARY_TYPE%
echo.

REM Check if binary exists (redundant but keep for clarity)
if not exist "%BINARY_PATH%" (
    echo ERROR: Executable not found at %BINARY_PATH%
    echo.
    exit /b 1
)

REM Check for admin privileges
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo ERROR: This script requires administrator privileges.
    echo.
    echo Please run this script as Administrator:
    echo   1. Right-click on install.bat
    echo   2. Select "Run as administrator"
    echo.
    echo Alternatively, you can manually copy the executable to a directory in your PATH.
    echo.
    exit /b 1
)

REM Create install directory if it doesn't exist
if not exist "%INSTALL_DIR%" (
    echo Creating directory: %INSTALL_DIR%
    mkdir "%INSTALL_DIR%"
)

REM Copy binary
echo Copying binary to: %INSTALL_PATH%
if "%BINARY_TYPE%"=="jar" (
    echo Copying JAR to: %JAR_INSTALL_PATH%
    copy /Y "%BINARY_PATH%" "%JAR_INSTALL_PATH%" >nul
    
    REM Create batch wrapper
    echo @echo off > "%INSTALL_PATH:.exe=.bat%"
    echo java -jar "%JAR_INSTALL_PATH%" %%* >> "%INSTALL_PATH:.exe=.bat%"
    
    echo Creating wrapper: %INSTALL_PATH:.exe=.bat%
) else (
    echo Copying binary to: %INSTALL_PATH%
    copy /Y "%BINARY_PATH%" "%INSTALL_PATH%" >nul
)
REM Add to PATH if not already there
echo %PATH% | find /i "%INSTALL_DIR%" >nul
if %errorlevel% neq 0 (
    echo Adding %INSTALL_DIR% to system PATH...
    setx /M PATH "%PATH%;%INSTALL_DIR%" >nul
    echo.
    echo NOTE: PATH updated. You may need to restart your terminal for changes to take effect.
)

echo.
echo =========================================
echo Installation successful!
echo =========================================
echo.
echo The 'hubbers' command is now available in your PATH.
echo.
echo Please restart your terminal or command prompt, then try:
echo   hubbers --version
echo   hubbers --help
echo   hubbers list agents
echo.
