# KeyFort Environment & Dependency Verification Script
$root = Resolve-Path "$PSScriptRoot\.."
Set-Location $root

Write-Host "KeyFort System Setup Verification" -ForegroundColor Cyan
Write-Host "================================="

function Report-Check($label, $pass, $fixMsg) {
    if ($pass) {
        Write-Host " [OK]   $label" -ForegroundColor Green
    } else {
        Write-Host " [FAIL] $label" -ForegroundColor Red
        Write-Host "        Fix: $fixMsg" -ForegroundColor Yellow
    }
}

# 1. Java 17+
$javaPass = $false
try {
    $javaVer = java -version 2>&1 | Out-String
    if ($javaVer -match 'version "(\d+)') {
        $major = [int]$matches[1]
        if ($major -ge 17) { $javaPass = $true }
    }
} catch {}
Report-Check "Java 17+ runtime" $javaPass "Install JDK 17 or later and add it to PATH."

# 2. Gradle wrapper
$wrapperPass = Test-Path "$root\gradlew.bat"
Report-Check "Gradle wrapper present" $wrapperPass "Ensure gradlew.bat exists in repository root."

# 3. Port 8080 free
$port8080 = $true
$conn8080 = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
if ($conn8080) { $port8080 = $false }
Report-Check "Port 8080 free (keyfort-server)" $port8080 "Stop process using port 8080 or run scripts\STOP_DEMO.bat."

# 4. Port 9090 free
$port9090 = $true
$conn9090 = Get-NetTCPConnection -LocalPort 9090 -ErrorAction SilentlyContinue
if ($conn9090) { $port9090 = $false }
Report-Check "Port 9090 free (keyfort-demo-site)" $port9090 "Stop process using port 9090 or run scripts\STOP_DEMO.bat."

# 5. Android SDK detected
$sdkPass = $false
if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) {
    $sdkPass = $true
} elseif (Test-Path "$root\local.properties") {
    $prop = Get-Content "$root\local.properties" | Select-String "sdk.dir"
    if ($prop) { $sdkPass = $true }
}
Report-Check "Android SDK detected" $sdkPass "Set ANDROID_HOME or configure sdk.dir in local.properties."

# 6. adb available
$adbPass = $false
try {
    $null = Get-Command adb -ErrorAction Stop
    $adbPass = $true
} catch {}
Report-Check "Android Debug Bridge (adb)" $adbPass "Add %ANDROID_HOME%\platform-tools to your PATH."

# 7. Emulator image present
$avdPass = $false
try {
    $avdList = emulator -list-avds 2>&1
    if ($avdList -and $avdList.Length -gt 0) { $avdPass = $true }
} catch {}
Report-Check "Android emulator AVD" $avdPass "Create an AVD in Android Studio Device Manager."

# 8. Hardware virtualization enabled
$virtPass = $false
try {
    $cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
    if ($cpu.VirtualizationFirmwareEnabled) { $virtPass = $true }
} catch {
    $virtPass = $true # Fallback if WMI query restricted
}
Report-Check "Hardware virtualization enabled" $virtPass "Enable VT-x/AMD-V in your BIOS/UEFI settings."

Write-Host "================================="
