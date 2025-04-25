# Pokémon GO Auto Catcher

An Android application that uses Frida Gadget to hook into Pokémon GO and implement various cheats using frida-il2cpp-bridge.

## Aplication is still under development


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

#### Debug Build

1. Clone this repository
2. Open the project in Android Studio
3. Build and install the app on your rooted device

#### Release Build

To create a signed release build:

1. Run the `create-keystore.bat` script to generate a keystore file
2. Follow the prompts to create your keystore
3. Build the release APK in Android Studio or using Gradle:
   ```
   ./gradlew assembleRelease
   ```
4. The signed APK will be available at `app/build/outputs/apk/release/app-release.apk`

### Creating GitHub Releases

This project is configured with GitHub Actions to automatically build and publish release APKs when a new tag is pushed or when manually triggered.

To create a new release:

1. Push a new tag with a version number:
   ```
   git tag -a v1.0.1 -m "Version 1.0.1"
   git push origin v1.0.1
   ```

2. Or manually trigger the workflow from the GitHub Actions tab and provide the version name and code.

3. GitHub Actions will build the release APK, sign it with the keystore (stored in GitHub Secrets), and create a new release with the APK attached.

#### Required GitHub Secrets

For the automated release process to work, you need to set up the following secrets in your GitHub repository:

- `RELEASE_KEYSTORE`: Base64-encoded keystore file
- `SIGNING_KEY_ALIAS`: The alias used when creating the keystore
- `SIGNING_KEY_PASSWORD`: The password for the key
- `SIGNING_STORE_PASSWORD`: The password for the keystore

#### Creating and Encoding a Keystore for GitHub

##### Windows Users:
1. Run the `create-keystore.bat` script to generate a keystore file
2. Run the `encode-keystore-for-github.ps1` PowerShell script to encode it properly for GitHub
3. Copy the content of the generated `keystore.github.txt` file
4. Add this as the `RELEASE_KEYSTORE` secret in your GitHub repository

##### Linux/Mac Users:
1. Make the script executable: `chmod +x create-github-keystore.sh`
2. Run the script: `./create-github-keystore.sh`
3. Follow the prompts to create your keystore
4. Copy the content of the generated `keystore.github.txt` file
5. Add this as the `RELEASE_KEYSTORE` secret in your GitHub repository

**Important**: The base64-encoded keystore must be a single line with no spaces or line breaks.

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
