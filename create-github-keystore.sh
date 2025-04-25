#!/bin/bash
# Script to create a keystore and encode it for GitHub Actions

echo "===== Pokemon GO Auto Catcher - GitHub Keystore Generator ====="
echo "=========================================================="
echo

# Check if keytool is available
if ! command -v keytool &> /dev/null; then
    echo "Error: keytool not found. Please install Java JDK."
    exit 1
fi

# Prompt for keystore information
read -p "Enter key alias (e.g., pogoauto): " KEY_ALIAS
read -sp "Enter key password: " KEY_PASSWORD
echo
read -sp "Enter keystore password: " STORE_PASSWORD
echo
read -p "Enter validity in years (e.g., 25): " VALIDITY

echo
echo "Creating keystore file..."
keytool -genkey -v -keystore keystore.jks -alias "$KEY_ALIAS" -keyalg RSA -keysize 2048 -validity $((VALIDITY*365)) \
    -storepass "$STORE_PASSWORD" -keypass "$KEY_PASSWORD" \
    -dname "CN=Pokemon GO Auto Catcher, OU=Development, O=PogoAuto, L=Unknown, ST=Unknown, C=US"

if [ $? -ne 0 ]; then
    echo "Failed to create keystore file."
    exit 1
fi

echo
echo "Keystore file created successfully: keystore.jks"

# Encode the keystore for GitHub
echo
echo "Encoding keystore for GitHub..."
base64 -w 0 keystore.jks > keystore.github.txt

echo
echo "Keystore encoded successfully to keystore.github.txt"
echo
echo "Instructions:"
echo "1. Copy the entire content of keystore.github.txt"
echo "2. Go to your GitHub repository settings"
echo "3. Navigate to Secrets and Variables -> Actions"
echo "4. Create the following secrets:"
echo "   - RELEASE_KEYSTORE: Paste the copied base64 content"
echo "   - SIGNING_KEY_ALIAS: $KEY_ALIAS"
echo "   - SIGNING_KEY_PASSWORD: Your key password"
echo "   - SIGNING_STORE_PASSWORD: Your keystore password"
echo
echo "Note: The keystore.github.txt file is added to .gitignore to prevent accidental commits."
echo

# Create a gradle.properties file with signing config
echo
echo "Creating gradle.properties with signing configuration..."
cat > gradle.properties << EOF
# Signing config
signing.keyAlias=$KEY_ALIAS
signing.keyPassword=$KEY_PASSWORD
signing.storePassword=$STORE_PASSWORD
signing.storeFile=keystore.jks
EOF

echo "gradle.properties created with signing configuration."
echo
echo "IMPORTANT: Do not commit gradle.properties to version control!"
echo "Make sure it's listed in .gitignore"
echo
