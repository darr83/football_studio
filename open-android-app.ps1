$projectPath = Join-Path $PSScriptRoot "android-app"

$studioCandidates = @(
    (Join-Path $env:ProgramFiles "Android\Android Studio\bin\studio64.exe"),
    (Join-Path ${env:ProgramFiles(x86)} "Android\Android Studio\bin\studio64.exe"),
    (Join-Path $env:LOCALAPPDATA "Programs\Android Studio\bin\studio64.exe"),
    (Join-Path $env:ProgramFiles "JetBrains\Android Studio\bin\studio64.exe"),
    (Join-Path ${env:ProgramFiles(x86)} "JetBrains\Android Studio\bin\studio64.exe")
)

$studioExe = $studioCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $studioExe) {
    Write-Error "Android Studio executable not found. Open '$projectPath' manually in Android Studio."
    exit 1
}

Start-Process -FilePath $studioExe -ArgumentList "`"$projectPath`""
Write-Output "Opening Android Studio project: $projectPath"
