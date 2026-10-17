package com.accountreport.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class ReportFormatter
{
    private final UsefulCatalog catalog;
    private final DateTimeFormatter date;
    public ReportFormatter(UsefulCatalog catalog, ZoneId zone)
    {
        this.catalog = catalog;
        date = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss XXX '['VV']'", java.util.Locale.ROOT).withZone(zone);
    }

    public String format(Snapshot s, ReportOptions options, Instant generated, Instant sessionStart)
    {
        StringBuilder out = new StringBuilder("ACCOUNT REPORT EXPORTER\n");
        line(out, "Character: " + clean(s.name));
        line(out, "Account type: " + clean(s.accountType) + " | World profile: " + clean(s.profile));
        line(out, "Combat level: " + (s.combatLevel > 0 ? s.combatLevel : "Unknown")
            + " | Total level: " + (s.totalLevel > 0 ? s.totalLevel : "Unknown"));
        line(out, "Generated: " + date.format(generated));
        line(out, "Plugin version: " + Snapshot.PLUGIN_VERSION + " | Report format: " + Snapshot.FORMAT_VERSION);
        line(out, "Identity observed: " + freshness(s.identityAt, sessionStart));
        line(out, "Skills observed: " + freshness(s.skillsAt, sessionStart));
        line(out, "Quests observed: " + freshness(s.questsAt, sessionStart));
        line(out, "Bank observed: " + freshness(s.bank.observedAt, sessionStart));
        line(out, "Inventory observed: " + freshness(s.inventory.observedAt, sessionStart));
        line(out, "Equipment observed: " + freshness(s.equipment.observedAt, sessionStart));

        section(out, "DATA COVERAGE AND LIMITATIONS");
        line(out, "All values are last observed, not live knowledge. Cached observations may be stale.");
        line(out, "Snapshots from different times can disagree; location quantities are NEVER added together.");
        line(out, "This plugin cannot observe changes on mobile or another client. Reopen RuneLite, log in,");
        line(out, "and open your bank to refresh it. Login alone does not refresh the bank.");
        line(out, "Bank uses the full server item container, independent of searches, filters and tabs.");
        line(out, "Placeholders and nonpositive quantities are excluded. Original IDs and variants are retained.");
        line(out, "Noted items show their unnoted reference ID; neither IDs nor charges are silently merged.");
        line(out, "Excluded: POH, STASH, seed vault, death/group storage, potion storage and other containers.");
        line(out, "Container ownership does not reveal contents, charges, stored supplies or account unlocks.");
        line(out, "Useful-item matching covers only the bundled catalog; absence does not prove non-ownership.");
        line(out, "Quest API entries include miniquests/subquests; this API exposes no reliable type flag.");
        line(out, "Quest counts are API-entry counts, not a count of full quests or quest points.");
        line(out, "Unlock flags are direct observations, not a guarantee a prayer is usable at current levels or in every world.");
        line(out, "Boss/encounter counts are observed totals, not session kills. Unlisted bosses are Unknown, not zero.");
        line(out, "Open your own Combat Achievements boss details to observe existing counts; personal KC messages also update them.");
        line(out, "No hiscores lookup, other players' logs, inferred KC, or automatic interface/game actions are used.");
        line(out, "Deferred: diaries, Slayer task/points/rewards, minigame currencies, other unlock flags, Word export.");
        line(out, "Private identifiers and credentials are never included. No account data is sent anywhere.");
        line(out, "Disabled sections also exclude their containers from the useful-items summary.");

        if (options.skills)
        {
            section(out, "SKILLS (actual levels; no boosted/drained levels)");
            if (s.skillsAt == null) { line(out, "Skills not yet observed"); }
            s.skills.stream().sorted(Comparator.comparing(r -> r.name)).forEach(r -> line(out,
                clean(r.name) + " | Level " + (r.level == null ? "Unknown" : r.level)
                + " | XP " + (r.xp == null ? "Unknown" : r.xp) + " | XP to next actual level: "
                + (r.level == null || r.xp == null ? "Unknown"
                : r.nextXp == null ? "N/A (maximum actual level)" : r.nextXp)));
        }
        if (options.quests)
        {
            section(out, "QUESTS AND MINIQUEST API ENTRIES");
            line(out, "Quest points: " + (s.questPoints == null ? "Unknown" : s.questPoints));
            if (s.questsAt == null) { line(out, "Quest states not yet observed"); }
            for (String state : List.of("Completed", "In progress", "Not started", "Unknown"))
            {
                List<Snapshot.QuestRow> rows = new ArrayList<>();
                s.quests.stream().filter(q -> q.state.equals(state)).sorted(Comparator.comparing(q -> q.name)).forEach(rows::add);
                line(out, state + " (" + rows.size() + "):");
                rows.forEach(q -> line(out, "  " + clean(q.name)));
            }
        }
        if (options.unlocks)
        {
            section(out, "VERIFIED UNLOCK FLAGS (last observed)");
            s.unlocks.stream().sorted(Comparator.comparing(r -> r.kind.label)).forEach(r -> line(out,
                r.kind.label + ": " + r.value + " | " + freshness(r.observedAt, sessionStart)));
            line(out, "Knight Waves is the training requirement used by the prayer interface; levels and other requirements remain separate.");
            line(out, "A scroll unlock flag does not mean the prayer is active or usable with your current stats.");
        }
        if (options.bossCounts)
        {
            section(out, "BOSS / ENCOUNTER COUNTS (last-observed totals)");
            if (s.bossCounts.isEmpty()) { line(out, "Boss counts not yet observed. Unknown, not zero."); }
            s.bossCounts.stream().sorted(Comparator.comparing(r -> r.name, String.CASE_INSENSITIVE_ORDER)).forEach(r -> line(out,
                r.name + " | Count " + r.count + " | Source: " + r.source.label
                    + " | " + freshness(r.observedAt, sessionStart)));
            line(out, "Unlisted bosses: Unknown. Open Combat Achievements > Bosses > a boss's details to refresh it.");
            line(out, "Raid modes and different encounters are separate; counts are never added together.");
        }
        if (options.bank) { container(out, "BANK", s.bank, sessionStart); }
        if (options.inventory) { container(out, "INVENTORY", s.inventory, sessionStart); }
        if (options.equipment) { container(out, "EQUIPMENT", s.equipment, sessionStart); }
        if (options.useful)
        {
            section(out, "USEFUL ITEMS (separate last-observed quantities by location)");
            for (Map.Entry<String, Set<Integer>> group : catalog.groups.entrySet())
            {
                line(out, group.getKey() + ":");
                boolean found = false;
                if (options.bank) { found |= useful(out, "Bank", s.bank, group.getValue(), sessionStart); }
                if (options.inventory) { found |= useful(out, "Inventory", s.inventory, group.getValue(), sessionStart); }
                if (options.equipment) { found |= useful(out, "Equipment", s.equipment, group.getValue(), sessionStart); }
                if (!found) { line(out, "  Not found in observed containers (enabled sections only)."); }
            }
        }
        return out.toString();
    }

    private boolean useful(StringBuilder out, String location, Snapshot.Container c, Set<Integer> ids, Instant session)
    {
        boolean found = false;
        // Group identical raw variants only within this one location. No cross-container total.
        Map<String, Long> quantities = new TreeMap<>();
        for (Snapshot.ItemRow row : c.items)
        {
            if (ids.contains(row.id) || ids.contains(row.notedOf))
            { quantities.merge(itemLabel(row), row.quantity, Long::sum); }
        }
        for (Map.Entry<String, Long> row : quantities.entrySet())
        {
            line(out, "  " + location + ": " + row.getKey() + " x " + row.getValue()
                + " | " + freshness(c.observedAt, session));
            found = true;
        }
        return found;
    }

    private void container(StringBuilder out, String name, Snapshot.Container c, Instant session)
    {
        section(out, name + " (last observed)");
        if (c.observedAt == null)
        {
            line(out, name.equals("BANK") ? "Bank not yet observed. Open your bank to refresh."
                : name + " not yet observed / unavailable.");
            return;
        }
        line(out, freshness(c.observedAt, session));
        if (name.equals("BANK")) { line(out, "Open your bank to refresh."); }
        if (c.items.isEmpty()) { line(out, "Observed empty (no owned items)."); }
        c.items.stream().sorted(Comparator.comparing((Snapshot.ItemRow r) -> r.name, String.CASE_INSENSITIVE_ORDER)
            .thenComparingInt(r -> r.id).thenComparing(r -> r.slot)).forEach(r -> line(out,
                "  " + (name.equals("EQUIPMENT") ? clean(r.slot) + ": " : "") + itemLabel(r) + " x " + r.quantity));
    }

    private static String itemLabel(Snapshot.ItemRow row)
    { return clean(row.name) + " [ID " + row.id + "]" + (row.notedOf < 0 ? "" : " (noted; unnoted ID " + row.notedOf + ")"); }

    public String freshness(Instant at, Instant session)
    {
        if (at == null) { return "Not yet observed / unavailable"; }
        return date.format(at) + (session != null && !at.isBefore(session)
            ? " (observed this session; may now be stale)" : " (cached observation; may be stale)");
    }
    private static String clean(String s) { return s.replaceAll("[\\p{Cntrl}]", " "); }
    private static void section(StringBuilder b, String title) { b.append('\n').append(title).append('\n'); }
    private static void line(StringBuilder b, String s) { b.append(s).append('\n'); }
}
