#!/usr/bin/env python3
"""
Debug script for Frida integration with Pokémon GO Auto Catcher
This script helps diagnose issues with Frida injection and monitoring
"""

import os
import sys
import time
import argparse
import subprocess
import frida
import colorama
from colorama import Fore, Back, Style

# Initialize colorama
colorama.init()

def parse_args():
    parser = argparse.ArgumentParser(description='Debug Frida integration with Pokémon GO')
    parser.add_argument('--package', '-p', default='com.nianticlabs.pokemongo',
                        help='Package name of Pokémon GO (default: com.nianticlabs.pokemongo)')
    parser.add_argument('--script', '-s', default='app/src/main/assets/pokemon-go-hook.js',
                        help='Path to the Frida script to inject')
    parser.add_argument('--dark-mode', '-d', action='store_true',
                        help='Use dark mode for console output')
    parser.add_argument('--verbose', '-v', action='store_true',
                        help='Enable verbose output')
    return parser.parse_args()

def run_command(cmd, capture_output=True):
    """Run a shell command and return the output"""
    print(f"{Fore.CYAN}Running: {cmd}{Style.RESET_ALL}")
    try:
        if capture_output:
            result = subprocess.run(cmd, shell=True, check=False,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                                   universal_newlines=True)
            return result.stdout, result.stderr, result.returncode
        else:
            result = subprocess.run(cmd, shell=True, check=False)
            return None, None, result.returncode
    except Exception as e:
        print(f"{Fore.RED}Error running command: {e}{Style.RESET_ALL}")
        return None, str(e), -1

def check_adb_connection():
    """Check if ADB is connected to a device"""
    print(f"{Fore.GREEN}Checking ADB connection...{Style.RESET_ALL}")
    stdout, stderr, returncode = run_command("adb devices")

    if returncode != 0:
        print(f"{Fore.RED}Error: ADB connection failed. Make sure your device is connected and USB debugging is enabled.{Style.RESET_ALL}")
        return False

    # Check if any devices are connected
    lines = stdout.strip().split('\n')
    if len(lines) <= 1:
        print(f"{Fore.RED}Error: No devices connected.{Style.RESET_ALL}")
        return False

    print(f"{Fore.GREEN}ADB connection successful.{Style.RESET_ALL}")
    return True

def check_frida_server():
    """Check if Frida server is running on the device"""
    print(f"{Fore.GREEN}Checking Frida server...{Style.RESET_ALL}")

    # Use ps -A for better compatibility across Android versions
    stdout, stderr, returncode = run_command("adb shell ps -A | grep frida-server")

    if returncode != 0 or not stdout:
        print(f"{Fore.YELLOW}Warning: Frida server not running on device.{Style.RESET_ALL}")

        # Try to start Frida server
        print(f"{Fore.GREEN}Attempting to start Frida server...{Style.RESET_ALL}")

        # First check if frida-server exists on the device
        _, _, check_returncode = run_command("adb shell \"ls /data/local/tmp/frida-server\"")

        if check_returncode != 0:
            print(f"{Fore.YELLOW}Frida server not found in /data/local/tmp/. Checking if it's available elsewhere...{Style.RESET_ALL}")
            _, _, which_returncode = run_command("adb shell \"which frida-server\"")

            if which_returncode != 0:
                print(f"{Fore.RED}Error: frida-server not found on device. Please install it first.{Style.RESET_ALL}")
                print(f"{Fore.YELLOW}You can download it from https://github.com/frida/frida/releases{Style.RESET_ALL}")
                print(f"{Fore.YELLOW}and push it to the device with: adb push frida-server /data/local/tmp/{Style.RESET_ALL}")
                return False

        # Try to start the server with su
        _, _, returncode = run_command("adb shell \"su -c '/data/local/tmp/frida-server &'\"", capture_output=False)

        # If su fails, try without su (some devices have frida-server in PATH and don't need root)
        if returncode != 0:
            print(f"{Fore.YELLOW}Failed to start with su, trying without root...{Style.RESET_ALL}")
            _, _, returncode = run_command("adb shell \"/data/local/tmp/frida-server &\"", capture_output=False)

            if returncode != 0:
                print(f"{Fore.YELLOW}Failed to start from /data/local/tmp/, trying from PATH...{Style.RESET_ALL}")
                _, _, returncode = run_command("adb shell \"frida-server &\"", capture_output=False)

        if returncode != 0:
            print(f"{Fore.RED}Error: Failed to start Frida server.{Style.RESET_ALL}")
            return False

        # Wait for server to start
        print(f"{Fore.YELLOW}Waiting for Frida server to start...{Style.RESET_ALL}")
        time.sleep(3)

        # Check again
        stdout, stderr, returncode = run_command("adb shell ps -A | grep frida-server")
        if returncode != 0 or not stdout:
            print(f"{Fore.RED}Error: Frida server still not running after attempt to start.{Style.RESET_ALL}")
            return False

    print(f"{Fore.GREEN}Frida server is running.{Style.RESET_ALL}")
    return True

def check_pokemon_go(package_name):
    """Check if Pokémon GO is installed and running"""
    print(f"{Fore.GREEN}Checking Pokémon GO installation...{Style.RESET_ALL}")
    stdout, stderr, returncode = run_command(f"adb shell pm list packages | grep {package_name}")

    if returncode != 0 or not stdout:
        print(f"{Fore.RED}Error: Pokémon GO ({package_name}) is not installed.{Style.RESET_ALL}")
        return False, None

    print(f"{Fore.GREEN}Pokémon GO ({package_name}) is installed.{Style.RESET_ALL}")

    # Check if it's running - use ps -A for better compatibility
    print(f"{Fore.GREEN}Checking if Pokémon GO is running...{Style.RESET_ALL}")
    stdout, stderr, returncode = run_command(f"adb shell ps -A | grep {package_name}")

    if returncode != 0 or not stdout:
        print(f"{Fore.YELLOW}Pokémon GO is not running.{Style.RESET_ALL}")
        return True, None

    # Extract PID - handle different ps output formats
    try:
        pid = None
        for line in stdout.strip().split('\n'):
            if package_name in line:
                parts = line.strip().split()
                # Different Android versions have different ps output formats
                # Try to find the PID column
                if len(parts) > 1:
                    # Common formats:
                    # USER PID ... NAME
                    # PID USER ... NAME
                    # Try to determine which format we have
                    if parts[0].isdigit():
                        # First column is PID
                        pid = int(parts[0])
                    elif len(parts) > 1 and parts[1].isdigit():
                        # Second column is PID
                        pid = int(parts[1])
                    else:
                        # Try to find a column that looks like a PID
                        for part in parts:
                            if part.isdigit() and 1000 <= int(part) <= 99999:
                                pid = int(part)
                                break
                    break

        if pid:
            print(f"{Fore.GREEN}Pokémon GO is running with PID: {pid}{Style.RESET_ALL}")
            return True, pid
        else:
            print(f"{Fore.YELLOW}Pokémon GO is running but couldn't extract PID.{Style.RESET_ALL}")
            # Try an alternative method to get the PID
            stdout, stderr, returncode = run_command(f"adb shell pidof {package_name}")
            if returncode == 0 and stdout.strip():
                try:
                    pid = int(stdout.strip().split()[0])
                    print(f"{Fore.GREEN}Found PID using pidof: {pid}{Style.RESET_ALL}")
                    return True, pid
                except:
                    pass
            return True, None
    except Exception as e:
        print(f"{Fore.RED}Error extracting PID: {e}{Style.RESET_ALL}")
        return True, None

def check_frida_gadget(package_name):
    """Check if Frida gadget is loaded in Pokémon GO"""
    print(f"{Fore.GREEN}Checking if Frida gadget is loaded in Pokémon GO...{Style.RESET_ALL}")

    # First get the PID using our improved function
    installed, pid = check_pokemon_go(package_name)

    if not installed or pid is None:
        print(f"{Fore.YELLOW}Pokémon GO is not running or PID couldn't be determined, cannot check for Frida gadget.{Style.RESET_ALL}")
        return False

    # Check maps for Frida gadget
    stdout, stderr, returncode = run_command(f"adb shell \"cat /proc/{pid}/maps | grep -i 'frida\\|gadget'\"")

    if returncode != 0 or not stdout:
        print(f"{Fore.YELLOW}Frida gadget not found in Pokémon GO process maps.{Style.RESET_ALL}")

        # Try an alternative method - check loaded libraries
        print(f"{Fore.YELLOW}Trying alternative method to detect Frida...{Style.RESET_ALL}")
        stdout, stderr, returncode = run_command(f"adb shell \"ls -la /proc/{pid}/fd | grep -i 'frida\\|gadget'\"")

        if returncode != 0 or not stdout:
            # Try one more method - check memory regions
            stdout, stderr, returncode = run_command(f"adb shell \"cat /proc/{pid}/status | grep -i 'vm'\"")
            if returncode == 0 and stdout:
                print(f"{Fore.YELLOW}Process memory information:{Style.RESET_ALL}")
                for line in stdout.strip().split('\n'):
                    print(f"  {line}")

            # Check loaded libraries
            stdout, stderr, returncode = run_command(f"adb shell \"ls -la /proc/{pid}/map_files | grep -i '\\.so'\"")
            if returncode == 0 and stdout:
                print(f"{Fore.YELLOW}Checking loaded libraries:{Style.RESET_ALL}")
                libs = []
                for line in stdout.strip().split('\n'):
                    if ".so" in line.lower():
                        libs.append(line)

                if libs:
                    print(f"{Fore.YELLOW}Found {len(libs)} loaded libraries, showing first 5:{Style.RESET_ALL}")
                    for i, lib in enumerate(libs[:5]):
                        print(f"  {lib}")

            print(f"{Fore.YELLOW}Frida gadget not detected using alternative methods either.{Style.RESET_ALL}")
            return False
        else:
            print(f"{Fore.GREEN}Found potential Frida references in file descriptors:{Style.RESET_ALL}")
            for line in stdout.strip().split('\n'):
                print(f"  {line}")
            print(f"{Fore.GREEN}Frida might be loaded but not visible in maps.{Style.RESET_ALL}")
            return True

    print(f"{Fore.GREEN}Frida gadget is loaded in Pokémon GO!{Style.RESET_ALL}")
    print(f"{Fore.CYAN}Found these Frida-related entries in process maps:{Style.RESET_ALL}")
    for line in stdout.strip().split('\n'):
        print(f"  {line}")

    return True

def inject_frida_script(package_name, script_path, verbose=False):
    """Inject Frida script into Pokémon GO"""
    print(f"{Fore.GREEN}Attempting to inject Frida script into Pokémon GO...{Style.RESET_ALL}")

    # Check if script exists
    if not os.path.exists(script_path):
        print(f"{Fore.RED}Error: Script file not found: {script_path}{Style.RESET_ALL}")
        return False

    try:
        # Get a device
        device = frida.get_usb_device()

        # Attach to the process
        session = device.attach(package_name)

        # Read the script
        with open(script_path, 'r') as f:
            script_code = f.read()

        # Create a script
        script = session.create_script(script_code)

        # Define message handler
        def on_message(message, data):
            if message['type'] == 'send':
                print(f"{Fore.GREEN}[Frida] {message['payload']}{Style.RESET_ALL}")
            elif message['type'] == 'error':
                print(f"{Fore.RED}[Frida Error] {message['stack']}{Style.RESET_ALL}")
            else:
                print(f"{Fore.YELLOW}[Frida Message] {message}{Style.RESET_ALL}")

        # Set message handler
        script.on('message', on_message)

        # Load the script
        script.load()

        print(f"{Fore.GREEN}Successfully injected Frida script into Pokémon GO!{Style.RESET_ALL}")
        print(f"{Fore.CYAN}Press Ctrl+C to stop...{Style.RESET_ALL}")

        # Keep the script running
        while True:
            time.sleep(0.1)

    except frida.ProcessNotFoundError:
        print(f"{Fore.RED}Error: Process not found: {package_name}{Style.RESET_ALL}")
        return False
    except frida.ServerNotRunningError:
        print(f"{Fore.RED}Error: Frida server is not running on the device.{Style.RESET_ALL}")
        return False
    except Exception as e:
        print(f"{Fore.RED}Error injecting Frida script: {e}{Style.RESET_ALL}")
        return False

def main():
    args = parse_args()

    # Set background color for dark mode
    if args.dark_mode:
        os.system('color 0f')  # Black background, white text (Windows)
        print(f"{Back.BLACK}{Fore.WHITE}", end="")

    print(f"{Fore.CYAN}===== Pokémon GO Frida Debug Tool ====={Style.RESET_ALL}")
    print(f"{Fore.CYAN}Package: {args.package}{Style.RESET_ALL}")
    print(f"{Fore.CYAN}Script: {args.script}{Style.RESET_ALL}")
    print()

    # Check ADB connection
    if not check_adb_connection():
        return 1

    # Check Frida server
    if not check_frida_server():
        return 1

    # Check Pokémon GO
    installed, pid = check_pokemon_go(args.package)
    if not installed:
        return 1

    # If Pokémon GO is not running, ask to start it
    if pid is None:
        print(f"{Fore.YELLOW}Pokémon GO is not running. Do you want to start it? (y/n){Style.RESET_ALL}")
        choice = input().lower()
        if choice == 'y':
            print(f"{Fore.GREEN}Starting Pokémon GO...{Style.RESET_ALL}")
            run_command(f"adb shell monkey -p {args.package} -c android.intent.category.LAUNCHER 1", capture_output=False)

            # Wait for app to start
            print(f"{Fore.YELLOW}Waiting for Pokémon GO to start...{Style.RESET_ALL}")
            time.sleep(10)

            # Check again
            installed, pid = check_pokemon_go(args.package)
            if pid is None:
                print(f"{Fore.RED}Error: Failed to start Pokémon GO or get its PID.{Style.RESET_ALL}")
                return 1
        else:
            print(f"{Fore.YELLOW}Exiting as Pokémon GO is not running.{Style.RESET_ALL}")
            return 0

    # Check if Frida gadget is loaded
    gadget_loaded = check_frida_gadget(args.package)

    # Inject Frida script
    if gadget_loaded or True:  # Try anyway even if gadget not detected
        print(f"{Fore.GREEN}Attempting to inject Frida script...{Style.RESET_ALL}")
        inject_frida_script(args.package, args.script, args.verbose)
    else:
        print(f"{Fore.RED}Cannot inject Frida script as Frida gadget is not loaded.{Style.RESET_ALL}")
        return 1

    return 0

if __name__ == "__main__":
    try:
        # Make the script executable in WSL
        if os.name != 'nt':  # Not Windows
            os.chmod(__file__, 0o755)
        sys.exit(main())
    except KeyboardInterrupt:
        print(f"{Fore.YELLOW}\nExiting due to user interrupt.{Style.RESET_ALL}")
        sys.exit(0)
    except Exception as e:
        print(f"{Fore.RED}\nUnexpected error: {e}{Style.RESET_ALL}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
