#!/usr/bin/env python3
"""
Pokémon GO Auto Catcher Log Receiver
-----------------------------------
A simple UDP server that receives and displays logs from the Android app.

Usage:
    python LogReceiver.py [port] [--filter category1,category2,...] [--dark-mode] [--stats]

Default port is 9999 if not specified.
Optional filter categories: CAPTURE, ENCOUNTER, MOVEMENT, ITEM, NETWORK, INIT, GYM, RAID, POKESTOP, FRIEND, COLLECTION
Additional options:
    --dark-mode: Use dark mode (black background)
    --stats: Show real-time statistics
"""

import socket
import sys
import time
import os
import re
import json
import argparse
from datetime import datetime
from colorama import init, Fore, Style

# Initialize colorama
init(autoreset=True)

# Default port
DEFAULT_PORT = 9999

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
    "D": Fore.WHITE,
    "I": Fore.WHITE,
    "W": Fore.YELLOW,
    "E": Fore.RED
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
    "D": "🔍 ",
    "I": "ℹ️ ",
    "W": "⚠️ ",
    "E": "❌ "
}

def clear_screen():
    """Clear the console screen."""
    os.system('cls' if os.name == 'nt' else 'clear')

def get_timestamp():
    """Get current timestamp for log file."""
    return datetime.now().strftime("%Y%m%d_%H%M%S")

def format_message(message):
    """Format and colorize the message based on its content."""
    # Check if it's a trace message
    trace_match = re.search(r'\[TRACE\]\[(.*?)\]\[(.*?)\](.*)', message)
    if trace_match:
        timestamp = trace_match.group(1)
        category = trace_match.group(2)
        content = trace_match.group(3)

        # Extract JSON data if present
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

                json_data = f"\n  {json_formatted.replace('{', '').replace('}', '').replace('\n', '\n  ')}"
                content = content[:json_start].strip()
        except:
            pass

        color = CATEGORY_COLORS.get(category, Fore.WHITE)
        emoji = CATEGORY_EMOJIS.get(category, "")

        # Format timestamp to be more readable
        try:
            dt = datetime.fromisoformat(timestamp.replace('Z', '+00:00'))
            formatted_time = dt.strftime('%H:%M:%S.%f')[:-3]  # Show only milliseconds
        except:
            formatted_time = timestamp

        return f"{Fore.WHITE}[{formatted_time}] {color}{emoji}[{category}]{Style.RESET_ALL} {content}{json_data}"

    # Check if it's a standard log message
    log_match = re.search(r'\[(.*?)\] \[(.*?)/(.*?)\] (.*)', message)
    if log_match:
        timestamp = log_match.group(1)
        level = log_match.group(2)
        tag = log_match.group(3)
        content = log_match.group(4)

        color = CATEGORY_COLORS.get(level, Fore.WHITE)
        emoji = CATEGORY_EMOJIS.get(level, "")

        return f"{Fore.WHITE}[{timestamp}] {color}{emoji}[{level}/{tag}]{Style.RESET_ALL} {content}"

    # Default formatting
    return message

def should_display(message, filters):
    """Check if the message should be displayed based on filters."""
    if not filters:
        return True

    # Check if it's a trace message
    trace_match = re.search(r'\[TRACE\]\[(.*?)\]\[(.*?)\]', message)
    if trace_match:
        category = trace_match.group(2)
        return category in filters

    # Check if it's a standard log message
    log_match = re.search(r'\[(.*?)\] \[(.*?)/(.*?)\]', message)
    if log_match:
        level = log_match.group(2)
        return level in filters

    # Default: show message if no filter matches
    return True

def parse_args():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(description='Pokémon GO Auto Catcher Log Receiver')
    parser.add_argument('port', nargs='?', type=int, default=DEFAULT_PORT,
                        help=f'Port to listen on (default: {DEFAULT_PORT})')
    parser.add_argument('--filter', type=str, default='',
                        help='Comma-separated list of categories to display (e.g., CAPTURE,ENCOUNTER)')
    parser.add_argument('--dark-mode', action='store_true',
                        help='Use dark mode (black background)')
    parser.add_argument('--stats', action='store_true',
                        help='Show real-time statistics')
    return parser.parse_args()

def print_stats(stats, start_time):
    """Print statistics about received messages."""
    clear_screen()

    # Calculate elapsed time
    elapsed = time.time() - start_time
    hours, remainder = divmod(int(elapsed), 3600)
    minutes, seconds = divmod(remainder, 60)
    elapsed_str = f"{hours:02d}:{minutes:02d}:{seconds:02d}"

    # Print header
    print(f"{Fore.CYAN}=== Pokémon GO Activity Statistics ==={Style.RESET_ALL}")
    print(f"{Fore.CYAN}Session time: {elapsed_str}{Style.RESET_ALL}")
    print(f"{Fore.CYAN}Total messages: {sum(stats.values())}{Style.RESET_ALL}")
    print()

    # Print category counts
    print(f"{Fore.CYAN}Category Counts:{Style.RESET_ALL}")

    # Sort categories by count (descending)
    sorted_stats = sorted(stats.items(), key=lambda x: x[1], reverse=True)

    for category, count in sorted_stats:
        if count > 0:
            color = CATEGORY_COLORS.get(category, Fore.WHITE)
            emoji = CATEGORY_EMOJIS.get(category, "")
            print(f"{color}{emoji}[{category}]{Style.RESET_ALL}: {count}")

    print()
    print(f"{Fore.GREEN}Press Ctrl+C to exit or 'r' to return to log view{Style.RESET_ALL}")

    # Wait for user input
    try:
        # Wait for a key press
        print(f"{Fore.GREEN}Press any key to return to log view...{Style.RESET_ALL}")

        # Wait for input (works on all platforms)
        input()

        clear_screen()
        return
    except KeyboardInterrupt:
        sys.exit(0)
    except Exception as e:
        print(f"Error reading input: {e}")
        time.sleep(2)
        return

def main():
    # Parse command line arguments
    args = parse_args()
    port = args.port

    # Set up filters
    filters = [f.strip() for f in args.filter.split(',')] if args.filter else []

    # Set background color for dark mode
    if args.dark_mode:
        os.system('color 0f')  # Black background, white text (Windows)

    # Create UDP socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

    # Bind socket to port
    server_address = ('0.0.0.0', port)
    print(f"{Fore.CYAN}Starting Pokémon GO Auto Catcher Log Receiver on port {port}...{Style.RESET_ALL}")
    sock.bind(server_address)

    # Create log file
    log_filename = f"pogo_log_{get_timestamp()}.txt"
    print(f"{Fore.CYAN}Saving logs to {log_filename}{Style.RESET_ALL}")

    # Print instructions
    print(f"\n{Fore.YELLOW}Instructions:{Style.RESET_ALL}")
    print("1. Make sure your Android device and PC are on the same network")
    print("2. In the app, go to Log Settings and enter your PC's IP address")
    print("3. Set the port to match this receiver's port (default: 9999)")
    print("4. Click 'Start Streaming' in the app")

    if args.stats:
        print(f"5. Press 's' to view statistics")

    if filters:
        print(f"\n{Fore.YELLOW}Filtering:{Style.RESET_ALL} Only showing categories: {', '.join(filters)}")

    print(f"\n{Fore.GREEN}Waiting for logs... Press Ctrl+C to exit.{Style.RESET_ALL}\n")

    try:
        with open(log_filename, 'w', encoding='utf-8') as log_file:
            log_file.write(f"=== Pokémon GO Auto Catcher Log Receiver ===\n")
            log_file.write(f"Started: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n")

            # Stats for connection
            messages_received = 0
            last_stats_time = time.time()
            start_time = time.time()

            # Category statistics
            category_stats = {category: 0 for category in CATEGORY_COLORS.keys()}

            # View mode (logs or stats)
            view_stats = False

            # Set socket to non-blocking mode
            sock.setblocking(False)

            while True:
                # Check for keyboard input if stats mode is enabled
                if args.stats and sys.stdin.isatty():
                    if sys.platform == 'win32':
                        import msvcrt
                        if msvcrt.kbhit():
                            key = msvcrt.getch().decode('utf-8').lower()
                            if key == 's':
                                view_stats = True
                                print_stats(category_stats, start_time)
                    # Note: Unix/Linux input handling is more complex and would need to be implemented separately

                # Try to receive data (non-blocking)
                try:
                    data, address = sock.recvfrom(4096)
                    message = data.decode('utf-8', errors='replace')

                    # Get client IP and port
                    client_ip = address[0]
                    client_port = address[1]

                    # Update stats
                    messages_received += 1

                    # Update category stats
                    trace_match = re.search(r'\[TRACE\]\[[^]]*\]\[([^]]+)\]', message)
                    if trace_match:
                        category = trace_match.group(1)
                        if category in category_stats:
                            category_stats[category] += 1

                    log_match = re.search(r'\[[^]]*\] \[([^/]+)/[^]]*\]', message)
                    if log_match:
                        level = log_match.group(1)
                        if level in category_stats:
                            category_stats[level] += 1

                    current_time = time.time()
                    if current_time - last_stats_time >= 60:  # Show stats every minute
                        if not view_stats:
                            print(f"{Fore.CYAN}[INFO] Received {messages_received} messages in the last minute from {client_ip}:{client_port}{Style.RESET_ALL}")
                        messages_received = 0
                        last_stats_time = current_time

                    # Check if we should display this message
                    if should_display(message, filters) and not view_stats:
                        # Format and print message to console
                        formatted_message = format_message(message)
                        print(formatted_message)

                    # Write original message to log file (unformatted)
                    log_file.write(f"{message}\n")
                    log_file.flush()

                except BlockingIOError:
                    # No data available, just continue
                    pass

                # Small sleep to prevent CPU hogging
                time.sleep(0.01)

    except KeyboardInterrupt:
        print(f"\n{Fore.YELLOW}Shutting down...{Style.RESET_ALL}")
    finally:
        sock.close()
        print(f"{Fore.GREEN}Log receiver closed. Logs saved to {log_filename}{Style.RESET_ALL}")

if __name__ == "__main__":
    main()
