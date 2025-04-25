# Set JAVA_HOME to a common location for Android Studio
$javaHome = "C:\Program Files\Android\Android Studio\jbr"
if (Test-Path $javaHome) {
    $env:JAVA_HOME = $javaHome
    Write-Host "Set JAVA_HOME to $javaHome"
} else {
    # Try alternative locations
    $javaHome = "C:\Program Files\Android\Android Studio\jre"
    if (Test-Path $javaHome) {
        $env:JAVA_HOME = $javaHome
        Write-Host "Set JAVA_HOME to $javaHome"
    } else {
        Write-Host "Could not find Java installation. Please set JAVA_HOME manually."
        exit 1
    }
}

# Add Java to PATH
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Navigate to project directory
Set-Location C:\Users\Alex\pogo-auto-catcher-1

# Run Gradle build
.\gradlew.bat :app:assembleDebug --info
