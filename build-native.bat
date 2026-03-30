@echo off
setlocal enabledelayedexpansion

echo =========================================
echo Hubbers Native Build Script
echo =========================================
echo.

REM Check if GraalVM native-image is installed
where native-image >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: GraalVM native-image not found!
    echo.
    echo Please install GraalVM and ensure 'native-image' is in your PATH.
    echo.
    echo Installation instructions:
    echo   1. Download GraalVM from: https://www.graalvm.org/downloads/
    echo   2. Set JAVA_HOME to GraalVM directory
    echo   3. Run: gu install native-image
    echo   4. Ensure you have Visual Studio Build Tools installed
    echo.
    exit /b 1
)

REM Display GraalVM version
echo Using GraalVM:
java -version
echo.

REM Clean and build with native profile
echo Building native executable...
echo.
call mvn -Pnative clean package

REM Check if build was successful
if exist "target\hubbers.exe" (
    echo.
    echo =========================================
    echo Build successful!
    echo =========================================
    echo.
    echo Native executable: target\hubbers.exe
    for %%A in (target\hubbers.exe) do echo Size: %%~zA bytes
    echo.
    echo To test the executable:
    echo   target\hubbers.exe --help
    echo.
    echo To install globally:
    echo   install.bat
    echo.
) else (
    echo.
    echo ERROR: Build failed. Native executable not found.
    exit /b 1
)
