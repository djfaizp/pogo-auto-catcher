# Pokémon GO Auto Catcher

An Android application that uses Frida Gadget to hook into Pokémon GO and implement various cheats using frida-il2cpp-bridge.

## Features

- Perfect Throw: Always hits inside the smallest circle with a curveball
- More features coming soon...

## Requirements

- Rooted Android device
- Pokémon GO installed on the device
- Android 7.0+ (API level 24+)

## How It Works

This application uses Frida Gadget to hook into the Pokémon GO process and modify its behavior at runtime. The app injects JavaScript code that uses frida-il2cpp-bridge to interact with the Unity IL2CPP runtime of Pokémon GO.

### Technical Details

1. The app loads the Frida Gadget library (`libfrida-gadget.so`) at startup
2. When you launch Pokémon GO from the app, it extracts and configures the Frida script
3. The script hooks into the `AttemptCapture` method in the `EncounterInteractionState` class
4. When you throw a Pokéball, the script modifies the throw parameters to ensure a perfect throw

## Setup Instructions

### Prerequisites

- A rooted Android device
- Pokémon GO installed
- Basic knowledge of Android development

### Building the App

1. Clone this repository
2. Open the project in Android Studio
3. Build and install the app on your rooted device

### Using the App

1. Launch the app
2. Toggle the cheats you want to enable
3. Press the "Launch Pokémon GO" button
4. Enjoy your enhanced Pokémon GO experience!

## Warning

**Use at your own risk!** Using this app may violate Pokémon GO's Terms of Service and could result in your account being banned. This app is provided for educational purposes only.

## Credits

This project is based on the guide "How to make simple Pokemon Go cheats" and uses the frida-il2cpp-bridge library.

## License

This project is licensed under the MIT License - see the LICENSE file for details.
