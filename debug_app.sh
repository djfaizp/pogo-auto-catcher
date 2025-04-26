#!/bin/bash

echo "Debugging Pokemon GO Auto Catcher"
echo "================================"

echo "Checking ADB connection..."
adb devices
if [ $? -ne 0 ]; then
    echo "Error: ADB connection failed. Make sure your device is connected and USB debugging is enabled."
    exit 1
fi

echo ""
echo "Clearing logcat..."
adb logcat -c
if [ $? -ne 0 ]; then
    echo "Error: Failed to clear logcat."
    exit 1
fi

echo ""
echo "Installing the app..."
adb install -r app/build/outputs/apk/debug/app-debug.apk
if [ $? -ne 0 ]; then
    echo "Error: Failed to install the app."
    exit 1
fi

echo ""
echo "Starting the app..."
adb shell am start -n com.catcher.pogoauto/.MainActivity
if [ $? -ne 0 ]; then
    echo "Error: Failed to start the app."
    exit 1
fi

echo ""
echo "Capturing logs..."
echo "Press Ctrl+C to stop capturing logs."
echo ""

adb logcat -v threadtime | grep -i -E "pogoauto|frida|zygote|pokemon"

echo ""
echo "Debug session ended."
