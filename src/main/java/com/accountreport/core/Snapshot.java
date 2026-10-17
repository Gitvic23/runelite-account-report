package com.accountreport.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable values; no client objects may cross into storage or the UI. */
public final class Snapshot
{
    public static final String PLUGIN_VERSION = "1.0.0";
    public static final int FORMAT_VERSION = 1;
    public final String key; // Internal only: never render this or use it in report filenames.
    public final String reportId;
    public final String name;
    public final String accountType;
    public final String profile;
    public final int combatLevel;
    public final int totalLevel;
    public final Instant identityAt;
    public final Instant skillsAt;
    public final Instant questsAt;
    public final Integer questPoints;
    public final List<SkillRow> skills;
    public final List<QuestRow> quests;
    public final Container bank;
    public final Container inventory;
    public final Container equipment;

    public Snapshot(Builder b)
    {
        key = Objects.requireNonNull(b.key);
        reportId = Objects.requireNonNull(b.reportId);
        name = Objects.requireNonNull(b.name);
        accountType = Objects.requireNonNull(b.accountType);
        profile = Objects.requireNonNull(b.profile);
        combatLevel = b.combatLevel;
        totalLevel = b.totalLevel;
        identityAt = b.identityAt;
        skillsAt = b.skillsAt;
        questsAt = b.questsAt;
        questPoints = b.questPoints;
        skills = copy(b.skills);
        quests = copy(b.quests);
        bank = Objects.requireNonNull(b.bank);
        inventory = Objects.requireNonNull(b.inventory);
        equipment = Objects.requireNonNull(b.equipment);
    }

    private static <T> List<T> copy(List<T> input)
    {
        return Collections.unmodifiableList(new ArrayList<>(input));
    }

    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder
    {
        public String key;
        public String reportId = UUID.randomUUID().toString();
        public String name = "Unknown";
        public String accountType = "Unknown";
        public String profile = "Unknown";
        public int combatLevel;
        public int totalLevel;
        public Instant identityAt;
        public Instant skillsAt;
        public Instant questsAt;
        public Integer questPoints;
        public List<SkillRow> skills = Collections.emptyList();
        public List<QuestRow> quests = Collections.emptyList();
        public Container bank = Container.unknown();
        public Container inventory = Container.unknown();
        public Container equipment = Container.unknown();

        public Builder(String key) { this.key = key; }
        private Builder(Snapshot s)
        {
            key = s.key; reportId = s.reportId; name = s.name;
            accountType = s.accountType; profile = s.profile;
            combatLevel = s.combatLevel; totalLevel = s.totalLevel;
            identityAt = s.identityAt; skillsAt = s.skillsAt; questsAt = s.questsAt;
            questPoints = s.questPoints; skills = s.skills; quests = s.quests;
            bank = s.bank; inventory = s.inventory; equipment = s.equipment;
        }
        public Snapshot build() { return new Snapshot(this); }
    }

    public static final class SkillRow
    {
        public final String name;
        public final Integer level;
        public final Integer xp;
        public final Integer nextXp;
        public SkillRow(String name, Integer level, Integer xp, Integer nextXp)
        { this.name = name; this.level = level; this.xp = xp; this.nextXp = nextXp; }
    }

    public static final class QuestRow
    {
        public final String name;
        public final String state;
        public QuestRow(String name, String state) { this.name = name; this.state = state; }
    }

    public static final class ItemRow
    {
        public final int id;
        public final String name;
        public final long quantity;
        public final String slot;
        public final int notedOf; // -1 unless the definition explicitly identifies a noted item.
        public ItemRow(int id, String name, long quantity, String slot, int notedOf)
        {
            if (id < 0 || quantity <= 0) { throw new IllegalArgumentException("Invalid owned item"); }
            this.id = id; this.name = Objects.requireNonNull(name);
            this.quantity = quantity; this.slot = Objects.requireNonNull(slot); this.notedOf = notedOf;
        }
    }

    public static final class Container
    {
        public final Instant observedAt; // null means unavailable, not empty.
        public final List<ItemRow> items;
        public Container(Instant observedAt, List<ItemRow> items)
        {
            if (observedAt == null && !items.isEmpty()) { throw new IllegalArgumentException("Unobserved items"); }
            this.observedAt = observedAt; this.items = copy(items);
        }
        public static Container unknown() { return new Container(null, Collections.emptyList()); }
    }
}
