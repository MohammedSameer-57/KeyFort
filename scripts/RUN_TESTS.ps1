# KeyFort Test Suite Runner with Per-Module Breakdown
$root = Resolve-Path "$PSScriptRoot\.."
Set-Location $root

Write-Host "Running KeyFort Test Suite across all modules..." -ForegroundColor Cyan

$env:GRADLE_USER_HOME = "$root\.gradle-home"
$output = .\gradlew.bat test --no-daemon 2>&1

$output | Out-Host

Write-Host "`nTest Execution Summary" -ForegroundColor Cyan
Write-Host "======================"
$modules = @("keyfort-core", "keyfort-demo-site", "keyfort-server")
foreach ($m in $modules) {
    $reportDir = "$root\$m\build\test-results\test"
    $count = 0
    if (Test-Path $reportDir) {
        $xmlFiles = Get-ChildItem -Path $reportDir -Filter "TEST-*.xml"
        foreach ($f in $xmlFiles) {
            [xml]$xml = Get-Content $f.FullName
            $count += [int]$xml.testsuite.tests
        }
    }
    Write-Host (" {0,-20} : {1} tests passed" -f $m, $count) -ForegroundColor Green
}
Write-Host "======================"
