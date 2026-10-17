package com.accountreport.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressionTest
{
    @Test public void knightWavesUsesTheVerifiedCompletionThresholdRatherThanQuestOrPrayerActivation()
    {
        assertEquals("Incomplete", Progression.unlockValue(Progression.Unlock.KNIGHT_WAVES, 0));
        assertEquals("Incomplete", Progression.unlockValue(Progression.Unlock.KNIGHT_WAVES, 7));
        assertEquals("Completed", Progression.unlockValue(Progression.Unlock.KNIGHT_WAVES, 8));
        assertEquals("Completed", Progression.unlockValue(Progression.Unlock.KNIGHT_WAVES, 9));
        assertEquals("Unknown", Progression.unlockValue(Progression.Unlock.KNIGHT_WAVES, -1));
    }

    @Test public void binaryUnlockFlagsRejectUnexpectedValuesAndMissingObservationsStartUnknown()
    {
        assertEquals("Not unlocked", Progression.unlockValue(Progression.Unlock.RIGOUR, 0));
        assertEquals("Unlocked", Progression.unlockValue(Progression.Unlock.RIGOUR, 1));
        assertEquals("Unknown", Progression.unlockValue(Progression.Unlock.RIGOUR, 2));
        assertEquals("Unknown", Progression.unlockValue(Progression.Unlock.RIGOUR, -1));
        for (Snapshot.UnlockRow row : Progression.unknownUnlocks())
        { assertEquals("Unknown", row.value); assertNull(row.observedAt); }
    }

    @Test public void parsesAbsoluteSystemCountsIncludingMarkupAndThousands()
    {
        Snapshot.BossCount count = Progression.fromGameMessage(
            "Your <col=ff0000>Kree'arra</col> kill count is: <col=ff0000>1,234</col>.", Fixtures.TIME);
        assertNotNull(count); assertEquals("Kree'arra", count.name); assertEquals(1234, count.count);
        assertEquals(Fixtures.TIME, count.observedAt); assertEquals(Progression.BossSource.GAME_MESSAGE, count.source);
        assertEquals(1, Progression.fromGameMessage(
            "Your <col=6800bf>Kalphite Queen (Echo)</col> kill count is:<col=e00a19>1</col>", Fixtures.TIME).count);
    }

    @Test public void parsesRaidAndCompletionMessageFormsWithoutCombiningModes()
    {
        Snapshot.BossCount normal = Progression.fromGameMessage(
            "Your completed Theatre of Blood count is: <col=ff0000>73</col>.", Fixtures.TIME);
        Snapshot.BossCount entry = Progression.fromGameMessage(
            "Your completed Theatre of Blood: Entry Mode count is: <col=ff0000>9</col>.", Fixtures.TIME);
        List<Snapshot.BossCount> rows = Progression.observeBoss(List.of(normal), entry);
        assertEquals(2, rows.size()); assertEquals(73, rows.get(0).count); assertEquals(9, rows.get(1).count);
        assertEquals("TzHaar-Ket-Rak's First Challenge", Progression.fromGameMessage(
            "Your completion count for TzHaar-Ket-Rak's First Challenge is: <col=ff0000>3</col>.", Fixtures.TIME).name);
        assertEquals("Tempoross", Progression.fromGameMessage("Your subdued Tempoross count is: 10.", Fixtures.TIME).name);
    }

    @Test public void malformedUnrelatedNegativeAndOverflowMessagesNeverBecomeCounts()
    {
        for (String message : List.of("I have 42 Zulrah kills", "Your Zulrah kill count is: -1.",
            "Your Zulrah kill count is: 1,23.", "Your Zulrah kill count is: 99999999999999999.",
            "Your loot is worth: 100", "Other player: Your Zulrah kill count is: 100.",
            "Your Zulrah kill count is: 4. Personal best: 2:00", "Your Zulrah kill count is: 01."))
        { assertNull(message, Progression.fromGameMessage(message, Fixtures.TIME)); }
    }

    @Test public void anExplicitZeroIsObservedWhileMissingOrUnavailableCountsStayAbsent()
    {
        assertEquals(0, Progression.fromBossPage("Zulrah", "Kill Count: <col=ffffff>0</col>", Fixtures.TIME).count);
        assertEquals(0, Progression.fromGameMessage("Your Zulrah kill count is: 0.", Fixtures.TIME).count);
        assertNull(Progression.fromBossPage("Zulrah", "Kill Count: N/A", Fixtures.TIME));
        assertNull(Progression.fromBossPage("Zulrah", "Personal Best: 0:42", Fixtures.TIME));
        assertNull(Progression.fromBossPage("", "Kill Count: 0", Fixtures.TIME));
    }

    @Test public void gauntletPageAndChatAliasUpdateOneCounterButCorruptedRemainsSeparate()
    {
        Snapshot.BossCount normal = Progression.fromBossPage("The Gauntlet", "Kill Count: 10", Fixtures.TIME);
        Snapshot.BossCount newer = Progression.fromGameMessage("Your Gauntlet completion count is: 11.", Fixtures.TIME.plusSeconds(1));
        List<Snapshot.BossCount> rows = Progression.observeBoss(List.of(normal), newer);
        rows = Progression.observeBoss(rows, Progression.fromBossPage("The Corrupted Gauntlet", "Kill Count: 7", Fixtures.TIME));
        assertEquals(2, rows.size()); assertEquals(11, rows.get(0).count); assertEquals(7, rows.get(1).count);
    }

    @Test public void keepsNewerEvidenceRatherThanAddingCountsOrKeepingAnInventedMaximum()
    {
        Snapshot.BossCount newer = new Snapshot.BossCount("Zulrah", 20, Fixtures.TIME, Progression.BossSource.GAME_MESSAGE);
        Snapshot.BossCount older = new Snapshot.BossCount("Zulrah", 19, Fixtures.TIME.minusSeconds(1), Progression.BossSource.COMBAT_ACHIEVEMENTS);
        List<Snapshot.BossCount> rows = Progression.observeBoss(List.of(newer), older);
        assertEquals(20, rows.get(0).count);
        Snapshot.BossCount refreshed = new Snapshot.BossCount("Zulrah", 18, Fixtures.TIME.plusSeconds(1), Progression.BossSource.COMBAT_ACHIEVEMENTS);
        assertEquals(18, Progression.observeBoss(rows, refreshed).get(0).count);
    }

    @Test public void newObservationsAreImmutableAcrossThreads()
    {
        Snapshot.Builder b = Fixtures.character();
        List<Snapshot.BossCount> list = new ArrayList<>();
        list.add(new Snapshot.BossCount("Zulrah", 1, Fixtures.TIME, Progression.BossSource.GAME_MESSAGE));
        b.bossCounts = list; Snapshot snapshot = b.build(); list.clear();
        assertEquals(1, snapshot.bossCounts.size());
        try { snapshot.bossCounts.clear(); fail("Mutable observations"); } catch (UnsupportedOperationException expected) { }
    }
}
