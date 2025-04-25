# Pokemon GO Auto Catcher - Keystore Encoder for GitHub
# This script properly encodes a keystore file for use with GitHub Actions

# Check if keystore.jks exists
if (-not (Test-Path -Path "keystore.jks")) {
    Write-Host "Error: keystore.jks file not found." -ForegroundColor Red
    Write-Host "Please run create-keystore.bat first to create a keystore." -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit
}

Write-Host "===== Pokemon GO Auto Catcher - Keystore Encoder for GitHub =====" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script will encode your keystore file for use with GitHub Actions." -ForegroundColor White
Write-Host ""

# Read the keystore file as bytes and convert to base64
Write-Host "Reading keystore.jks and converting to base64..." -ForegroundColor Yellow
$bytes = [System.IO.File]::ReadAllBytes("$PWD\keystore.jks")
$base64 = [System.Convert]::ToBase64String($bytes)

# Save the base64 string to a file (single line, no breaks)
$base64 | Out-File -FilePath "keystore.github.txt" -NoNewline

Write-Host ""
Write-Host "Success! The base64-encoded keystore has been saved to:" -ForegroundColor Green
Write-Host "keystore.github.txt" -ForegroundColor White
Write-Host ""
Write-Host "Instructions:" -ForegroundColor Yellow
Write-Host "1. Open keystore.github.txt in a text editor" -ForegroundColor White
Write-Host "2. Copy the entire content (it's a single line with no breaks)" -ForegroundColor White
Write-Host "3. Go to your GitHub repository settings" -ForegroundColor White
Write-Host "4. Navigate to Secrets and Variables -> Actions" -ForegroundColor White
Write-Host "5. Create the following secrets:" -ForegroundColor White
Write-Host "   - RELEASE_KEYSTORE: Paste the copied base64 content" -ForegroundColor White
Write-Host "   - SIGNING_KEY_ALIAS: Your key alias" -ForegroundColor White
Write-Host "   - SIGNING_KEY_PASSWORD: Your key password" -ForegroundColor White
Write-Host "   - SIGNING_STORE_PASSWORD: Your keystore password" -ForegroundColor White
Write-Host ""
Write-Host "Note: The keystore.github.txt file is added to .gitignore to prevent accidental commits." -ForegroundColor Yellow
Write-Host ""

Read-Host "Press Enter to exit"
