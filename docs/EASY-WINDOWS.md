# Use the test plugin while Plugin Hub review is pending

## Set up once

1. Close the development client and normal RuneLite. [Download this branch](https://github.com/Gitvic23/runelite-account-report/tree/account-report-exporter) using **Code > Download ZIP**.
2. Choose **Extract All** and keep the extracted project in a permanent folder, such as Documents. Open the folder containing `build.gradle` and `Start Account Report.bat`. Do not run inside the ZIP or move individual BAT files out of the folder.
3. Double-click **Create Desktop Shortcuts.bat**. It adds **Account Report Exporter (test)** and **Account Report - Exported reports** to your Windows Desktop, including a Desktop redirected to OneDrive. It leaves existing shortcuts with those names unchanged. No administrator privileges are needed.
4. Double-click **Account Report Exporter (test)**. The first launch may download dependencies. It uses your existing full JDK, looks for the recommended Temurin 11 installation if needed, and otherwise uses Java from PATH. If Java is missing, follow the JDK 11 steps in the README.
5. If your Jagex development login still works, use it. If the login is missing, rejected, expired, or for the wrong character, refresh it using [the official Jagex-account steps](JAGEX-ACCOUNT-WINDOWS.md#prepare-the-local-login-once). Remove the temporary launcher argument after refreshing it. The helpers do not read, copy, modify or upload the saved credentials.

## Each time you want account advice

1. Close normal RuneLite, then double-click **Account Report Exporter (test)**. Keep its command window open; minimizing it is fine. The test client shares your normal RuneLite data/settings directory, so use one client at a time.
2. Log in and enable **Account Report Exporter** if necessary. Open its sidebar and your bank. For boss totals you want refreshed, open your own Combat Achievements boss-detail pages.
3. Click **Copy report to clipboard**, switch to ChatGPT, paste, and add your question. The copy button refreshes available observations and uses the same report format as TXT export. It does not create a TXT file.
4. If you prefer an attachment, click **Export TXT report**, then use the **Account Report - Exported reports** shortcut. Select the updated `account-report-...txt` file. History is off by default, so the same character's latest file updates instead of creating a new file each time.

You do not need to open PowerShell, type Gradle commands, or run a clean build each time. Existing observations and plugin settings remain in your usual RuneLite data folder. Automatic TXT export is optional in plugin settings; it is off by default and runs at most once per minute while valid observations are available.

## Updates and cleanup

- The shortcuts point to this extracted project. To try a later version, extract its full ZIP into a new permanent folder. Delete the two old desktop shortcuts and run the new folder's **Create Desktop Shortcuts.bat**. Your plugin caches/reports stay in the RuneLite data folder. Downloading a new ZIP does not update old shortcut targets automatically.
- The reports shortcut uses `%USERPROFILE%\.runelite\plugin-data\account-report-exporter\reports`. If you customized RuneLite's data location, use **Copy report folder path** in the plugin and paste the actual path into File Explorer instead.
- When the plugin is available through Plugin Hub, close the test client and install it from your normal Jagex-launched RuneLite. Delete the two desktop shortcuts when you no longer need them. Follow the [Jagex cleanup steps](JAGEX-ACCOUNT-WINDOWS.md#finish-and-restore-ordinary-use) to remove the temporary development-login file. Leave your report/cache folder in place.
- These BAT files are standalone Windows development helpers. They launch Gradle or File Explorer outside the plugin; they are not bundled in the plugin JAR and do not reintroduce process execution into plugin code. They do not alter the installed RuneLite executable, Jagex Launcher configuration, PowerShell execution policy or persistent Java settings.

The helpers were reviewed in the Linux cloud environment; actual Windows shortcut creation, Explorer and Jagex login must be verified on your PC.
