# Account Report Exporter

A local RuneLite plugin that exports an Old School RuneScape character to a readable UTF-8 TXT report for manually sharing with ChatGPT. **No AI connection, API key, external account, server or account-data network requests.** You decide whether to share the file.

This is a **development plugin**, not a reviewed or published Plugin Hub installation. Nothing has been submitted or published. Dropping its JAR into normal RuneLite does not install it.

## Implemented

- Display name, account type including unranked Group Ironman, combat/total levels, timezone, plugin/report versions and independent observation times.
- Every skill in the current client enum, actual level, XP and XP to the next actual level. Maximum actual level shows N/A. Includes Sailing on the tested release; no fixed skill count.
- Every quest API entry grouped by Completed / In progress / Not started / Unknown, with counts and quest points. Miniquests/subquests are included but not separately classified: the API exposes no reliable type flag.
- Full received bank container, independent of searches, tabs and filters, with alphabetical item names, original IDs and quantities. Placeholders and bank fillers are excluded.
- Inventory and equipment, with independent timestamps and occupied equipment slots. Valid empty containers differ from unavailable containers.
- Useful progression items: rune pouches, herb sack, gem bag, seed box, fishing barrels, forestry kits/baskets, essence pouches, graceful, Slayer helmets, rune crossbow, dragon defender, fire cape, Barrows gloves and common teleport jewelry. The [maintainable catalog](src/main/resources/useful-items.tsv) contains verified IDs and named official constants.
- Sidebar buttons for TXT export, clipboard copy and opening the report folder, with observation times and success/error feedback.
- Section toggles, automatic local exports (off by default, at most one attempt per minute), and timestamped history (off by default). Manual exports refresh skills/quests and available inventory/equipment first.

See the [synthetic example report](docs/example-report.txt). Its fictional skill/quest dataset is deliberately abbreviated; real reports enumerate every client API entry.

## Accuracy and storage

Versioned local caches preserve observations across restarts. **Bank not yet observed** is different from an empty bank. Missing containers, login/logout and hopping retain the last valid snapshot. Log in and open your bank to refresh it. If you enable the plugin with the bank already open, close/reopen the bank to receive a fresh update.

"Observed this session" means observed at the stated time, not guaranteed current ownership. Snapshots from different times can disagree. Quantities are **never added across locations**. Withdrawing a pouch after an old bank snapshot can show it in both locations, with separate times and no combined total. The report cannot observe changes on mobile or another client.

Caches use RuneLite's stable per-character account hash plus world-profile type internally, never display names or login credentials. Characters and normal/league/Deadman profiles are isolated. The hash is digested for cache selection and never exported. A rename updates the displayed name but keeps the same latest-report file, whose random local ID is unrelated to game identity. Reports are disabled while logged out. Corrupt or unsupported-version caches are ignored with feedback; re-observe containers. A corrupt cache can create a new random report filename; old TXT files are not automatically deleted.

All production file I/O uses RuneLite's current `Filepath` API in its plugin-owned directory. Arbitrary custom folders are not accepted. UTF-8 temp-file writes use atomic replacement when supported, with ordinary replacement as the fallback. Pending writes drain on plugin shutdown without blocking the game/UI threads; a forced process kill can still lose recent observations. Files contain plain local account data, not encrypted storage.

**Outside coverage:** POH, STASH, seed vault, death/group storage, potion storage and contents of pouches/sacks/bags/barrels. Item presence does not prove stored supplies, charges, usability or account unlocks. Unrecognized items remain in the full lists but may not match the summary catalog.

**Deferred:** achievement diaries, current Slayer task, Slayer points/reward unlocks, minigame currencies/rewards, other account flags and Word output. These require further state-specific verification and are never filled with guessed zeros. See [API evidence and decisions](docs/DEVELOPMENT.md).

## Windows: first-time setup

1. Download the complete project ZIP and choose **Extract All**, for example to `C:\Users\YourName\Documents\account-report-exporter`. Do not run from inside the ZIP. The project folder contains `build.gradle` and `gradlew.bat`. Alternatively clone this repository once these changes are present in your copy.
2. Install a **64-bit Eclipse Temurin JDK 11** from [Adoptium](https://adoptium.net/temurin/releases/?version=11). Choose JDK, not JRE. Let the installer set `JAVA_HOME` and add Java to PATH. The official RuneLite plugin instructions recommend Java 11. This project uses the official Gradle 8.10 wrapper and targets Java 11 bytecode. It was compiled/tested here with **Temurin 11.0.28+6** and **RuneLite 1.13.2**; install a current supported JDK 11 security update on your PC.
3. Install [IntelliJ IDEA](https://www.jetbrains.com/idea/download/) with its free Java development features. Git for Windows is optional unless you want to clone the repository.
4. Open the extracted folder in File Explorer. Click its address bar, type `powershell`, and press Enter. Check:

   ```powershell
   java -version
   javac -version
   ```

   Both should report 11. If `javac` is missing, install a JDK and reopen PowerShell. To select one just for this window, replace this example with your actual installed directory:

   ```powershell
   $env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-11.YOUR-VERSION'
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   ```

5. Build and test:

   ```powershell
   .\gradlew.bat clean build
   ```

   The first run downloads Gradle and RuneLite dependencies. No separate Gradle installation is needed. Look for `BUILD SUCCESSFUL`. Test results are in `build\reports\tests\test\index.html`. The JAR in `build\libs` is not an installer.
6. If you use a **Jagex Account**, first complete the [Windows development-login steps](docs/JAGEX-ACCOUNT-WINDOWS.md). Running Gradle directly does not inherit a login from the Jagex Launcher. RuneLite's official procedure temporarily saves a local authentication file through your normal RuneLite client; the development client then reads it. No plugin login-code change is needed.

   Launch the development client from the project folder:

   ```powershell
   .\gradlew.bat run
   ```

7. Keep PowerShell open. This uses the official external-plugin launcher with developer mode and debug logs. Close your normal RuneLite client before running the development client. The normal client opened from the Jagex Launcher will not contain this local development plugin. After testing, follow the guide's cleanup steps to remove the temporary login file and restore any client argument you added. **Never upload `credentials.properties`, credentials, session files or login tokens.** RuneLite itself handles login; this plugin does not read them.
8. In the development client's wrench/configuration panel, search for **Account Report Exporter** and enable it. Open the paper/report sidebar icon. Log in, wait a few seconds for observations, open your bank, and click **Export TXT report**.

### IntelliJ alternative

Choose **Open**, select the project folder and import it as Gradle. Set **Project SDK** to JDK 11 and **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM** to that JDK. Run the `build` task in the Gradle tool window, then `run`. Alternatively run the `main` method in `src/test/java/com/accountreport/AccountReportLauncher.java` with `--developer-mode --debug` as program arguments.

The same [Jagex-account preparation and cleanup](docs/JAGEX-ACCOUNT-WINDOWS.md) applies to IntelliJ. Keep the usual Windows user/home directory so RuneLite can find its local login file.

### Find and upload the report

Click **Open report folder**. By default on Windows it is:

```text
%USERPROFILE%\.runelite\plugin-data\account-report-exporter\reports
```

You can paste that path into File Explorer. A customized RuneLite base directory can change the location; the folder button uses the actual path. The latest file is `account-report-<random-local-id>.txt`, one per character/world-profile. History files start with `history-` when enabled. The adjacent `cache` folder is internal storage; upload the TXT, not the cache.

Open the TXT in Notepad and check its character name, bank time and coverage. In ChatGPT, click **Attach files / plus**, select the TXT, and ask for progression advice. For example: "Suggest Ironman progression priorities from this report; respect snapshot dates and ask about unknown storage or unlocks." Uploading is your deliberate action, separate from the plugin. Alternatively copy the report using the sidebar and paste it yourself.

## Troubleshooting

- Share the exact command, relevant PowerShell error and output of `java -version` / `javac -version`. Test failures have details in `build\reports\tests\test\index.html`.
- RuneLite logs are under `%USERPROFILE%\.runelite\logs`, unless customized. Look for `AccountReportPlugin` in the latest `client.log` or the `run` console. Review/redact logs before sharing them; do not share credential, session, profile or settings files.
- A `JAVA_COMPILER` or missing `javac` error means a JRE or wrong Gradle JVM: select a full JDK 11.
- A development client without your Jagex login needs the [official temporary credential-file workflow](docs/JAGEX-ACCOUNT-WINDOWS.md). Putting the export flag in Gradle or launching the normal client alone does not load this plugin.
- Dependency downloads need `services.gradle.org`, its official download redirects, `repo.runelite.net` and Maven Central (`repo.maven.apache.org`). Check your proxy/firewall; do not disable TLS/checksum verification.
- For an outdated RuneLite client, run `.\gradlew.bat --refresh-dependencies build`, then launch again. The dependency follows the official template's `latest.release`; future API changes can require maintenance.
- Old/unknown bank data: log into the correct character and close/reopen the bank. A deposit box is not the bank-observation workflow.
- Export errors: check disk space and directory permissions. If clipboard access fails, export TXT. The RuneLite folder helper offers to copy the path if opening Explorer fails.

## Validation and developer notes

The plugin and development launcher compile against real RuneLite artifacts. Tests cover deterministic formatting, unknown/empty containers, placeholders/fillers, full-container API reads, dynamic skill enumeration/max levels, variants/notes, stale quantities, account/session isolation, missing/corrupt/versioned caches, actual disk persistence, filename safety and UTF-8 replacement.

**In-game behavior, login, and Windows desktop/clipboard integration have not been verified in this headless cloud machine.** Complete the [manual checklist](docs/MANUAL-TESTING.md) yourself. A passing build or started JVM does not establish in-game correctness.

On Linux/macOS use `./gradlew clean build` and `./gradlew run`. `./gradlew coreTest` exercises the core independently of RuneLite dependency access. `./gradlew exampleReport` regenerates the sample through the real formatter.

Responsibilities are separated: `RuneLiteCollector` reads client APIs; `core/` owns immutable snapshots, isolation, storage schema, formatting and reports; `RuneLiteFiles` confines disk writes; `AccountReportPanel`/`AccountReportConfig` provide UI/settings; `AccountReportPlugin` coordinates client-thread collection, worker I/O and EDT updates. No gameplay automation, input generation or game-action modification is implemented.

Licensed under [BSD-2-Clause](LICENSE). See [third-party notices](THIRD-PARTY-NOTICES.md) and [verified API sources](docs/DEVELOPMENT.md).
