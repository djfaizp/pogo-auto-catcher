@echo off
echo Pokémon GO Auto Catcher Frida Setup
echo ==================================
echo.

REM Check if ADB is available
where adb >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ADB is not installed or not in PATH.
    echo Please install Android SDK Platform Tools.
    pause
    exit /b 1
)

REM Check if device is connected
adb devices | findstr "device$" >nul
if %ERRORLEVEL% neq 0 (
    echo No Android device connected.
    echo Please connect your device and enable USB debugging.
    pause
    exit /b 1
)

REM Check if Frida server is already on the device
echo Checking if Frida server is already on the device...
adb shell "ls /data/local/tmp/frida-server" >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Frida server not found on the device.
    echo.
    
    REM Download Frida server if not already downloaded
    if not exist frida-server-16.1.4-android-arm64.xz (
        echo Downloading Frida server...
        powershell -Command "Invoke-WebRequest -Uri 'https://github.com/frida/frida/releases/download/16.1.4/frida-server-16.1.4-android-arm64.xz' -OutFile 'frida-server-16.1.4-android-arm64.xz'"
        
        REM Check if download was successful
        if not exist frida-server-16.1.4-android-arm64.xz (
            echo Failed to download Frida server.
            pause
            exit /b 1
        )
    )
    
    REM Extract Frida server if not already extracted
    if not exist frida-server-16.1.4-android-arm64 (
        echo Extracting Frida server...
        powershell -Command "& { Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.FileSystem]::ExtractToDirectory('frida-server-16.1.4-android-arm64.xz', '.') }"
        
        REM Check if extraction was successful
        if not exist frida-server-16.1.4-android-arm64 (
            echo Failed to extract Frida server.
            pause
            exit /b 1
        )
    )
    
    REM Push Frida server to the device
    echo Pushing Frida server to the device...
    adb push frida-server-16.1.4-android-arm64 /data/local/tmp/frida-server
    
    REM Set executable permissions
    echo Setting executable permissions...
    adb shell "chmod 755 /data/local/tmp/frida-server"
)

REM Check if Frida server is running
echo Checking if Frida server is running...
adb shell "ps | grep frida-server" >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Starting Frida server...
    adb shell "/data/local/tmp/frida-server &" >nul 2>nul
    timeout /t 2 >nul
    
    REM Check if Frida server started successfully
    adb shell "ps | grep frida-server" >nul 2>nul
    if %ERRORLEVEL% neq 0 (
        echo Failed to start Frida server.
        echo Please make sure your device is rooted and try again.
        pause
        exit /b 1
    )
)

echo Frida server is running on the device.
echo.

REM Check if Pokémon GO is installed
echo Checking if Pokémon GO is installed...
adb shell "pm list packages | grep com.nianticlabs.pokemongo" >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Pokémon GO is not installed on the device.
    echo Please install Pokémon GO and try again.
    pause
    exit /b 1
)

echo Pokémon GO is installed on the device.
echo.

REM Check if our app is installed
echo Checking if Auto Catcher app is installed...
adb shell "pm list packages | grep com.catcher.pogoauto" >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Auto Catcher app is not installed on the device.
    echo Please install the app and try again.
    pause
    exit /b 1
)

echo Auto Catcher app is installed on the device.
echo.

echo Setup completed successfully!
echo.
echo Next steps:
echo 1. Start Pokémon GO on your device
echo 2. Start the Auto Catcher app
echo 3. Run the Frida log receiver with: python FridaLogReceiver.py --dark-mode
echo.

pause
