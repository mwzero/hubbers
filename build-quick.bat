@echo off
REM Quick build script - skips UI module

setlocal

echo ========================================
echo Hubbers Quick Build (Framework + Dist)
echo ========================================
echo.
echo Skipping UI module (use 'mvn clean install' for full build)
echo.

call mvn clean package -pl hubbers-framework,hubbers-distribution -am -DskipTests

if %errorlevel% neq 0 (
    echo.
    echo Build FAILED!
    exit /b 1
)

echo.
echo ========================================
echo Build complete!
echo ========================================
echo.
echo JAR location: hubbers-distribution\target\hubbers.jar
echo.
echo Run with:
echo   java -jar hubbers-distribution\target\hubbers.jar ^<command^>
echo.
