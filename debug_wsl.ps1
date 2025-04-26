# PowerShell script to launch the WSL debugging environment for Pokemon GO Auto Catcher

Write-Host "Pokemon GO Auto Catcher WSL Debug Helper" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host

# Check if WSL is installed
try {
    $wslCheck = wsl --list
    if ($LASTEXITCODE -ne 0) {
        Write-Host "WSL is not installed or not working properly." -ForegroundColor Red
        Write-Host "Please install WSL by running 'wsl --install' in an elevated PowerShell prompt." -ForegroundColor Yellow
        exit 1
    }
} catch {
    Write-Host "Error checking WSL: $_" -ForegroundColor Red
    exit 1
}

# Check if the debug_wsl.sh script exists
if (-not (Test-Path "debug_wsl.sh")) {
    Write-Host "The debug_wsl.sh script was not found." -ForegroundColor Red
    exit 1
}

# Make sure the script is executable in WSL
try {
    wsl chmod +x debug_wsl.sh
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Failed to make the debug script executable." -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "Error making script executable: $_" -ForegroundColor Red
    exit 1
}

# Launch the WSL debug script
Write-Host "Launching WSL debug environment..." -ForegroundColor Green
try {
    wsl ./debug_wsl.sh
} catch {
    Write-Host "Error launching WSL debug environment: $_" -ForegroundColor Red
    exit 1
}
