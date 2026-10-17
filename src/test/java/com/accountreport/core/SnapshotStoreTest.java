package com.accountreport.core;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SnapshotStoreTest
{
    static final class MemoryFiles implements LocalFiles
    {
        final Map<String, String> data = new HashMap<>();
        public String read(String name) { return data.get(name); }
        public void atomicWrite(String name, String value) { data.put(name, value); }
    }

    @Test public void roundTripPreservesBankUnknownInventoryAndEmptyEquipment() throws Exception
    {
        MemoryFiles files = new MemoryFiles();
        SnapshotStore store = new SnapshotStore(files);
        Snapshot.Builder b = Fixtures.character();
        b.bank = new Snapshot.Container(Fixtures.TIME, List.of(Fixtures.item(12791, "Rune pouch", 1)));
        b.equipment = new Snapshot.Container(Fixtures.TIME, List.of());
        store.save(b.build());
        SnapshotStore.LoadResult loaded = new SnapshotStore(files).load(Fixtures.KEY);
        assertNull(loaded.warning);
        assertEquals(12791, loaded.snapshot.bank.items.get(0).id);
        assertEquals(Fixtures.TIME, loaded.snapshot.bank.observedAt);
        assertNull(loaded.snapshot.inventory.observedAt);
        assertEquals(Fixtures.TIME, loaded.snapshot.equipment.observedAt);
        assertTrue(loaded.snapshot.equipment.items.isEmpty());
        assertEquals(b.reportId, loaded.snapshot.reportId);
    }

    @Test public void accountsAreIsolatedAndRenameDoesNotChangeIdentity() throws Exception
    {
        MemoryFiles files = new MemoryFiles(); SnapshotStore store = new SnapshotStore(files);
        Snapshot.Builder first = Fixtures.character();
        first.bank = new Snapshot.Container(Fixtures.TIME, List.of(Fixtures.item(1, "Private A", 1)));
        store.save(first.build());
        String secondKey = Identity.key(987654L, "STANDARD");
        assertNull(store.load(secondKey).snapshot);
        first.name = "Renamed character"; store.save(first.build());
        assertEquals("Renamed character", store.load(Fixtures.KEY).snapshot.name);
        assertEquals(first.reportId, store.load(Fixtures.KEY).snapshot.reportId);
        assertNotEquals(Fixtures.KEY, Identity.key(123456L, "DEADMAN"));
    }

    @Test public void missingCacheIsUnknownWithoutError()
    {
        SnapshotStore.LoadResult loaded = new SnapshotStore(new MemoryFiles()).load(Fixtures.KEY);
        assertNull(loaded.snapshot); assertNull(loaded.warning);
    }

    @Test public void corruptTruncatedAndFutureVersionCachesFailSafely() throws Exception
    {
        String valid = SnapshotStore.encode(Fixtures.character().build());
        for (String data : List.of("garbage", valid.replace("schema=1", "schema=999"),
            valid.replace("skills.count=2", "skills.count=999999999"), valid.replace("skillsAt=", "missingSkillsAt=")))
        {
            MemoryFiles files = new MemoryFiles(); files.data.put("cache-" + Fixtures.KEY + ".properties", data);
            SnapshotStore.LoadResult loaded = new SnapshotStore(files).load(Fixtures.KEY);
            assertNull(loaded.snapshot); assertNotNull(loaded.warning);
        }
    }

    @Test public void wrongIdentityInCacheCannotLoadAsAnotherCharacter() throws Exception
    {
        MemoryFiles files = new MemoryFiles(); String other = Identity.key(9, "STANDARD");
        files.data.put("cache-" + other + ".properties", SnapshotStore.encode(Fixtures.character().build()));
        assertNull(new SnapshotStore(files).load(other).snapshot);
    }

    @Test public void filesystemReadFailureIsVisible()
    {
        LocalFiles failing = new LocalFiles() {
            public String read(String n) throws IOException { throw new IOException("Denied"); }
            public void atomicWrite(String n, String s) { }
        };
        assertNotNull(new SnapshotStore(failing).load(Fixtures.KEY).warning);
    }

    @Test public void unknownSkillsSurviveCacheAndMissingNextXpIsNotMistakenForMaximum() throws Exception
    {
        Snapshot.Builder b = Fixtures.character();
        b.skills = List.of(new Snapshot.SkillRow("Magic", null, null, null));
        Snapshot restored = SnapshotStore.decode(SnapshotStore.encode(b.build()), Fixtures.KEY);
        assertNull(restored.skills.get(0).level); assertNull(restored.skills.get(0).xp);
        String corrupted = SnapshotStore.encode(Fixtures.character().build()).replace("skills.0.nextXp=126022", "missing=126022");
        try { SnapshotStore.decode(corrupted, Fixtures.KEY); fail("Missing XP was silently treated as maximum"); }
        catch (IOException expected) { }
    }

    @Test public void unsafeCacheKeyRejectedBeforeFilesystemAccess()
    {
        try { new SnapshotStore(new MemoryFiles()).load("../../credentials"); fail("Unsafe key accepted"); }
        catch (IllegalArgumentException expected) { }
    }
}
