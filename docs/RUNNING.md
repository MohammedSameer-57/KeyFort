# Running KeyFort

## Quickstart (Three Commands)

1. `scripts\CHECK_SETUP.bat` - Verifies Java, Gradle, ports 8080/9090, Android SDK, and virtualization.
2. `scripts\RUN_DEMO.bat` - Starts server and demo site, waiting until ready, then opens http://localhost:8080/demo.
3. `scripts\STOP_DEMO.bat` - Stops all running demo processes and cleans up runtime state.

---

## Automated Test Execution

To execute the automated test suite across all active modules:

```cmd
scripts\RUN_TESTS.bat
```

Or via Gradle directly:

```cmd
.\gradlew.bat test
```

When no Android SDK is detected, `:keyfort-android` is safely skipped, and tests run for `:keyfort-core`, `:keyfort-demo-site`, and `:keyfort-server`.

---

## Emulator and Mobile Testing

### 1. Prerequisites and Setup
1. Install Android Studio with Android SDK Platform 34.
2. In Android Studio Device Manager, create a virtual device (Pixel 7 / API 34 image).
3. Ensure hardware virtualization (VT-x / AMD-V) is enabled in BIOS.
4. Run `scripts\CHECK_SETUP.bat` to confirm all green checks.

### 2. Running Demo with Android App
1. Start the backend services:
   ```cmd
   scripts\RUN_DEMO.bat
   ```
2. Start the Android emulator from Android Studio or via command line:
   ```cmd
   emulator -avd <Your_AVD_Name>
   ```
3. Build and install the Android app:
   ```cmd
   .\gradlew.bat :keyfort-android:installDebug
   ```
4. Five-step live demo workflow:
   - **Step 1 (Create Link)**: Open KeyFort on the device/emulator, tap "Create one-time link" for CinemaStream Family.
   - **Step 2 (Share Link)**: Tap "Share link" to open Android share sheet or copy the URL. Open the link in emulator Chrome (`http://10.0.2.2:8080/g/{link}`).
   - **Step 3 (Sign In as Friend)**: On the recipient page in Chrome, tap "Open CinemaStream Family". The friend is authenticated into a read-only guest session on `http://10.0.2.2:9090`.
   - **Step 4 (Copied Link Refused)**: Re-open or paste the same link into a second tab. The request is rejected: "Refused: this link was already used once."
   - **Step 5 (End Session & Key Renewal)**: Return to the KeyFort app, tap "End session". On next vault unlock, local key rotation executes, renewing the vault key.

### 3. Physical USB Device Variant (Optional)
To test with a physical Android device connected via USB:
1. Enable USB Debugging on the phone.
2. Reverse port forward both backend ports via adb:
   ```cmd
   adb reverse tcp:8080 tcp:8080
   adb reverse tcp:9090 tcp:9090
   ```
3. Optional: Mirror device screen to desktop using `scrcpy`:
   ```cmd
   scrcpy
   ```

---

## Port Conflicts & Troubleshooting

If port `8080` (KeyFort Server) or `9090` (Demo Site) is in use by another process:

### 1. Identify Conflicting Processes
```powershell
Get-NetTCPConnection -LocalPort 8080,9090 -State Listen -ErrorAction SilentlyContinue | Select-Object LocalPort, OwningProcess
```

### 2. Terminate Conflicting Processes (One-Liner)
```powershell
Get-NetTCPConnection -LocalPort 8080,9090 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

### 3. Clean Stopped Processes
If background Java processes linger after testing:
```powershell
Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -eq "" } | Stop-Process -Force
```

