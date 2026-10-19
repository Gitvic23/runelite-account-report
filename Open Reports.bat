@echo off
setlocal
title Account Report Exporter - exported reports
set "ACCOUNT_REPORT_REPORTS=%USERPROFILE%\.runelite\plugin-data\account-report-exporter\reports"
if not exist "%ACCOUNT_REPORT_REPORTS%\." goto not_found
start "" explorer.exe "%ACCOUNT_REPORT_REPORTS%"
exit /b 0

:not_found
echo No report folder was found in the default RuneLite location.
echo Start the test client and click Export TXT report at least once.
echo If you use a custom RuneLite location, click Copy report folder path
echo in the plugin and paste it into File Explorer's address bar instead.
pause
exit /b 1
