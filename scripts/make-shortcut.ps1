# Puts a "Lumi Launcher (демо)" shortcut on the desktop that starts the launcher from source.
# Run it again after moving the project: the shortcut stores absolute paths.
$scripts = $PSScriptRoot
$project = Split-Path $scripts -Parent
$desktop = [Environment]::GetFolderPath('Desktop')

# The shortcut from before the launcher was renamed Lumi.
Remove-Item -LiteralPath (Join-Path $desktop 'Yumi Launcher (демо).lnk') -ErrorAction SilentlyContinue

$shell = New-Object -ComObject WScript.Shell
$link = $shell.CreateShortcut((Join-Path $desktop 'Lumi Launcher (демо).lnk'))
$link.TargetPath = Join-Path $env:WINDIR 'System32\wscript.exe'
$link.Arguments = '"' + (Join-Path $scripts 'run-demo.vbs') + '"'
$link.WorkingDirectory = $project
$link.IconLocation = (Join-Path $scripts 'lumi.ico') + ',0'
$link.Description = 'Lumi Launcher из исходников'
$link.Save()
Write-Output "Ярлык создан: $(Join-Path $desktop 'Lumi Launcher (демо).lnk')"
