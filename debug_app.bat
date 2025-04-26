@echo off
echo Debugging Pokemon GO Auto Catcher
echo ================================

echo Checking ADB connection...
adb devices
if %ERRORLEVEL% NEQ 0 (
    echo Error: ADB connection failed. Make sure your device is connected and USB debugging is enabled.
    goto :end
)

echo.
echo Clearing logcat...
adb logcat -c
if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to clear logcat.
    goto :end
)

echo.
echo Installing the app...
adb install -r app\build\outputs\apk\debug\app-debug.apk
if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to install the app.
    goto :end
)

echo.
echo Starting the app...
adb shell am start -n com.catcher.pogoauto/.MainActivity
if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to start the app.
    goto :end
)

echo.
echo Capturing logs...
echo Press Ctrl+C to stop capturing logs.
echo.

adb logcat -v threadtime | findstr /i "pogoauto frida zygote pokemon"

:end
echo.
echo Debug session ended.
pause
