; Lumi Launcher's installer. Built by scripts/build-installer.ps1 from the application image that
; `:app:createDistributable` makes: the launcher with its own Java, nothing else to install first.

#define AppVersion "1.1"
#define AppImage "..\app\build\compose\binaries\main\app\LumiLauncher"
#define AppExe "LumiLauncher.exe"

[Setup]
AppId={{80EB9D2A-572B-40D7-A735-FB3483246B50}
AppName=Lumi Launcher
AppVersion={#AppVersion}
AppVerName=Lumi Launcher {#AppVersion}
AppPublisher=Lumi
AppPublisherURL=https://github.com/GXUser7/yumi-launcher
AppSupportURL=https://github.com/GXUser7/yumi-launcher/issues
; A game launcher needs no administrator: like Minecraft's own, it goes into the user's programs
; (%LOCALAPPDATA%\Programs) and asks no UAC question. /ALLUSERS on the command line still works.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=commandline
; An update over Yumi Launcher (before 1.1) stays in the folder it was installed to: UsePreviousAppDir.
DefaultDirName={autopf}\Lumi Launcher
DefaultGroupName=Lumi Launcher
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir=..\dist
OutputBaseFilename=Lumi-Launcher-{#AppVersion}-windows-x64-setup
SetupIconFile=..\scripts\lumi.ico
WizardStyle=modern
WizardSmallImageFile=wizard-small-55.bmp,wizard-small-69.bmp,wizard-small-83.bmp,wizard-small-110.bmp,wizard-small-138.bmp
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName=Lumi Launcher
Compression=lzma2/ultra64
SolidCompression=yes
; An open launcher is closed before its files are replaced; it stops the servers it runs on the way out.
CloseApplications=yes
RestartApplications=no
SetupLogging=yes

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[InstallDelete]
; An update brings a whole new image; libraries of the old one must not linger next to it.
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"
; Before 1.1 the launcher was Yumi Launcher: its .exe and shortcuts would be left behind.
Type: files; Name: "{app}\YumiLauncher.exe"
Type: files; Name: "{autoprograms}\Yumi Launcher.lnk"
Type: files; Name: "{autodesktop}\Yumi Launcher.lnk"

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Lumi Launcher"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\Lumi Launcher"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,Lumi Launcher}"; Flags: nowait postinstall skipifsilent

; Nothing of the player's is removed on uninstall: the settings, %APPDATA%\LumiLauncher (YumiLauncher
; for players from before 1.1) and the game folder with its worlds stay, and a reinstall picks them
; up where they were.
