@echo off
rem Starts the launcher from source -- the "demo" -- with the toolchain a terminal build uses:
rem the shared JDK 21 next to the project when there is one, and the warm Gradle cache.
setlocal
for %%I in ("%~dp0..") do set "PROJECT=%%~fI"
if exist "%PROJECT%\..\.tools\toolchain\jdk21\bin\java.exe" set "JAVA_HOME=%PROJECT%\..\.tools\toolchain\jdk21"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
cd /d "%PROJECT%"
if not exist ".local" mkdir ".local"
rem By full path: with NoDefaultCurrentDirectoryInExePath set, cmd does not look in the current folder.
call "%PROJECT%\gradlew.bat" --offline -q :app:run > ".local\demo.log" 2>&1
rem A dependency that is not in the cache yet: once more, online.
if errorlevel 1 findstr /c:"offline mode" ".local\demo.log" >nul && call "%PROJECT%\gradlew.bat" -q :app:run > ".local\demo.log" 2>&1
exit /b %ERRORLEVEL%
