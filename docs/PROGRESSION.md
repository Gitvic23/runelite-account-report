# Version 1.1 progression observations

Version 1.1.0 adds verified unlock flags and local boss/encounter totals. Report format and cache schema are now version 2. Existing valid version-1 caches are read and migrated without discarding bank snapshots, timestamps or the random report filename ID.

## Unlocks

Log in, wait for stable observations and export. The plugin reads these supported named `gameval.VarbitID` constants on the client thread every 15 seconds, and on manual export:

| Report field | RuneLite constant | Interpretation verified from game scripts |
| --- | --- | --- |
| Knight Waves training | `KR_KNIGHTWAVES_STATE` | `prayer_overrides` uses `int_to_bool(state - 7)`, so state ≥8 is Completed and 0–7 is Incomplete. Negative/unavailable is Unknown. |
| Rigour unlock flag | `PRAYER_RIGOUR_UNLOCKED` | 0 Not unlocked, 1 Unlocked; other values Unknown. |
| Augury unlock flag | `PRAYER_AUGURY_UNLOCKED` | Same binary interpretation. |
| Preserve unlock flag | `PRAYER_PRESERVE_UNLOCKED` | Same binary interpretation. |
| Deadeye unlock flag | `PRAYER_DEADEYE_UNLOCKED` | Same binary interpretation. |
| Mystic Vigour unlock flag | `PRAYER_MYSTIC_VIGOUR_UNLOCKED` | Same binary interpretation. |

The plugin does **not** mistake `PRAYER_PIETY` / `PRAYER_CHIVALRY` activation varbits for training completion. It does not infer training from King's Ransom, a prayer being switched off, or an owned scroll. Completing Knight Waves and meeting Prayer/Defence levels are separate facts. The report does not claim a prayer is currently usable: membership, stat requirements and special-world overrides remain separate. Failed reads retain a previous observation/time, or Unknown if none exists.

## Boss/encounter counts

1. Open the game's **Combat Achievements** interface.
2. Choose **Bosses** and open a boss's detail page.
3. Wait a few seconds with the page open. The sidebar's observed-count tally should update.
4. Export. The report should contain that boss's displayed count, observation time and `personal Combat Achievements boss page` source.
5. Visit additional boss pages to cover more historical counts. A zero shown on a page is recorded as zero; bosses never observed stay Unknown.

The plugin reads `InterfaceID.CaBoss.BOSS_NAME` dynamic child 0 and the `Kill Count:` text child of `CA_BOSS_STATS`, as created by `ca_boss_set_name` and `ca_boss_init_stats`. It ignores personal-best time and combat-level rows. Missing, hidden, nonnumeric or ambiguous layouts yield no new observation and cannot clear a saved count. It does not read the POH Boss Kill Log, which could belong to someone else, nor another player's chat/profile.

Supported absolute personal system messages also refresh totals, for example:

```text
Your Zulrah kill count is: 42.
Your completed Theatre of Blood: Entry Mode count is: 9.
Your Gauntlet completion count is: 11.
Your completion count for TzHaar-Ket-Rak's First Challenge is: 3.
```

Only `GAMEMESSAGE` and `SPAM` event types are eligible, with verified anchored message forms. Public/private/friends/clan/trade chat is ignored. Color tags, thousands separators, an optional final period and the verified completion/subdued prefixes are handled. Malformed, negative or overflowing counts are ignored. The plugin stores only encounter name, count, source and timestamp, not chat transcripts. Unrecognized message forms may not update a count; the boss page is the manual refresh path where available.

Each parsed number is the game's **reported total**, not an increment. The plugin never adds 1 per kill, sums sources, merges raid modes, estimates totals from combat achievements/loot, or asks a remote service. Names/modes are preserved; only the verified `The Gauntlet`/`Gauntlet` and `The Corrupted Gauntlet`/`Corrupted Gauntlet` article differences are normalized. A newer authoritative lower count is shown as received rather than silently preserving an invented maximum. Older timestamps cannot replace newer evidence.

These are game-defined encounter counters: raids, Barrows and challenge/minigame-style bosses may count completions or chests rather than individual monsters killed. The report labels them as counts rather than claiming all counters mean the same thing. Unlisted encounters are Unknown, not zero, and this version does not fetch every historical boss count at login. It cannot see new kills made on mobile until an updated count is observed here.

## Storage, settings and accuracy checks

Unlock flags and boss counts inherit the existing stable character/world-profile identity and session guards. Logout, switching and loading cannot attach an old snapshot to a new account. Both lists are immutable between threads; versioned local cache writes keep the existing worker, batching and atomic replacement. New settings **Include unlock flags** and **Include boss counts** control their report sections; they do not activate any remote connection.

Before relying on these new fields, test locally:

- Compare Knight Waves status with your known completion; check that a completed training status does not claim Piety is usable below its requirements.
- Compare a known prayer-scroll unlock and a locked prayer. Export immediately after a real unlock or after a 15-second observation interval.
- Open one boss with a known nonzero count, then a known zero count. Compare names, totals and timestamps; unopened bosses must remain unknown.
- Open normal and harder/entry raid or Gauntlet modes and verify separate rows. Personal bests must not become counts.
- Perform one boss kill normally with the plugin enabled; compare the game's absolute system message with the exported total. Ordinary chat containing similar text must not update anything.
- Restart and export before opening the boss page: old totals/times must survive as cached. Switch characters: no unlock/count leakage. Confirm old bank data survived the version-1 migration.
- Disable each new report section and confirm the rows disappear. Toggle the plugin off/on and reopen a page to refresh it.

The new fields are compiled and unit-tested against supported RuneLite APIs. Their live Windows/game behavior still needs these user-run checks. The user validated the original exporter on Windows; that does not establish that this new page-layout parser has passed a live test.

## Authoritative sources checked

- [RuneLite API and client source](https://github.com/runelite/runelite/tree/e336ac270345c6c48e0fa69a6432d7fa03c5eb76): named varbits, `InterfaceID.CaBoss`, Widget APIs, ChatMessage types and the core Chat Commands plugin/tests' KC message forms.
- [RuneLite game-script source](https://github.com/runelite/cs2-scripts/tree/736e78d4c4260ba2a9d8ce3d2d665a98185de42e): `[proc,prayer_overrides]`, `[proc,int_to_bool]`, `[proc,prayer_isavailable_req]`, `[proc,script9352]`, `[clientscript,ca_boss_init]`, `[proc,ca_boss_set_name]`, `[proc,ca_boss_init_stats]` and `[proc,ca_specific_killcount]`.

Scripts were inspected as documentation only; the plugin neither executes arbitrary scripts nor adds raw numeric script IDs. UI reads use named public constants. No new runtime dependency or network capability was added.
