package com.accountreport.core;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReportFormatterTest
{
    private final ReportFormatter formatter = new ReportFormatter(
        new UsefulCatalog(Map.of("Rune pouches", Set.of(12791, 27281))), ZoneId.of("America/Chicago"));
    private String report(Snapshot.Builder b)
    { return formatter.format(b.build(), ReportOptions.all(), Fixtures.TIME.plusSeconds(120), Fixtures.TIME.minusSeconds(1)); }

    @Test public void distinguishesUnknownFromObservedEmpty()
    {
        Snapshot.Builder b = Fixtures.character();
        assertTrue(report(b).contains("Bank not yet observed"));
        b.bank = new Snapshot.Container(Fixtures.TIME, List.of());
        assertFalse(report(b).contains("Bank not yet observed"));
        assertTrue(report(b).contains("Observed empty (no owned items)."));
    }

    @Test public void deterministicOrderingAndQuestCounts()
    {
        Snapshot.Builder b = Fixtures.character();
        b.bank = new Snapshot.Container(Fixtures.TIME, List.of(Fixtures.item(2, "Zulrah's scales", 3),
            Fixtures.item(3, "Air rune", 9), Fixtures.item(1, "Air rune", 8)));
        String first = report(b);
        List<Snapshot.ItemRow> shuffled = new ArrayList<>(b.bank.items);
        Collections.reverse(shuffled); b.bank = new Snapshot.Container(Fixtures.TIME, shuffled);
        assertEquals(first, report(b));
        assertTrue(first.indexOf("Air rune [ID 1]") < first.indexOf("Air rune [ID 3]"));
        assertTrue(first.indexOf("Air rune [ID 3]") < first.indexOf("Zulrah's scales [ID 2]"));
        assertTrue(first.contains("Completed (1):\n  Cook's Assistant"));
        assertTrue(first.contains("Unknown (1):\n  Future API entry"));
    }

    @Test public void staleLocationsRemainSeparateAndVariantsKeepOriginalIds()
    {
        Snapshot.Builder b = Fixtures.character();
        b.bank = new Snapshot.Container(Fixtures.TIME.minusSeconds(86400), List.of(Fixtures.item(12791, "Rune pouch", 1)));
        b.inventory = new Snapshot.Container(Fixtures.TIME, List.of(Fixtures.item(12791, "Rune pouch", 1),
            Fixtures.item(27281, "Divine rune pouch", 1)));
        String text = report(b);
        assertTrue(text.contains("Bank: Rune pouch [ID 12791] x 1"));
        assertTrue(text.contains("Inventory: Divine rune pouch [ID 27281] x 1"));
        assertTrue(text.contains("Inventory: Rune pouch [ID 12791] x 1"));
        assertFalse(text.contains("Rune pouch [ID 12791] x 2"));
        assertTrue(text.contains("cached observation; may be stale"));
        assertFalse(text.contains("Not owned"));
    }

    @Test public void notedItemIsExplicitAndMatchesCatalogWithoutChangingItsId()
    {
        Snapshot.Builder b = Fixtures.character();
        b.inventory = new Snapshot.Container(Fixtures.TIME,
            List.of(new Snapshot.ItemRow(55555, "Rune pouch", 2, "1", 12791)));
        assertTrue(report(b).contains("Inventory: Rune pouch [ID 55555] (noted; unnoted ID 12791) x 2"));
    }

    @Test public void maximumActualLevelAndTimezoneAndPrivateIdentity()
    {
        Snapshot.Builder b = Fixtures.character();
        String text = report(b);
        assertTrue(text.contains("N/A (maximum actual level)"));
        assertTrue(text.contains("-05:00 [America/Chicago]"));
        assertFalse(text.contains(Fixtures.KEY));
        assertFalse(text.contains(b.reportId));
        assertFalse(text.contains("123456"));
    }

    @Test public void disabledSectionsDoNotLeakIntoSummary()
    {
        Snapshot.Builder b = Fixtures.character();
        b.bank = new Snapshot.Container(Fixtures.TIME, List.of(Fixtures.item(12791, "Rune pouch", 1)));
        String text = formatter.format(b.build(), new ReportOptions(false, false, false, true, true, true), Fixtures.TIME, null);
        assertFalse(text.contains("Bank: Rune pouch"));
        assertFalse(text.contains("SKILLS (actual"));
        assertTrue(text.contains("Not found in observed containers"));
    }

    @Test public void cacheAndSessionFreshnessAreClearlyLabeled()
    {
        assertTrue(formatter.freshness(Fixtures.TIME, Fixtures.TIME.plusSeconds(1)).contains("cached observation"));
        assertTrue(formatter.freshness(Fixtures.TIME, Fixtures.TIME.minusSeconds(1)).contains("observed this session"));
        assertTrue(formatter.freshness(null, Fixtures.TIME).contains("unavailable"));
    }

    @Test public void unavailableSkillValuesAreUnknownRatherThanZeroOrMaximum()
    {
        Snapshot.Builder b = Fixtures.character();
        b.skills = List.of(new Snapshot.SkillRow("Magic", null, null, null));
        String text = report(b);
        assertTrue(text.contains("Magic | Level Unknown | XP Unknown | XP to next actual level: Unknown"));
        assertFalse(text.contains("maximum actual level"));
    }

    @Test public void bundledCatalogExcludesQuestLookalikesAndVouchers() throws Exception
    {
        UsefulCatalog c = UsefulCatalog.load();
        assertTrue(c.groups.get("Essence pouches").contains(26784));
        assertFalse(c.groups.get("Essence pouches").contains(6819));
        assertFalse(c.groups.get("Essence pouches").contains(26906));
        assertFalse(c.groups.get("Rune pouches").contains(24587));
        assertFalse(c.groups.get("Gem bag").contains(766));
        assertFalse(c.groups.get("Dragon defender").contains(20143));
        assertTrue(c.groups.get("Forestry kit and baskets").contains(28143));
    }

    @Test public void progressionShowsKnownZeroAndUnknownSeparatelyWithSourceAndTime()
    {
        Snapshot.Builder b = Fixtures.character();
        b.unlocks = List.of(new Snapshot.UnlockRow(Progression.Unlock.KNIGHT_WAVES, "Completed", Fixtures.TIME));
        b.bossCounts = List.of(new Snapshot.BossCount("Zulrah", 0, Fixtures.TIME.minusSeconds(3600), Progression.BossSource.COMBAT_ACHIEVEMENTS));
        String text = report(b);
        assertTrue(text.contains("Knight Waves training: Completed"));
        assertTrue(text.contains("Zulrah | Count 0 | Source: personal Combat Achievements boss page"));
        assertTrue(text.contains("Unlisted bosses: Unknown"));
        assertTrue(text.contains("cached observation; may be stale"));
        assertFalse(text.contains("Piety: Usable"));
    }

    @Test public void newSectionsCanBeDisabledAndBossRowsHaveDeterministicOrdering()
    {
        Snapshot.Builder b = Fixtures.character();
        Snapshot.BossCount z = new Snapshot.BossCount("Zulrah", 1, Fixtures.TIME, Progression.BossSource.GAME_MESSAGE);
        Snapshot.BossCount a = new Snapshot.BossCount("Alchemical Hydra", 2, Fixtures.TIME, Progression.BossSource.GAME_MESSAGE);
        b.bossCounts = List.of(z, a); String first = report(b);
        b.bossCounts = List.of(a, z); assertEquals(first, report(b));
        assertTrue(first.indexOf("Alchemical Hydra | Count") < first.indexOf("Zulrah | Count"));
        String disabled = formatter.format(b.build(), new ReportOptions(true, true, true, true, true, true, false, false), Fixtures.TIME, null);
        assertFalse(disabled.contains("Alchemical Hydra | Count"));
        assertFalse(disabled.contains("Knight Waves training:"));
    }
}
