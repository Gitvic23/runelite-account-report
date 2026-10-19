@echo off
setlocal
title Account Report Exporter - test client
pushd "%~dp0"
if errorlevel 1 goto folder_error

echo Account Report Exporter - test client
echo Close your normal RuneLite window before using this client.
echo Keep this window open while playing; you can minimize it.
echo The first start may download dependencies and take a few minutes.
echo.

if not exist "gradlew.bat" goto missing_project
if not exist "build.gradle" goto missing_project

rem Reuse an existing full JDK, or find the Temurin 11 installation
rem recommended in the Windows guide. Changes apply only to this window.
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" if exist "%JAVA_HOME%\bin\javac.exe" goto launch
set "JAVA_HOME="
for /d %%J in ("%ProgramFiles%\Eclipse Adoptium\jdk-11*" "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-11*") do if exist "%%~fJ\bin\java.exe" if exist "%%~fJ\bin\javac.exe" set "JAVA_HOME=%%~fJ"
if defined JAVA_HOME goto launch
where java.exe >nul 2>&1
if errorlevel 1 goto missing_java
where javac.exe >nul 2>&1
if errorlevel 1 goto missing_java

:launch
rem Gradle compiles only what changed. No clean build or test run is needed
rem for daily use. RuneLite handles authentication itself.
call gradlew.bat --console=plain run
set "ACCOUNT_REPORT_EXIT=%ERRORLEVEL%"
if not "%ACCOUNT_REPORT_EXIT%"=="0" goto launch_error
popd
exit /b 0

:missing_project
echo Extract the entire project ZIP first, then run this file from that folder.
echo Do not move this BAT file out of the project. Use a desktop shortcut instead.
goto failed

:missing_java
echo A full Java JDK was not found. Install 64-bit Temurin JDK 11,
echo or follow the Java setup steps in README.md, then try again.
goto failed

:launch_error
echo.
echo The test client stopped with an error. Read the message above.
echo Setup help: README.md
echo Jagex login help: docs\JAGEX-ACCOUNT-WINDOWS.md
echo Never share credentials.properties or a full RuneLite settings folder.
popd
pause
exit /b %ACCOUNT_REPORT_EXIT%

:failed
popd
pause
exit /b 1

:folder_error
echo Could not open the project folder. Extract it to a local Documents folder.
pause
exit /b 1
