package com.accountreport.core;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Explicit versioned schema; no Java object deserialization or credentials. Worker-thread only. */
public final class SnapshotStore
{
    private final LocalFiles files;
    public SnapshotStore(LocalFiles files) { this.files = files; }

    public static final class LoadResult
    {
        public final Snapshot snapshot;
        public final String warning;
        private LoadResult(Snapshot s, String w) { snapshot = s; warning = w; }
    }

    public LoadResult load(String key)
    {
        Identity.checkKey(key);
        try
        {
            String data = files.read("cache-" + key + ".properties");
            if (data == null) { return new LoadResult(null, null); }
            return new LoadResult(decode(data, key), null);
        }
        catch (IOException | RuntimeException e)
        {
            return new LoadResult(null, "Saved snapshot unavailable or corrupt; re-observe your containers. "
                + e.getClass().getSimpleName());
        }
    }

    public void save(Snapshot snapshot) throws IOException
    {
        Identity.checkKey(snapshot.key);
        files.atomicWrite("cache-" + snapshot.key + ".properties", encode(snapshot));
    }

    static String encode(Snapshot s) throws IOException
    {
        Properties p = new Properties();
        put(p, "schema", 1); put(p, "key", s.key); put(p, "reportId", s.reportId);
        put(p, "name", s.name); put(p, "type", s.accountType); put(p, "profile", s.profile);
        put(p, "combat", s.combatLevel); put(p, "total", s.totalLevel);
        put(p, "identityAt", s.identityAt); put(p, "skillsAt", s.skillsAt); put(p, "questsAt", s.questsAt);
        put(p, "questPoints", s.questPoints);
        put(p, "skills.count", s.skills.size());
        for (int i = 0; i < s.skills.size(); i++)
        {
            Snapshot.SkillRow r = s.skills.get(i); String k = "skills." + i + ".";
            put(p, k + "name", r.name); put(p, k + "level", r.level);
            put(p, k + "xp", r.xp); put(p, k + "nextXp", r.nextXp);
            put(p, k + "nextState", r.level == null || r.xp == null ? "unknown" : r.nextXp == null ? "maximum" : "value");
        }
        put(p, "quests.count", s.quests.size());
        for (int i = 0; i < s.quests.size(); i++)
        {
            Snapshot.QuestRow r = s.quests.get(i); String k = "quests." + i + ".";
            put(p, k + "name", r.name); put(p, k + "state", r.state);
        }
        writeContainer(p, "bank", s.bank);
        writeContainer(p, "inventory", s.inventory);
        writeContainer(p, "equipment", s.equipment);
        StringWriter writer = new StringWriter(); p.store(writer, "Account Report cache; local data, schema 1");
        return writer.toString();
    }

    static Snapshot decode(String data, String expectedKey) throws IOException
    {
        if (data.length() > 8_000_000) { throw new IOException("Oversized cache"); }
        Properties p = new Properties(); p.load(new StringReader(data));
        if (!"1".equals(required(p, "schema")) || !expectedKey.equals(required(p, "key")))
        { throw new IOException("Cache version or character mismatch"); }
        Snapshot.Builder b = new Snapshot.Builder(expectedKey);
        b.reportId = UUID.fromString(required(p, "reportId")).toString();
        b.name = required(p, "name"); b.accountType = required(p, "type"); b.profile = required(p, "profile");
        b.combatLevel = number(p, "combat"); b.totalLevel = number(p, "total");
        b.identityAt = time(p, "identityAt"); b.skillsAt = time(p, "skillsAt"); b.questsAt = time(p, "questsAt");
        b.questPoints = optionalNumber(p, "questPoints");
        List<Snapshot.SkillRow> skills = new ArrayList<>();
        for (int i = 0, n = count(p, "skills.count"); i < n; i++)
        {
            String k = "skills." + i + ".";
            Integer level = optionalNumber(p, k + "level");
            Integer xp = optionalNumber(p, k + "xp");
            Integer next = optionalNumber(p, k + "nextXp");
            String state = required(p, k + "nextState");
            String expected = level == null || xp == null ? "unknown" : next == null ? "maximum" : "value";
            if (!state.equals(expected)) { throw new IOException("Inconsistent skill availability"); }
            skills.add(new Snapshot.SkillRow(required(p, k + "name"), level, xp, next));
        }
        b.skills = skills;
        List<Snapshot.QuestRow> quests = new ArrayList<>();
        for (int i = 0, n = count(p, "quests.count"); i < n; i++)
        {
            String k = "quests." + i + ".";
            String state = required(p, k + "state");
            if (!List.of("Completed", "In progress", "Not started", "Unknown").contains(state))
            { throw new IOException("Unknown quest state"); }
            quests.add(new Snapshot.QuestRow(required(p, k + "name"), state));
        }
        b.quests = quests;
        if ((b.skillsAt == null && !skills.isEmpty()) || (b.questsAt == null && !quests.isEmpty()))
        { throw new IOException("Missing observation time"); }
        b.bank = readContainer(p, "bank"); b.inventory = readContainer(p, "inventory");
        b.equipment = readContainer(p, "equipment");
        return b.build();
    }

    private static void writeContainer(Properties p, String prefix, Snapshot.Container c)
    {
        put(p, prefix + ".at", c.observedAt); put(p, prefix + ".count", c.items.size());
        for (int i = 0; i < c.items.size(); i++)
        {
            Snapshot.ItemRow r = c.items.get(i); String k = prefix + "." + i + ".";
            put(p, k + "id", r.id); put(p, k + "name", r.name); put(p, k + "quantity", r.quantity);
            put(p, k + "slot", r.slot); put(p, k + "notedOf", r.notedOf);
        }
    }

    private static Snapshot.Container readContainer(Properties p, String prefix) throws IOException
    {
        List<Snapshot.ItemRow> items = new ArrayList<>();
        for (int i = 0, n = count(p, prefix + ".count"); i < n; i++)
        {
            String k = prefix + "." + i + ".";
            items.add(new Snapshot.ItemRow(number(p, k + "id"), required(p, k + "name"),
                Long.parseLong(required(p, k + "quantity")), required(p, k + "slot"),
                Integer.parseInt(required(p, k + "notedOf"))));
        }
        return new Snapshot.Container(time(p, prefix + ".at"), items);
    }

    private static void put(Properties p, String key, Object value)
    { if (value != null) { p.setProperty(key, value.toString()); } }
    private static String required(Properties p, String key) throws IOException
    {
        String s = p.getProperty(key);
        if (s == null) { throw new IOException("Missing field " + key); }
        return s;
    }
    private static int number(Properties p, String key) throws IOException
    {
        int n = Integer.parseInt(required(p, key));
        if (n < 0) { throw new IOException("Negative value"); }
        return n;
    }
    private static Integer optionalNumber(Properties p, String key) throws IOException
    { return p.containsKey(key) ? number(p, key) : null; }
    private static int count(Properties p, String key) throws IOException
    {
        int n = number(p, key);
        if (n > 20_000) { throw new IOException("Oversized list"); }
        return n;
    }
    private static Instant time(Properties p, String key)
    { return p.containsKey(key) ? Instant.parse(p.getProperty(key)) : null; }
}
