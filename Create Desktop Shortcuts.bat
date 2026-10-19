@echo off
setlocal
title Account Report Exporter - desktop setup
set "ACCOUNT_REPORT_PROJECT=%~dp0"
if not exist "%~dp0Start Account Report.bat" goto missing_project
if not exist "%~dp0Open Reports.bat" goto missing_project
rem The project path travels through an environment variable, not executable
rem PowerShell source. No execution-policy or launcher setting is changed.
powershell.exe -NoProfile -Command "$ErrorActionPreference = 'Stop'; try { $root = $env:ACCOUNT_REPORT_PROJECT; $desktop = [Environment]::GetFolderPath('Desktop'); if (-not $desktop) { throw 'Windows could not locate your Desktop.' }; $shell = New-Object -ComObject WScript.Shell; foreach ($entry in @(@('Account Report Exporter (test)', 'Start Account Report.bat'), @('Account Report - Exported reports', 'Open Reports.bat'))) { $file = Join-Path $desktop ($entry[0] + '.lnk'); if (Test-Path -LiteralPath $file) { Write-Host ('Already exists; left unchanged: ' + $file); continue }; $shortcut = $shell.CreateShortcut($file); $shortcut.TargetPath = Join-Path $root $entry[1]; $shortcut.WorkingDirectory = $root; $shortcut.Description = $entry[0]; $shortcut.Save(); Write-Host ('Created: ' + $file) } } catch { Write-Host ('Could not create shortcuts: ' + $_.Exception.Message); exit 1 }"
if errorlevel 1 goto shortcut_error
echo.
echo Desktop shortcuts are ready. Keep the extracted project folder in place.
echo Double-click Account Report Exporter (test) to start the test client.
pause
exit /b 0

:missing_project
echo Extract the complete project ZIP and run this file from that folder.
pause
exit /b 1

:shortcut_error
echo You can still double-click Start Account Report.bat in the project folder.
echo To make a shortcut manually, right-click that file, choose Show more
echo options if needed, then Send to, Desktop (create shortcut).
pause
exit /b 1
