@echo off
echo ===== Pokemon GO Auto Catcher - Frida Log Receiver =====
echo =========================================
echo.

REM Check if Python is installed
where python >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Python is not installed or not in PATH.
    echo Please install Python from https://www.python.org/downloads/
    pause
    exit /b 1
)

REM Check if required packages are installed
echo Checking required packages...
pip install -r requirements.txt

REM Check if Frida server is running on the device
echo Checking Frida server status...
adb shell "ps | grep frida-server" >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Frida server is not running on the device.
    echo Starting Frida server...
    adb shell "/data/local/tmp/frida-server &" >nul 2>nul
    timeout /t 2 >nul
)

REM Check if ADB is available
echo Checking ADB connection...
adb devices > adb_devices.txt
type adb_devices.txt
echo.

REM Check if Frida server is running
echo Checking Frida server status...
adb shell "ps | grep frida-server" > frida_status.txt
type frida_status.txt
if %ERRORLEVEL% neq 0 (
    echo WARNING: Frida server might not be running on the device.
    echo You may need to start it with: adb shell "/data/local/tmp/frida-server &"
    echo.
)

REM Check if Pokémon GO is running
echo Checking if Pokémon GO is running...
adb shell "ps | grep com.nianticlabs.pokemongo" > pogo_status.txt
type pogo_status.txt
if %ERRORLEVEL% neq 0 (
    echo WARNING: Pokémon GO is not running on the device.
    echo Please start Pokémon GO on your device.
    echo.
    echo Continuing anyway to capture logs when it starts...
    echo.
)

REM Check for Frida gadget in the process
echo Checking for Frida gadget in Pokémon GO process...
adb shell "ps | grep com.nianticlabs.pokemongo" > pogo_pid.txt
for /f "tokens=2" %%a in (pogo_pid.txt) do (
    set POGO_PID=%%a
    echo Pokémon GO PID: %%a
    adb shell "cat /proc/%%a/maps | grep frida" > frida_maps.txt
    type frida_maps.txt
    if %ERRORLEVEL% neq 0 (
        echo WARNING: Frida gadget might not be loaded in Pokémon GO.
        echo.
    ) else (
        echo Frida gadget is loaded in Pokémon GO.
        echo.
    )
)

REM Run the Frida log receiver
echo Starting Frida log receiver...
echo.
echo Press Ctrl+C to exit when done.
echo.
python FridaLogReceiver.py --dark-mode

echo.
echo Log receiver closed.
pause
