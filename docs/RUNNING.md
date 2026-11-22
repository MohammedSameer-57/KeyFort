# Running KeyFort

This document details the operational commands to build, test, and run KeyFort components.

## Prerequisites

- Java Development Kit (JDK) 17 or higher.
- Git.
- Optional: Android SDK (compileSdk 34) if compiling or testing `:keyfort-android`.

## Testing

Run the full automated test suite for available modules:

```bash
# Linux / macOS
./gradlew test

# Windows
.\gradlew.bat test
```

If the Android SDK is not detected in environment variables (`ANDROID_HOME`, `ANDROID_SDK_ROOT`) or `local.properties`, Gradle automatically skips `:keyfort-android` and runs tests for `:keyfort-core` and `:keyfort-server`.

To run tests for a specific module:

```bash
.\gradlew.bat :keyfort-core:test
.\gradlew.bat :keyfort-server:test
```

## Running the Server

### Local Development via Gradle

To start the Spring Boot service directly:

```bash
# Windows Batch
scripts\START_KEYFORT.bat

# Windows PowerShell
powershell -ExecutionPolicy Bypass -File scripts\START_KEYFORT.ps1

# Direct Gradle invocation
.\gradlew.bat :keyfort-server:run
```

The service listens on port 8080. By default, it operates with an in-memory H2 database.

### Docker Container

To package and run the server inside a container:

```bash
docker build -t keyfort-server .
docker run -p 8080:8080 -e KEYFORT_JWT_SECRET="replace-with-a-32-byte-secret-key-material" keyfort-server
```

## UI and Presentation Modes

### Simulation Prototype

The file `preview.html` contains an offline browser-based simulation prototype demonstrating the target workflow. To launch:

```bash
scripts\OPEN_DEMO.bat
```

Alternatively, open `preview.html` directly in any web browser.

Note: Until Phase 7 implementation is complete, `preview.html` and the hosted GitHub Pages site remain simulation prototypes and are not connected to the live backend server.

### Reverse Tunneling for Mobile Testing

To expose the local server running on port 8080 to an external HTTPS URL for mobile browser verification:

```bash
scripts\SHARE_TO_PHONE.bat
```

This uses an SSH reverse port forward through `localhost.run`.

## Android Application

When the Android SDK is configured locally:

```bash
.\gradlew.bat :keyfort-android:assembleDebug
```

The compiled APK will be located under `keyfort-android/build/outputs/apk/debug/` (or the configured Gradle output path).
