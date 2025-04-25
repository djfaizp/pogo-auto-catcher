@echo off
echo Rebuilding the app with Frida gadget fixes...

REM Set JAVA_HOME to common Android Studio JDK locations
if exist "C:\Program Files\Android\Android Studio\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    echo Set JAVA_HOME to %JAVA_HOME%
) else if exist "C:\Program Files\Android\Android Studio\jre" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jre"
    echo Set JAVA_HOME to %JAVA_HOME%
) else (
    echo WARNING: Could not find Android Studio JDK. Please set JAVA_HOME manually.
    echo Attempting to continue anyway...
)

REM Add Java to PATH
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Cleaning the project...
call .\gradlew.bat clean

echo Building the app...
call .\gradlew.bat :app:assembleDebug --info

echo Installing the app...
adb install -r app\build\outputs\apk\debug\app-debug.apk

echo Done!
echo Please launch the app and check if the Frida gadget library is now properly loaded.
pause
