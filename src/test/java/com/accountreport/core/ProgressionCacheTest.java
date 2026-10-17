package com.accountreport.core;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressionCacheTest
{
    @Test public void genuinelyVersionOneCacheMigratesWithoutLosingBankTimesOrReportIdentity() throws Exception
    {
        String data;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/cache-v1.properties"))
        { assertNotNull(in); data = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        assertTrue(data.contains("schema=1"));
        Snapshot migrated = SnapshotStore.decode(data, Fixtures.KEY);
        assertEquals("Synthetic Iron", migrated.name);
        assertEquals(Fixtures.TIME, migrated.bank.observedAt);
        assertEquals(12791, migrated.bank.items.get(0).id);
        assertEquals(Fixtures.TIME, migrated.inventory.observedAt);
        assertTrue(migrated.inventory.items.isEmpty()); assertNull(migrated.equipment.observedAt);
        assertEquals(Fixtures.character().reportId, migrated.reportId);
        assertTrue(migrated.bossCounts.isEmpty());
        assertTrue(migrated.unlocks.stream().allMatch(r -> r.value.equals("Unknown") && r.observedAt == null));
        String upgraded = SnapshotStore.encode(migrated); assertTrue(upgraded.contains("schema=2"));
        Snapshot reloaded = SnapshotStore.decode(upgraded, Fixtures.KEY);
        assertEquals(migrated.reportId, reloaded.reportId);
        assertEquals(Fixtures.TIME, reloaded.bank.observedAt);
    }

    @Test public void versionTwoRoundTripRetainsZeroUnknownTimestampsAndSource() throws Exception
    {
        Snapshot.Builder b = withProgression();
        Snapshot reloaded = SnapshotStore.decode(SnapshotStore.encode(b.build()), Fixtures.KEY);
        assertEquals("Completed", reloaded.unlocks.get(0).value);
        assertEquals(Fixtures.TIME, reloaded.unlocks.get(0).observedAt);
        assertTrue(reloaded.unlocks.stream().anyMatch(r -> r.value.equals("Unknown") && r.observedAt == null));
        assertEquals(0, reloaded.bossCounts.get(0).count);
        assertEquals(Progression.BossSource.COMBAT_ACHIEVEMENTS, reloaded.bossCounts.get(0).source);
        assertEquals(Fixtures.TIME.minusSeconds(60), reloaded.bossCounts.get(0).observedAt);
    }

    @Test public void bossAndUnlockCachesRemainIsolatedPerCharacter() throws Exception
    {
        SnapshotStoreTest.MemoryFiles files = new SnapshotStoreTest.MemoryFiles();
        SnapshotStore store = new SnapshotStore(files); store.save(withProgression().build());
        String otherKey = Identity.key(987654L, "STANDARD");
        Snapshot.Builder other = new Snapshot.Builder(otherKey); other.name = "Other character";
        store.save(other.build());
        Snapshot second = store.load(otherKey).snapshot;
        assertTrue(second.bossCounts.isEmpty());
        assertTrue(second.unlocks.stream().allMatch(r -> r.value.equals("Unknown")));
        assertEquals(1, store.load(Fixtures.KEY).snapshot.bossCounts.size());
    }

    @Test public void malformedNewObservationsAreRejectedInsteadOfInventingDefaults() throws Exception
    {
        String good = SnapshotStore.encode(withProgression().build());
        for (String bad : List.of(good.replace("bosses.0.count=0", "bosses.0.count=-1"),
            good.replace("bosses.0.at=", "missingBossTime="), good.replace("bosses.0.source=COMBAT_ACHIEVEMENTS", "bosses.0.source=HISCORE"),
            good.replace("unlocks.0.at=", "missingUnlockTime="), good.replace("unlocks.0.value=Completed", "unlocks.0.value=Invented")))
        {
            SnapshotStoreTest.MemoryFiles files = new SnapshotStoreTest.MemoryFiles();
            files.atomicWrite("cache-" + Fixtures.KEY + ".properties", bad);
            SnapshotStore.LoadResult loaded = new SnapshotStore(files).load(Fixtures.KEY);
            assertNull(loaded.snapshot); assertNotNull(loaded.warning);
        }
    }

    @Test public void duplicateBossRowsAreRejectedCaseInsensitively() throws Exception
    {
        Snapshot.Builder b = withProgression();
        b.bossCounts = List.of(b.bossCounts.get(0), new Snapshot.BossCount("zulrah", 5, Fixtures.TIME, Progression.BossSource.GAME_MESSAGE));
        try { SnapshotStore.decode(SnapshotStore.encode(b.build()), Fixtures.KEY); fail("Duplicate counters accepted"); }
        catch (IOException expected) { }
    }

    private Snapshot.Builder withProgression()
    {
        Snapshot.Builder b = Fixtures.character();
        b.unlocks = List.of(new Snapshot.UnlockRow(Progression.Unlock.KNIGHT_WAVES, "Completed", Fixtures.TIME));
        b.bossCounts = List.of(new Snapshot.BossCount("Zulrah", 0, Fixtures.TIME.minusSeconds(60), Progression.BossSource.COMBAT_ACHIEVEMENTS));
        return b;
    }
}
