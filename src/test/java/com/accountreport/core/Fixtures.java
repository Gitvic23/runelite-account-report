package com.accountreport.core;

import java.time.Instant;
import java.util.List;

public final class Fixtures
{
    public static final Instant TIME = Instant.parse("2026-10-17T16:00:00Z");
    public static final String KEY = Identity.key(123456L, "STANDARD");
    private Fixtures() { }
    public static Snapshot.Builder character()
    {
        Snapshot.Builder b = new Snapshot.Builder(KEY);
        b.reportId = "4b00668d-e765-4928-b8ea-77fffa6877a1";
        b.name = "Synthetic Iron"; b.accountType = "Ironman"; b.profile = "STANDARD";
        b.combatLevel = 90; b.totalLevel = 1500; b.identityAt = TIME;
        b.skillsAt = TIME; b.skills = List.of(new Snapshot.SkillRow("Magic", 75, 1_210_421, 126_022),
            new Snapshot.SkillRow("Attack", 99, 13_034_431, null));
        b.questsAt = TIME; b.questPoints = 150;
        b.quests = List.of(new Snapshot.QuestRow("Cook's Assistant", "Completed"),
            new Snapshot.QuestRow("Dragon Slayer I", "In progress"),
            new Snapshot.QuestRow("Dragon Slayer II", "Not started"),
            new Snapshot.QuestRow("Future API entry", "Unknown"));
        return b;
    }
    public static Snapshot.ItemRow item(int id, String name, long quantity)
    { return new Snapshot.ItemRow(id, name, quantity, "0", -1); }
}
