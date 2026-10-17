# Testing this plugin with a Jagex account on Windows

Verified against RuneLite's current official instructions on **2026-10-17**:

- [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
- [RuneLite Launcher Configuration](https://github.com/runelite/runelite/wiki/RuneLite-Launcher-Configuration)

Running `.\gradlew.bat run` starts a separate development-client process. It does not inherit the Jagex Launcher's login just because your normal RuneLite client can log in. The official solution is to let normal RuneLite temporarily save a local `credentials.properties` file and let development RuneLite read it.

**That file allows login without entering your password. Keep it private and local. Do not upload it, paste its contents, copy it into the project, send it with logs, or commit it to Git.** This guide checks only whether it exists. Account Report Exporter does not read or manage authentication. The project's `.gitignore` also excludes this filename as a precaution.

Your normal RuneLite executable and installation stay in place. There is no executable replacement, installation-directory change, Jagex Launcher redirection, or JAR installation. The temporary launcher argument is removed below. Both clients use the usual `.runelite` data directory, so run them one at a time; the development client is not a separate sandbox for your RuneLite settings.

## Prepare the local login once

1. **Close both RuneLite client windows**, including the development client. You can leave the Jagex Launcher open. Keep the project in its ordinary folder outside `.runelite`; do not move project files into the RuneLite installation.

2. In the Windows Start menu, search for **RuneLite (configure)** and open it. The official guide requires **RuneLite launcher 2.6.3 or newer**; this refers to the RuneLite launcher, not the Jagex Launcher or in-game client version. If the configure shortcut is missing, consult the official launcher-configuration link above before continuing.

3. Privately note the existing contents of **Client arguments** so you can restore them. Add the following on its own new line, preserving any existing lines, then click **Save**:

   ```text
   --insecure-write-credentials
   ```

   Put this in **Client arguments**, not **JVM arguments**, PowerShell, `build.gradle`, or the Jagex Launcher. The configure window's saved client arguments also apply when RuneLite is started through the Jagex Launcher. Leave all other configuration controls as they were. If this argument was already present for another development workflow, remember that when restoring your settings.

4. In the **Jagex Launcher**, select **Old School RuneScape**, select the character you want to report on, choose **RuneLite** as the client, and click **Play**. Let your normal RuneLite client open. This is the step that supplies the launcher authentication and writes the temporary file. Launching the Gradle client with the same flag would not supply that login.

5. Close that normal RuneLite window. To check only whether the file exists, use this command in an ordinary PowerShell window:

   ```powershell
   Test-Path -LiteralPath (Join-Path $env:USERPROFILE '.runelite\credentials.properties')
   ```

   `True` means a file exists; it does not prove its login is still valid. `False` means it was not found in the default location. **Do not open the file or use `Get-Content` to troubleshoot.** Its default location is `%USERPROFILE%\.runelite\credentials.properties`, outside this project. If it existed before you began, it might contain an older character/session; step 4 refreshes it for your selected character.

6. **Restore the launcher argument now**, before ordinary play: reopen **RuneLite (configure)**, remove just the `--insecure-write-credentials` line you added, and click **Save**. Keep the original client arguments and other settings. This stops later normal launches from continually writing credentials. It does not remove the file already created, so you can still use it for testing. If the flag was already there for another workflow, decide whether that workflow still needs it rather than clearing all client arguments.

## Run and test Account Report Exporter

7. In File Explorer, open the project folder containing `gradlew.bat`. Click the address bar, type `powershell`, and press Enter. Use the **same Windows user account** that ran normal RuneLite. Use an ordinary, non-administrator PowerShell window and keep the default Java `user.home`; do not add a `-Duser.home=...` option. This is separate from `JAVA_HOME`, which still selects your JDK as explained in the README.

8. Run:

   ```powershell
   .\gradlew.bat run
   ```

   No extra authentication arguments or token environment variables are needed. RuneLite itself uses the local saved credentials. The `run` task already enables developer mode and registers Account Report Exporter through the official external-plugin harness. You do not need to rebuild just to follow these login steps.

9. The development client should offer the saved character's login. Log in normally, check the **character display name**, and enable **Account Report Exporter** if needed. Wait for its observations, open your bank, then click **Export TXT report**. Check the report's name and bank timestamp before sharing the TXT. The normal Jagex-launched RuneLite window does not load this local project; use the Gradle-launched window for plugin testing.

If the saved login is rejected or expired, close the development client and repeat steps 2–6 through the Jagex Launcher. To test another character, select that character in the Jagex Launcher and repeat the preparation, then verify the name in the development client before exporting. A file left from an earlier login is not proof that it matches your current selection.

## Finish and restore ordinary use

10. Close the development client. If you have not already done step 6, remove only the temporary client-argument line you added in **RuneLite (configure)** and click **Save**. Preserve the settings that were present before this procedure.

11. Press **Win+R**, enter `%USERPROFILE%\.runelite`, and press Enter. Delete **only `credentials.properties`** when you no longer need the saved development login. Do not open it, and do not delete the `.runelite` folder, settings, profiles, caches, session files, or your report folder. Empty that file from the Recycle Bin if you used normal Explorer deletion. If you already relied on this file for another development workflow, deleting it will also require that workflow to obtain a fresh development login later.

12. Launch RuneLite from the **Jagex Launcher** as you normally do. Your RuneLite installation is still in its original location, with the prior launcher configuration restored. You can leave the plugin source folder on disk; no uninstall or JAR removal from normal RuneLite is required. Another development test may need a new temporary credential file.

Deleting your local file does not revoke any copy that might have escaped your machine. If it may have been exposed, RuneLite's official guide directs you to **End sessions** in account settings on the official RuneScape website. That invalidates sessions and may require signing in again through the Jagex Launcher. Do not send credentials to anyone for diagnosis.

## If it still opens without a login

- Confirm the flag was in **Client arguments** and the file was created by **RuneLite launched from the Jagex Launcher**, with the intended character selected.
- Confirm both clients run under the same Windows user and default Java `user.home`. Custom Java home-directory settings can put their `.runelite` folders in different places. Do not solve a mismatch by pasting credentials into commands or moving credentials into the source folder.
- Check the file's presence with `Test-Path`, then refresh it via the preparation steps if needed. Presence alone does not mean it is fresh or usable.
- Keep the normal client closed during the Gradle-client test. If the normal Jagex-launched client itself asks for an old username/password unexpectedly, see RuneLite's [official FAQ](https://github.com/runelite/runelite/wiki/FAQ#wrong-username-and-password-after-starting-via-the-jagex-launcher-on-windows), including its administrator-mode guidance.
- For help, describe the step that failed and share only a carefully reviewed error excerpt. Do not share `credentials.properties`, token environment variables, a complete process environment, or `.runelite` configuration/session archives.

## Project verification

The project's development launcher matches the current official example-plugin structure: `ExternalPluginManager.loadBuiltin(AccountReportPlugin.class)` followed by `RuneLite.main(args)`, with `--developer-mode`, `--debug` and assertions in the Gradle `run` task. No custom login handling is necessary or added. The flag is deliberately not placed in the project's Gradle arguments.

Sources inspected: RuneLite wiki revision `04f77ccb1a7adb17484b502d9e6134e04a3f0c0d` (including the official credential-file workflow and cleanup); launcher revision `9be750d93cea0850e37d8401e37e57dc45f84b9f` (`ConfigurationFrame`: client arguments, one per line); client revision `e336ac270345c6c48e0fa69a6432d7fa03c5eb76` (`RuneLite`: supported development flag and default `user.home/.runelite` directory); official example-plugin revision `46e14f2c448fab6cbc599df1015b9631dcf1fc52` (launcher/run task).

The user has confirmed that the project builds and the plugin opens/enables on Windows. Jagex-account login and cleanup have not been executed by the cloud agent; they must be performed locally by the user.
