# Pokémon GO Activity Tracer - Desktop Receiver

This is a desktop application that receives and displays Pokémon GO activity logs from the Android app.

## Features

- Receives logs in real-time from the Android app
- Colorized display of different log categories with emoji indicators
- Enhanced JSON formatting for better readability
- Real-time statistics tracking for different activity types
- Filtering options to focus on specific types of activity
- Dark mode support
- Saves all logs to a file for later analysis

## Installation

1. Make sure you have Python 3.6+ installed on your computer
2. Install the required dependencies:

```
pip install -r requirements.txt
```

## Usage

### Basic Usage

Run the log receiver with default settings:

```
python LogReceiver.py
```

This will start the receiver on the default port (9999).

### Advanced Options

```
python LogReceiver.py [port] [--filter category1,category2,...] [--dark-mode] [--stats]
```

- `port`: Optional port number (default: 9999)
- `--filter`: Comma-separated list of categories to display (e.g., CAPTURE,ENCOUNTER)
- `--dark-mode`: Use dark mode (black background)
- `--stats`: Enable real-time statistics tracking (press 's' to view stats)

### Available Filter Categories

- `CAPTURE`: Pokéball throws and captures
- `ENCOUNTER`: Pokémon encounters
- `MOVEMENT`: Player movement
- `ITEM`: Item usage
- `NETWORK`: Network requests
- `GYM`: Gym interactions and battles
- `RAID`: Raid battles
- `POKESTOP`: PokéStop interactions
- `FRIEND`: Friend interactions (gifts, etc.)
- `COLLECTION`: Pokémon collection changes (transfers, evolutions, etc.)
- `INIT`: Initialization messages
- `FRIDA`: Frida system messages
- `D`, `I`, `W`, `E`: Debug, Info, Warning, and Error log levels

### Examples

Display only capture and encounter events:
```
python LogReceiver.py --filter CAPTURE,ENCOUNTER
```

Use a different port with dark mode:
```
python LogReceiver.py 8888 --dark-mode
```

Enable statistics tracking with filtering:
```
python LogReceiver.py --filter GYM,RAID,POKESTOP --stats
```

Track all activities with dark mode and statistics:
```
python LogReceiver.py --dark-mode --stats
```

## Connecting the Android App

1. Make sure your Android device and PC are on the same network
2. In the app, go to Log Settings and enter your PC's IP address
3. Set the port to match this receiver's port (default: 9999)
4. Click 'Start Streaming' in the app

## Troubleshooting

- If you're not receiving logs, check that your firewall allows incoming connections on the specified port
- Make sure your Android device and PC are on the same network
- Verify that the IP address in the app settings is correct
