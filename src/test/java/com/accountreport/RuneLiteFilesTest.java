package com.accountreport;

import com.accountreport.core.Fixtures;
import com.accountreport.core.Progression;
import com.accountreport.core.ReportWriter;
import com.accountreport.core.Snapshot;
import com.accountreport.core.SnapshotStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.runelite.client.util.Filepath;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class RuneLiteFilesTest
{
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void realFilepathWritesUtf8ReplacesLatestAndLeavesNoTemps() throws Exception
    {
        Path directory = temp.newFolder().toPath();
        // Unchecked is used only to create a test root, never in production plugin code.
        RuneLiteFiles files = new RuneLiteFiles(Filepath.Unchecked.getRooted(directory));
        ReportWriter writer = new ReportWriter(files);
        String name = writer.write(Fixtures.character().build(), "First", false, Fixtures.TIME);
        writer.write(Fixtures.character().build(), "café 漁船\n", false, Fixtures.TIME);
        assertEquals("café 漁船\n", Files.readString(directory.resolve(name), StandardCharsets.UTF_8));
        try (var stream = Files.list(directory)) { assertEquals(1, stream.count()); }
    }

    @Test public void actualDiskCacheSurvivesNewStoreAndRejectsCorruption() throws Exception
    {
        Path directory = temp.newFolder().toPath();
        RuneLiteFiles files = new RuneLiteFiles(Filepath.Unchecked.getRooted(directory));
        Snapshot.Builder b = Fixtures.character();
        b.bank = new Snapshot.Container(Fixtures.TIME, List.of(Fixtures.item(9185, "Rune crossbow", 1)));
        new SnapshotStore(files).save(b.build());
        Snapshot loaded = new SnapshotStore(new RuneLiteFiles(Filepath.Unchecked.getRooted(directory))).load(Fixtures.KEY).snapshot;
        assertEquals(9185, loaded.bank.items.get(0).id);
        assertEquals(Fixtures.TIME, loaded.bank.observedAt);
        files.atomicWrite("cache-" + Fixtures.KEY + ".properties", "corrupted");
        assertNotNull(new SnapshotStore(files).load(Fixtures.KEY).warning);
    }

    @Test public void productionFileAdapterRejectsTraversal() throws Exception
    {
        RuneLiteFiles files = new RuneLiteFiles(Filepath.Unchecked.getRooted(temp.newFolder().toPath()));
        for (String name : List.of("../outside", "/absolute", "CON.txt", "bad\\name"))
        {
            try { files.atomicWrite(name, "bad"); fail("Unsafe filename accepted: " + name); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void actualDiskUpgradeRetainsOldBankAndNewEncounterEvidenceAcrossReload() throws Exception
    {
        Path directory = temp.newFolder().toPath();
        RuneLiteFiles files = new RuneLiteFiles(Filepath.Unchecked.getRooted(directory));
        String legacy;
        try (var in = getClass().getResourceAsStream("/fixtures/cache-v1.properties"))
        { assertNotNull(in); legacy = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        files.atomicWrite("cache-" + Fixtures.KEY + ".properties", legacy);
        SnapshotStore store = new SnapshotStore(files);
        Snapshot.Builder b = store.load(Fixtures.KEY).snapshot.toBuilder();
        b.bossCounts = List.of(new Snapshot.BossCount("Zulrah", 0, Fixtures.TIME, Progression.BossSource.COMBAT_ACHIEVEMENTS));
        b.unlocks = List.of(new Snapshot.UnlockRow(Progression.Unlock.KNIGHT_WAVES, "Completed", Fixtures.TIME));
        store.save(b.build());
        Snapshot restored = new SnapshotStore(new RuneLiteFiles(Filepath.Unchecked.getRooted(directory))).load(Fixtures.KEY).snapshot;
        assertEquals(12791, restored.bank.items.get(0).id); assertEquals(Fixtures.TIME, restored.bank.observedAt);
        assertEquals(Fixtures.character().reportId, restored.reportId);
        assertEquals(0, restored.bossCounts.get(0).count); assertEquals(Fixtures.TIME, restored.bossCounts.get(0).observedAt);
        assertEquals("Completed", restored.unlocks.get(0).value);
        assertTrue(files.read("cache-" + Fixtures.KEY + ".properties").contains("schema=2"));
    }
}
