package com.accountreport.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Verified, explicit observations only. No item/quest inference, remote lookups, or KC increments. */
public final class Progression
{
    public enum Unlock
    {
        KNIGHT_WAVES("Knight Waves training"),
        RIGOUR("Rigour unlock flag"),
        AUGURY("Augury unlock flag"),
        PRESERVE("Preserve unlock flag"),
        DEADEYE("Deadeye unlock flag"),
        MYSTIC_VIGOUR("Mystic Vigour unlock flag");

        public final String label;
        Unlock(String label) { this.label = label; }
    }

    public enum BossSource
    {
        GAME_MESSAGE("personal game message"),
        COMBAT_ACHIEVEMENTS("personal Combat Achievements boss page");
        public final String label;
        BossSource(String label) { this.label = label; }
    }

    private static final String NUMBER = "(0|[1-9][0-9]*|[1-9][0-9]{0,2}(?:,[0-9]{3})+)";
    private static final Pattern STANDARD = Pattern.compile(
        "^Your (?:(?:completed|subdued) )?(.+?) (?:kill |harvest |completion )?count is:\\s*" + NUMBER + "\\.?$");
    private static final Pattern COMPLETION = Pattern.compile(
        "^Your completion count for (.+?) is:\\s*" + NUMBER + "\\.?$");
    private static final Pattern PAGE_COUNT = Pattern.compile("^Kill Count:\\s*" + NUMBER + "$", Pattern.CASE_INSENSITIVE);

    private Progression() { }

    public static List<Snapshot.UnlockRow> unknownUnlocks()
    {
        List<Snapshot.UnlockRow> rows = new ArrayList<>();
        for (Unlock kind : Unlock.values()) { rows.add(new Snapshot.UnlockRow(kind, "Unknown", null)); }
        return rows;
    }

    public static String unlockValue(Unlock kind, int raw)
    {
        if (raw < 0) { return "Unknown"; }
        // prayer_overrides -> int_to_bool(kr_knightwaves_state - 7): true iff state >= 8.
        if (kind == Unlock.KNIGHT_WAVES) { return raw >= 8 ? "Completed" : "Incomplete"; }
        return raw == 1 ? "Unlocked" : raw == 0 ? "Not unlocked" : "Unknown";
    }

    public static String plain(String text)
    {
        return text == null ? "" : text.replaceAll("<[^>]*>", "").replaceAll("@[A-Za-z0-9_]+@", "").trim();
    }

    public static String bossName(String raw)
    {
        if (raw == null || raw.chars().anyMatch(Character::isISOControl) || raw.contains("<") || raw.contains(">"))
        { throw new IllegalArgumentException("Invalid encounter name"); }
        String name = raw.trim().replaceAll(" +", " ");
        if (name.isEmpty() || name.length() > 120) { throw new IllegalArgumentException("Invalid encounter name"); }
        // Verified names differ only by this article between the CA page and system messages.
        if (name.equalsIgnoreCase("The Gauntlet")) { return "Gauntlet"; }
        if (name.equalsIgnoreCase("The Corrupted Gauntlet")) { return "Corrupted Gauntlet"; }
        return name;
    }

    public static Snapshot.BossCount fromGameMessage(String message, Instant at)
    {
        String text = plain(message);
        for (Pattern pattern : List.of(COMPLETION, STANDARD))
        {
            Matcher m = pattern.matcher(text);
            if (m.matches()) { return count(m.group(1), m.group(2), at, BossSource.GAME_MESSAGE); }
        }
        return null;
    }

    public static Snapshot.BossCount fromBossPage(String name, String countText, Instant at)
    {
        Matcher m = PAGE_COUNT.matcher(plain(countText));
        return m.matches() ? count(plain(name), m.group(1), at, BossSource.COMBAT_ACHIEVEMENTS) : null;
    }

    private static Snapshot.BossCount count(String name, String value, Instant at, BossSource source)
    {
        try { return new Snapshot.BossCount(name, Integer.parseInt(value.replace(",", "")), at, source); }
        catch (IllegalArgumentException e) { return null; }
    }

    public static List<Snapshot.BossCount> observeBoss(List<Snapshot.BossCount> before, Snapshot.BossCount observed)
    {
        List<Snapshot.BossCount> result = new ArrayList<>(before);
        String key = observed.name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < result.size(); i++)
        {
            Snapshot.BossCount old = result.get(i);
            if (old.name.toLowerCase(Locale.ROOT).equals(key))
            {
                // An older callback must not replace newer evidence. A newer lower value stays explicit.
                if (!observed.observedAt.isBefore(old.observedAt)) { result.set(i, observed); }
                return result;
            }
        }
        result.add(observed);
        return result;
    }
}
