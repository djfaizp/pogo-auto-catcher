#!/bin/bash
# This script helps debug the Pokemon GO Auto Catcher app in WSL environment

echo "Pokemon GO Auto Catcher WSL Debug Helper"
echo "========================================"
echo

# Check if we're running in WSL
if ! grep -q Microsoft /proc/version 2>/dev/null; then
    echo "This script is designed to run in Windows Subsystem for Linux (WSL)"
    echo "It appears you're not running in WSL."
    exit 1
fi

# Check if ADB is installed
if ! command -v adb &> /dev/null; then
    echo "ADB is not installed or not in PATH"
    echo "Please install Android SDK Platform Tools"
    exit 1
fi

# Function to check ADB connection
check_adb() {
    echo "Checking ADB connection..."
    if ! adb devices | grep -q "device$"; then
        echo "No Android device connected or authorized"
        echo "Please connect your device and enable USB debugging"
        return 1
    fi
    echo "Device connected successfully"
    return 0
}

# Function to install the app
install_app() {
    echo "Installing the app..."
    if [ ! -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
        echo "Debug APK not found at app/build/outputs/apk/debug/app-debug.apk"
        echo "Please build the app first with: ./gradlew assembleDebug"
        return 1
    fi
    
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    if [ $? -ne 0 ]; then
        echo "Failed to install the app"
        return 1
    fi
    echo "App installed successfully"
    return 0
}

# Function to start the app
start_app() {
    echo "Starting the app..."
    adb shell am start -n com.catcher.pogoauto/.MainActivity
    if [ $? -ne 0 ]; then
        echo "Failed to start the app"
        return 1
    fi
    echo "App started successfully"
    return 0
}

# Function to capture logs
capture_logs() {
    echo "Capturing logs... (Press Ctrl+C to stop)"
    adb logcat -v threadtime | grep -i -E "pogoauto|frida|zygote|pokemon"
}

# Function to check if Frida server is running
check_frida_server() {
    echo "Checking if Frida server is running..."
    if ! adb shell ps -A | grep -q frida-server; then
        echo "Frida server is not running"
        echo "Would you like to start it? (y/n)"
        read -r answer
        if [ "$answer" = "y" ]; then
            echo "Attempting to start Frida server..."
            adb shell "su -c '/data/local/tmp/frida-server &'" &>/dev/null
            sleep 2
            if ! adb shell ps -A | grep -q frida-server; then
                echo "Failed to start Frida server"
                echo "Please make sure it's installed at /data/local/tmp/frida-server"
                return 1
            fi
            echo "Frida server started successfully"
        else
            return 1
        fi
    else
        echo "Frida server is running"
    fi
    return 0
}

# Function to check if Pokemon GO is running
check_pokemon_go() {
    echo "Checking if Pokemon GO is running..."
    if ! adb shell ps -A | grep -q com.nianticlabs.pokemongo; then
        echo "Pokemon GO is not running"
        echo "Would you like to start it? (y/n)"
        read -r answer
        if [ "$answer" = "y" ]; then
            echo "Starting Pokemon GO..."
            adb shell monkey -p com.nianticlabs.pokemongo -c android.intent.category.LAUNCHER 1 &>/dev/null
            sleep 10
            if ! adb shell ps -A | grep -q com.nianticlabs.pokemongo; then
                echo "Failed to start Pokemon GO"
                return 1
            fi
            echo "Pokemon GO started successfully"
        else
            return 1
        fi
    else
        echo "Pokemon GO is running"
    fi
    return 0
}

# Function to run Python debug script
run_python_debug() {
    echo "Running Python debug script..."
    if ! command -v python3 &> /dev/null; then
        echo "Python 3 is not installed"
        return 1
    fi
    
    if ! command -v pip3 &> /dev/null; then
        echo "pip3 is not installed"
        return 1
    fi
    
    # Check if required packages are installed
    if ! python3 -c "import frida, colorama" &>/dev/null; then
        echo "Installing required Python packages..."
        pip3 install frida-tools colorama
    fi
    
    # Run the debug script
    python3 debug_frida.py --package com.nianticlabs.pokemongo --script app/src/main/assets/pokemon-go-hook.js
}

# Main menu
show_menu() {
    echo
    echo "WSL Debug Menu:"
    echo "1. Check ADB connection"
    echo "2. Install the app"
    echo "3. Start the app"
    echo "4. Capture logs"
    echo "5. Check Frida server"
    echo "6. Check Pokemon GO"
    echo "7. Run Python debug script"
    echo "8. Exit"
    echo
    echo -n "Enter your choice: "
    read -r choice
    
    case $choice in
        1) check_adb; press_enter ;;
        2) install_app; press_enter ;;
        3) start_app; press_enter ;;
        4) capture_logs ;;
        5) check_frida_server; press_enter ;;
        6) check_pokemon_go; press_enter ;;
        7) run_python_debug ;;
        8) exit 0 ;;
        *) echo "Invalid choice"; press_enter ;;
    esac
}

# Function to wait for user to press Enter
press_enter() {
    echo
    echo "Press Enter to continue..."
    read -r
}

# Main loop
while true; do
    clear
    show_menu
done
