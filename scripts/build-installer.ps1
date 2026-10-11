# Builds Lumi Launcher's installer: the application image with its own Java (Compose's
# createDistributable), then Inno Setup around it. The result is dist\Lumi-Launcher-<version>-windows-x64-setup.exe.
#
#   ./scripts/build-installer.ps1              # uses JAVA_HOME, or the shared toolchain next to the project
#   ./scripts/build-installer.ps1 -Offline     # Gradle from its cache only
param(
    [string]$InnoCompiler = '',
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    if (-not $env:JAVA_HOME) {
        # The owner's machine keeps a portable JDK in E:\Projects\.tools; nothing is registered system-wide.
        $shared = Join-Path (Split-Path -Parent $root) '.tools\toolchain\jdk21'
        if (-not (Test-Path (Join-Path $shared 'bin\java.exe'))) { throw 'Set JAVA_HOME to a JDK 21.' }
        $env:JAVA_HOME = $shared
    }
    $gradleArgs = @(':app:createDistributable', '--console=plain')
    if ($Offline) { $gradleArgs += '--offline' }
    & ./gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Building the application image failed.' }

    if (-not $InnoCompiler) {
        $candidates = @(
            (Join-Path $root '.local\tools\inno\ISCC.exe'),
            (Join-Path ${env:ProgramFiles(x86)} 'Inno Setup 6\ISCC.exe'),
            (Join-Path $env:ProgramFiles 'Inno Setup 6\ISCC.exe'),
            (Join-Path $env:LOCALAPPDATA 'Programs\Inno Setup 6\ISCC.exe')
        )
        $InnoCompiler = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
        if (-not $InnoCompiler) { throw 'Inno Setup 6 not found: install it or pass -InnoCompiler <path to ISCC.exe>.' }
    }
    New-Item -ItemType Directory -Force -Path dist | Out-Null
    & $InnoCompiler /Q installer\LumiLauncher.iss
    if ($LASTEXITCODE -ne 0) { throw 'Compiling the installer failed.' }
    Get-ChildItem dist\*setup.exe | ForEach-Object {
        '{0}  {1:N1} MB' -f $_.Name, ($_.Length / 1MB)
        (Get-FileHash $_.FullName -Algorithm SHA256).Hash
    }
} finally { Pop-Location }
