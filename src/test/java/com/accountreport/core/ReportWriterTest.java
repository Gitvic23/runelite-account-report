package com.accountreport.core;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReportWriterTest
{
    @Test public void latestIsStableAcrossRenameAndHistoryIsOptIn() throws Exception
    {
        SnapshotStoreTest.MemoryFiles files = new SnapshotStoreTest.MemoryFiles();
        ReportWriter writer = new ReportWriter(files);
        Snapshot.Builder b = Fixtures.character();
        String first = writer.write(b.build(), "First", false, Fixtures.TIME);
        b.name = "New name";
        assertEquals(first, writer.write(b.build(), "Second", false, Fixtures.TIME.plusSeconds(1)));
        assertEquals(1, files.data.size()); assertEquals("Second", files.data.get(first));
        writer.write(b.build(), "Third", true, Fixtures.TIME.plusSeconds(2));
        assertEquals(2, files.data.size());
    }
    @Test public void maliciousDisplayNameCannotEscapeFolder() throws Exception
    {
        Snapshot.Builder b = Fixtures.character(); b.name = "../../CON:bad\\name\u0000";
        SnapshotStoreTest.MemoryFiles files = new SnapshotStoreTest.MemoryFiles();
        new ReportWriter(files).write(b.build(), "UTF-8: café", true, Fixtures.TIME);
        for (String name : files.data.keySet())
        {
            assertFalse(name.contains("..")); assertFalse(name.contains("/"));
            assertFalse(name.contains("\\")); assertFalse(name.contains(":"));
            assertFalse(name.contains(Fixtures.KEY));
        }
    }
    @Test public void invalidReportIdFailsBeforeWrite() throws Exception
    {
        Snapshot.Builder b = Fixtures.character(); b.reportId = "../../escape";
        SnapshotStoreTest.MemoryFiles files = new SnapshotStoreTest.MemoryFiles();
        try { new ReportWriter(files).write(b.build(), "text", false, Fixtures.TIME); fail("Unsafe ID accepted"); }
        catch (IllegalArgumentException expected) { assertTrue(files.data.isEmpty()); }
    }
}
