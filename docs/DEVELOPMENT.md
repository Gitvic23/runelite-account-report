# Verified sources and design

Official sources and their relevant API Javadoc were inspected before implementation on 2026-10-17. Production and development-launcher compilation then succeeded against real released **RuneLite 1.13.2** artifacts. Source HEAD was the next development snapshot; the actual build validates release compatibility.

| Official reference | Evidence |
| --- | --- |
| [example-plugin](https://github.com/runelite/example-plugin/tree/46e14f2c448fab6cbc599df1015b9631dcf1fc52) | External-plugin layout, Gradle 8.10 wrapper/checksum, Java release 11, launcher/run task. |
| [Plugin Hub](https://github.com/runelite/plugin-hub/tree/092f55279b06d8a97255edec38c4fa248d973772) | JDK 11, standard metadata, licensing, review and dependency requirements. No submission performed. |
| [RuneLite source](https://github.com/runelite/runelite/tree/e336ac2) | Current API/client implementations and Javadoc. Full revision is recorded in the useful-items table. |
| [Developer Guide](https://github.com/runelite/runelite/wiki/Developer-Guide) | Source/API guidance; wiki inspected at `04f77ccb1a7adb17484b502d9e6134e04a3f0c0d`. |
| [Plugin Hub Review](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review) | Security/game-rule review scope and functional-testing limitations. |
| [Rejected features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features) | Restrictions: no automated inputs, actions or combat assistance. |
| [Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts) | Local development-login instructions; credentials stay outside this plugin. |
| [API Javadoc](https://static.runelite.net/runelite-api/apidocs/) / [client Javadoc](https://static.runelite.net/runelite-client/apidocs/) | Documentation entry points; relevant Javadoc inspected in authoritative source. |

Jagex-account development setup was rechecked against the current official wiki, launcher and example-plugin sources on 2026-10-17. The existing development harness already supports RuneLite's saved-credential workflow; it needs no authentication-code changes. See [Windows preparation and full cleanup](JAGEX-ACCOUNT-WINDOWS.md) for the temporary normal-launcher argument, file-presence-only check, character selection and restoration steps. The exporter never reads authentication files, and the project does not enable credential writing itself.

## Collection contracts

- `Client` inherits `OAuthApi.getAccountHash()`, documented per RuneScape account, `-1` before login. RuneLite's `ConfigManager` associates it with `RuneScapeProfileType.getCurrent(client)`. A digest of that pair keys caches. Display names, login emails and credentials are never identity keys. Jagex-login characters remain separate.
- `Varbits.ACCOUNT_TYPE` documents values 0–6: normal, Ironman, Ultimate, Hardcore, Group, Hardcore Group, unranked Group. Future values are Unknown. This avoids the older account-type enum omitting unranked Group. `VarPlayer.QUEST_POINTS` is the named quest-point constant. These supported legacy constants compile with a deprecation notice; gameval IDs are used for containers/interfaces/fillers.
- `Skill.values()`, `getRealSkillLevel`, `getSkillExperience`, `Experience.getXpForLevel` and `MAX_REAL_LEVEL` give dynamic roster and actual XP/level caps. No boosted or virtual levels. Tested release includes Sailing.
- `Quest.values()` includes miniquests/subquests. `Quest.getState(client)` executes `QUEST_STATUS_GET` and reads its script stack. Calls run on the client thread while `LOGGED_IN`, with current identity/player and at least two logged-in ticks. Polling is once per minute plus manual export. An individual API failure becomes Unknown. The enum exposes no reliable miniquest type: counts are explicitly API-entry counts.
- `getItemContainer(gameval.InventoryID.BANK)` gives the full underlying container. `InterfaceID.Bankmain.ITEMS` only gates bank readiness; widget items are never enumerated. Bank capture additionally requires `ItemContainerChanged` in this login/session. State/identity changes clear pending flags. Missing containers or definitions retain old data; a valid received empty container is empty. Unconfirmed old bank containers are never bootstrapped on login.
- `getItemDefinition`, `getPlaceholderTemplateId` (`-1` for normal), and `gameval.ItemID.BANK_FILLER` filter non-owned entries. `getNote`/`getLinkedNoteId` explicitly label notes. No canonicalization or variant merging occurs. Equipment uses `EquipmentInventorySlot` indices and lists occupied slots.
- `Plugin.getPluginDirectory()`, descriptor `internalName`, and `Filepath` confine production I/O. UTF-8 temporary files are moved atomically where supported. `Filepath.Unchecked` appears **only in filesystem tests** to create temporary roots. No arbitrary custom export path is accepted.
- `ExternalPluginManager.loadBuiltin` plus `RuneLite.main` is the official development harness. Sidebar uses `ClientToolbar`/`NavigationButton`/`PluginPanel`. `LinkBrowser.open` is RuneLite's local-folder helper; it sends no account data.

## Threads, persistence and freshness

Client state belongs to the client thread; Swing components/clipboard belong to the EDT. A dedicated worker loads, formats and writes local data. Immutable snapshots copy all lists and contain no client objects. Session-generation guards reject late cache callbacks after hopping, logout or switching characters. Fresh identity updates a renamed display name. No character cache is selected while logged out.

Inventory/equipment changes capture on the next stable tick. Skills poll every 15 seconds; quests every 60 seconds. Manual exports refresh available data. Cache writes coalesce to once per five seconds and flush on logout/plugin shutdown. Automatic reports start after a minute and attempt at most once per minute. A busy guard rejects overlapping export/copy requests. Shutdown queues the last published immutable snapshot and drains writes without blocking the game/UI thread; abrupt termination cannot guarantee completion.

Cache schema 2 uses explicit Java Properties fields and UTF-8 readers/writers, **not Java object serialization**. Valid schema-1 caches migrate with core observations/timestamps and report identity preserved, and with the new flags/counters unknown until observed. The tests include a fixture generated by the genuine 1.0 codec. The new schema validates unlock kinds/statuses, count sources, nonnegative counts, required timestamps and duplicate names, in addition to existing field/count/key/UUID checks. Read/parse/version failures warn and return no cached snapshot. Report and cache format versions are independent. Future incompatible changes need schema migration/versioning. Keep plugin versions in Gradle, metadata and `Snapshot.PLUGIN_VERSION` consistent.

Latest filenames use a persisted random UUID, so renames retain one latest file. History names sanitize the display name and avoid Windows device paths. Internal hashes are absent from report text/filenames. Files are plain local text, not encryption. Location quantities are never summed across snapshots, even when their timestamps look close.

## Catalog maintenance

`src/main/resources/useful-items.tsv` holds category, ID and official `gameval.ItemID` constant. Every ID was verified at the revision in its header. Runtime names come from item definitions; no name guessing occurs. New/unlisted items remain in complete container sections.

Excluded lookalikes: quest gem bag 766, Devious Minds pouches 6819/26906, dummy gem bag 25628, rune-pouch voucher 24587 and defender ornament kit 20143. Degraded/charged/imbued/cosmetic variants retain raw IDs. A note can match the catalog through its explicit unnoted reference, but its original ID stays in the report. Broken/special-mode variants are named explicitly, without a usability/charge/unlock inference. Review item meaning and official constants before extending the table.

## Additional progression assessment

Version 1.1 implements Knight Waves and five direct prayer unlock flags, plus local boss/encounter counts from personal Combat Achievements pages and verified system-message patterns. [Progression API evidence and testing](PROGRESSION.md) records exact current source revisions and interpretation. It avoids activation-bit/unlock confusion, other players' POH logs, estimated/default-zero KC and remote hiscores. New observations use the existing per-character session and worker-I/O flow. Boss-page reads occur on stable game ticks; a changed page/count or the existing 15-second/manual refresh updates its row. Ordinary unchanged ticks do not write new snapshots solely for the boss page.

RuneLite's core Slayer plugin reads named `VarPlayerID.SLAYER_COUNT/SLAYER_TARGET` and `VarbitID.SLAYER_POINTS`, plus DB tables and special task/partner/modifier handling. A bare numeric varp is not a reliable task name. Diary tiers and reward/minigame unlocks also need field-specific meaning and freshness checks, sometimes widget-driven observations. These fields are explicitly deferred in the report instead of guessing zeros, inferring unlocks from items, or delaying TXT delivery.

## Validation

`./gradlew clean build coreTest exampleReport` compiles production and launcher code, runs all tests, separately runs the core suite and regenerates the fictional example with the real formatter. `./gradlew run` needs a desktop and user-managed login; this headless cloud environment cannot establish in-game behavior. Test-only Java reflection creates interface fakes; production uses none.

The official wrapper preserves Gradle distribution checksum verification. TLS/signature/checksum verification was never disabled. Cloud setup added a checksum-verified full JDK because the base image had only a JRE, and used the system's existing trusted Java CA store for the platform proxy. Cloud proxy settings are local environment configuration, not part of the Windows project.
