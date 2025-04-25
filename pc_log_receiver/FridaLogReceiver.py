#!/usr/bin/env python3
"""
Pokémon GO Auto Catcher Frida Log Receiver
-----------------------------------------
A Python script that connects to Frida on the Android device and captures logs from the injected script.

Usage:
    python FridaLogReceiver.py [--device DEVICE_ID] [--package PACKAGE_NAME] [--output OUTPUT_FILE] [--dark-mode]

Options:
    --device DEVICE_ID     : Frida device ID (default: USB device)
    --package PACKAGE_NAME : Package name of Pokémon GO (default: com.nianticlabs.pokemongo)
    --output OUTPUT_FILE   : File to save logs to (default: frida_logs_TIMESTAMP.txt)
    --dark-mode            : Use dark mode (black background)
"""

import frida
import sys
import time
import os
import re
import json
import argparse
from datetime import datetime
from colorama import init, Fore, Style, Back

# Initialize colorama
init(autoreset=True)

# Default package name for Pokémon GO
DEFAULT_PACKAGE = "com.nianticlabs.pokemongo"

# Color mapping for different log categories
CATEGORY_COLORS = {
    "CAPTURE": Fore.GREEN,
    "ENCOUNTER": Fore.CYAN,
    "MOVEMENT": Fore.BLUE,
    "ITEM": Fore.YELLOW,
    "NETWORK": Fore.MAGENTA,
    "GYM": Fore.RED,
    "RAID": Fore.LIGHTRED_EX,
    "POKESTOP": Fore.LIGHTBLUE_EX,
    "FRIEND": Fore.LIGHTGREEN_EX,
    "COLLECTION": Fore.LIGHTMAGENTA_EX,
    "INIT": Fore.WHITE,
    "FRIDA": Fore.LIGHTWHITE_EX,
    "AR": Fore.BLUE,
    "UNITY": Fore.LIGHTGREEN_EX,
    "FIREBASE": Fore.LIGHTRED_EX,
    "PHYSICS": Fore.CYAN,
    "AUTH": Fore.LIGHTMAGENTA_EX,
    "D": Fore.WHITE,
    "I": Fore.WHITE,
    "W": Fore.YELLOW,
    "E": Fore.RED,
    # New debug categories
    "DEBUG": Fore.LIGHTCYAN_EX,
    "ERROR": Fore.LIGHTRED_EX,
    "FRIDA_HOOK": Fore.LIGHTWHITE_EX,
    "PERF": Fore.LIGHTGREEN_EX,
    "ASSEMBLY": Fore.LIGHTBLUE_EX,
    "DIAGNOSTICS": Fore.LIGHTMAGENTA_EX
}

# Emoji mapping for different log categories
CATEGORY_EMOJIS = {
    "CAPTURE": "🔴 ",
    "ENCOUNTER": "👀 ",
    "MOVEMENT": "🚶 ",
    "ITEM": "🎒 ",
    "NETWORK": "📡 ",
    "GYM": "🏋️ ",
    "RAID": "⚔️ ",
    "POKESTOP": "🔵 ",
    "FRIEND": "👫 ",
    "COLLECTION": "📚 ",
    "INIT": "🚀 ",
    "FRIDA": "🔧 ",
    "AR": "📷 ",
    "UNITY": "🎮 ",
    "FIREBASE": "🔥 ",
    "PHYSICS": "🧲 ",
    "AUTH": "🔑 ",
    "D": "🔍 ",
    "I": "ℹ️ ",
    "W": "⚠️ ",
    "E": "❌ ",
    # New debug categories
    "DEBUG": "🔍 ",
    "ERROR": "❌ ",
    "FRIDA_HOOK": "🔌 ",
    "PERF": "⏱️ ",
    "ASSEMBLY": "📦 ",
    "DIAGNOSTICS": "🔬 "
}

def clear_screen():
    """Clear the console screen."""
    os.system('cls' if os.name == 'nt' else 'clear')

def get_timestamp():
    """Get current timestamp for log file."""
    return datetime.now().strftime("%Y%m%d_%H%M%S")

def extract_json_data(content):
    """Extract and format JSON data from a log message."""
    json_data = ""
    try:
        # Find JSON object in the content
        json_start = content.find('{')
        if json_start != -1:
            json_str = content[json_start:]
            json_obj = json.loads(json_str)
            # Format JSON data with better indentation and coloring
            json_formatted = json.dumps(json_obj, indent=2)
            # Add color to keys and values
            json_formatted = re.sub(r'"([^"]+)":', f"{Fore.YELLOW}\"\\1\"{Style.RESET_ALL}:", json_formatted)
            json_formatted = re.sub(r': "([^"]+)"', f": {Fore.GREEN}\"\\1\"{Style.RESET_ALL}", json_formatted)
            json_formatted = re.sub(r': (true|false|null)', f": {Fore.CYAN}\\1{Style.RESET_ALL}", json_formatted)
            json_formatted = re.sub(r': ([0-9]+)', f": {Fore.BLUE}\\1{Style.RESET_ALL}", json_formatted)

            # Format the JSON data for display
            formatted_json = json_formatted.replace('{', '').replace('}', '')
            formatted_json = formatted_json.replace('\n', '\n  ')
            json_data = f"\n  {formatted_json}"
            content = content[:json_start].strip()
            return content, json_data
    except:
        pass
    return content, json_data

def format_timestamp(timestamp):
    """Format timestamp to be more readable."""
    try:
        dt = datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
        return dt.strftime('%H:%M:%S.%f')[:-3]  # Show only milliseconds
    except:
        return timestamp

def format_message(message):
    """Format and colorize the message based on its content."""
    # Check if it's a trace message
    trace_match = re.search(r'\[TRACE\]\[(.*?)\]\[(.*?)\](.*)', message)
    if trace_match:
        timestamp = trace_match.group(1)
        category = trace_match.group(2)
        content = trace_match.group(3)

        # Extract JSON data if present
        content, json_data = extract_json_data(content)

        color = CATEGORY_COLORS.get(category, Fore.WHITE)
        emoji = CATEGORY_EMOJIS.get(category, "")
        formatted_time = format_timestamp(timestamp)

        return f"{Fore.WHITE}[{formatted_time}] {color}{emoji}[{category}]{Style.RESET_ALL} {content}{json_data}"

    # Check if it's a debug message
    debug_match = re.search(r'\[DEBUG\]\[(.*?)\]\[FRIDA_HOOK\]\[(.*?)\](.*)', message)
    if debug_match:
        timestamp = debug_match.group(1)
        category = debug_match.group(2)
        content = debug_match.group(3)

        # Extract JSON data if present
        content, json_data = extract_json_data(content)

        color = CATEGORY_COLORS.get(category, CATEGORY_COLORS.get("DEBUG", Fore.LIGHTCYAN_EX))
        emoji = CATEGORY_EMOJIS.get(category, CATEGORY_EMOJIS.get("DEBUG", "🔍 "))
        formatted_time = format_timestamp(timestamp)

        return f"{Fore.WHITE}[{formatted_time}] {Fore.LIGHTCYAN_EX}🔍 [DEBUG] {color}{emoji}[{category}]{Style.RESET_ALL} {content}{json_data}"

    # Check if it's an error message
    error_match = re.search(r'\[ERROR\]\[(.*?)\]\[FRIDA_HOOK\]\[(.*?)\](.*)', message)
    if error_match:
        timestamp = error_match.group(1)
        category = error_match.group(2)
        content = error_match.group(3)

        # Extract JSON data if present
        content, json_data = extract_json_data(content)

        color = CATEGORY_COLORS.get(category, CATEGORY_COLORS.get("ERROR", Fore.LIGHTRED_EX))
        emoji = CATEGORY_EMOJIS.get(category, CATEGORY_EMOJIS.get("ERROR", "❌ "))
        formatted_time = format_timestamp(timestamp)

        return f"{Fore.WHITE}[{formatted_time}] {Fore.LIGHTRED_EX}❌ [ERROR] {color}{emoji}[{category}]{Style.RESET_ALL} {content}{json_data}"

    # Check if it's a Frida hook message with a different prefix
    frida_hook_match = re.search(r'\[(.*?)\]\[(.*?)\]\[FRIDA_HOOK\]\[(.*?)\](.*)', message)
    if frida_hook_match:
        prefix = frida_hook_match.group(1)
        timestamp = frida_hook_match.group(2)
        category = frida_hook_match.group(3)
        content = frida_hook_match.group(4)

        # Extract JSON data if present
        content, json_data = extract_json_data(content)

        color = CATEGORY_COLORS.get(category, Fore.WHITE)
        emoji = CATEGORY_EMOJIS.get(category, "")
        formatted_time = format_timestamp(timestamp)

        return f"{Fore.WHITE}[{formatted_time}] {Fore.LIGHTWHITE_EX}🔌 [{prefix}] {color}{emoji}[{category}]{Style.RESET_ALL} {content}{json_data}"

    # Check if it's a standard log message
    log_match = re.search(r'\[(.*?)\] (.*)', message)
    if log_match:
        prefix = log_match.group(1)
        content = log_match.group(2)

        # Try to determine the category from the prefix
        category = None
        for cat in CATEGORY_COLORS.keys():
            if cat in prefix:
                category = cat
                break

        color = CATEGORY_COLORS.get(category, Fore.WHITE) if category else Fore.WHITE
        emoji = CATEGORY_EMOJIS.get(category, "") if category else ""

        return f"{Fore.WHITE}[{prefix}] {color}{emoji}{content}"

    # Default formatting
    return message

def on_message(message, _data):
    """Callback for Frida messages."""
    if message['type'] == 'send':
        # Get the payload
        payload = message['payload']

        # Format the message
        formatted = format_message(payload)

        # Print to console
        print(formatted)

        # Write to log file
        if log_file:
            log_file.write(f"{payload}\n")
            log_file.flush()
    elif message['type'] == 'error':
        print(f"{Fore.RED}[ERROR] {message['stack']}{Style.RESET_ALL}")
        if log_file:
            log_file.write(f"[ERROR] {message['stack']}\n")
            log_file.flush()

def parse_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description='Pokémon GO Auto Catcher Frida Log Receiver')
    parser.add_argument('--device', type=str, default=None,
                        help='Frida device ID (default: USB device)')
    parser.add_argument('--package', type=str, default=DEFAULT_PACKAGE,
                        help=f'Package name of Pokémon GO (default: {DEFAULT_PACKAGE})')
    parser.add_argument('--output', type=str, default=None,
                        help='File to save logs to (default: frida_logs_TIMESTAMP.txt)')
    parser.add_argument('--dark-mode', action='store_true',
                        help='Use dark mode (black background)')
    return parser.parse_args()

def main():
    global log_file

    # Parse command line arguments
    args = parse_args()

    # Set background color for dark mode
    if args.dark_mode:
        os.system('color 0f')  # Black background, white text (Windows)
        print(f"{Back.BLACK}{Fore.WHITE}", end="")

    # Set up log file
    log_filename = args.output if args.output else f"frida_logs_{get_timestamp()}.txt"

    try:
        # Get the device
        if args.device:
            device = frida.get_device(args.device)
        else:
            device = frida.get_usb_device()

        print(f"{Fore.CYAN}Connected to device: {device.id}{Style.RESET_ALL}")

        # Get the target package
        package_name = args.package
        print(f"{Fore.CYAN}Target package: {package_name}{Style.RESET_ALL}")

        # Open log file
        log_file = open(log_filename, 'w', encoding='utf-8')
        log_file.write(f"=== Pokémon GO Auto Catcher Frida Log Receiver ===\n")
        log_file.write(f"Started: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        log_file.write(f"Device: {device.id}\n")
        log_file.write(f"Package: {package_name}\n\n")

        # Attach to the process
        print(f"{Fore.CYAN}Attaching to {package_name}...{Style.RESET_ALL}")
        process = device.attach(package_name)

        # Create a more comprehensive script to capture all console output
        script_code = """
        // Capture all console output
        console.log = function(message) {
            send(message);
        };

        // Also capture console.error
        console.error = function(message) {
            send("[ERROR] " + message);
        };

        // Also capture console.warn
        console.warn = function(message) {
            send("[WARNING] " + message);
        };

        // Also capture console.info
        console.info = function(message) {
            send("[INFO] " + message);
        };

        // Also capture console.debug
        console.debug = function(message) {
            send("[DEBUG] " + message);
        };

        // Log script initialization
        send("[DEBUG][" + new Date().toISOString() + "][FRIDA_HOOK][INIT] Frida log capture script initialized");

        // Run a self-test to verify Frida is working
        send("FRIDA_SELF_TEST: PC Log Receiver script is running");

        // Try to detect if the main Frida script is loaded
        try {
            if (typeof Il2Cpp !== 'undefined') {
                send("FRIDA_SELF_TEST: Il2Cpp is available");
            } else {
                send("FRIDA_SELF_TEST: Il2Cpp is NOT available");
            }

            if (typeof Frida !== 'undefined') {
                send("FRIDA_SELF_TEST: Frida is available, version: " + Frida.version);
            } else {
                send("FRIDA_SELF_TEST: Frida is NOT available");
            }
        } catch (e) {
            send("FRIDA_SELF_TEST: Error in self-test: " + e);
        }
        """

        # Create and load the script
        script = process.create_script(script_code)
        script.on('message', on_message)
        script.load()

        print(f"{Fore.GREEN}Successfully attached to {package_name}{Style.RESET_ALL}")
        print(f"{Fore.CYAN}Saving logs to {log_filename}{Style.RESET_ALL}")
        print(f"\n{Fore.GREEN}Waiting for logs... Press Ctrl+C to exit.{Style.RESET_ALL}\n")

        # Keep the script running
        while True:
            time.sleep(0.1)

    except frida.ServerNotRunningError:
        print(f"{Fore.RED}Error: Frida server is not running on the device.{Style.RESET_ALL}")
        print(f"{Fore.YELLOW}Make sure the Frida server is running on your Android device.{Style.RESET_ALL}")
        print(f"{Fore.YELLOW}You can start it with 'adb shell /data/local/tmp/frida-server &'{Style.RESET_ALL}")

    except frida.ProcessNotFoundError:
        print(f"{Fore.RED}Error: Process {package_name} not found.{Style.RESET_ALL}")
        print(f"{Fore.YELLOW}Make sure Pokémon GO is running on your device.{Style.RESET_ALL}")

    except KeyboardInterrupt:
        print(f"\n{Fore.YELLOW}Shutting down...{Style.RESET_ALL}")

    except Exception as e:
        print(f"{Fore.RED}Error: {str(e)}{Style.RESET_ALL}")

    finally:
        if 'log_file' in globals() and log_file:
            log_file.close()
            print(f"{Fore.GREEN}Log receiver closed. Logs saved to {log_filename}{Style.RESET_ALL}")

if __name__ == "__main__":
    main()
