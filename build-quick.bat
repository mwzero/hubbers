@echo off
REM Quick build script - skips UI module

setlocal

echo ========================================
echo Hubbers Quick Build
echo ========================================
echo.
echo Skipping frontend rebuild (requires existing hubbers-ui\dist)
echo.

call mvn clean package -pl hubbers-distribution -am -DskipTests -Dhubbers.ui.skip.frontend=true

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
