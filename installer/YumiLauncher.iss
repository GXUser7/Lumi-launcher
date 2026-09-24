; Yumi Launcher's installer. Built by scripts/build-installer.ps1 from the application image that
; `:app:createDistributable` makes: the launcher with its own Java, nothing else to install first.

#define AppVersion "0.1.0"
#define AppImage "..\app\build\compose\binaries\main\app\YumiLauncher"
#define AppExe "YumiLauncher.exe"

[Setup]
AppId={{80EB9D2A-572B-40D7-A735-FB3483246B50}
AppName=Yumi Launcher
AppVersion={#AppVersion}
AppVerName=Yumi Launcher {#AppVersion}
AppPublisher=Yumi
AppPublisherURL=https://github.com/GXUser7/YumiLauncher
AppSupportURL=https://github.com/GXUser7/YumiLauncher/issues
; A game launcher needs no administrator: like Minecraft's own, it goes into the user's programs
; (%LOCALAPPDATA%\Programs) and asks no UAC question. /ALLUSERS on the command line still works.
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=commandline
DefaultDirName={autopf}\Yumi Launcher
DefaultGroupName=Yumi Launcher
DisableProgramGroupPage=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
OutputDir=..\dist
OutputBaseFilename=Yumi-Launcher-{#AppVersion}-windows-x64-setup
SetupIconFile=..\scripts\yumi.ico
WizardStyle=modern
WizardSmallImageFile=wizard-small-55.bmp,wizard-small-69.bmp,wizard-small-83.bmp,wizard-small-110.bmp,wizard-small-138.bmp
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName=Yumi Launcher
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

[Files]
Source: "{#AppImage}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Yumi Launcher"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\Yumi Launcher"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,Yumi Launcher}"; Flags: nowait postinstall skipifsilent

; Nothing of the player's is removed on uninstall: the settings, %APPDATA%\YumiLauncher and the
; game folder with its worlds stay, and a reinstall picks them up where they were.
