package com.accountreport.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContainerCollectorTest
{
    @Test public void excludesPositiveQuantityPlaceholderAndInvalidSlots()
    {
        Snapshot.Container c = ContainerCollector.observe(Fixtures.TIME, List.of(
            candidate(12791, 1, -1), candidate(9999, 50, 14401), candidate(-1, 1, -1),
            candidate(554, 0, -1), candidate(555, -1, -1)));
        assertEquals(1, c.items.size());
        assertEquals(12791, c.items.get(0).id);
    }

    @Test public void preservesAllRawVariantsAndLongQuantities()
    {
        Snapshot.Container c = ContainerCollector.observe(Fixtures.TIME, List.of(
            candidate(12791, 1, -1), candidate(27281, Integer.MAX_VALUE, -1)));
        assertEquals(2, c.items.size());
        assertEquals(27281, c.items.get(1).id);
        assertEquals(2147483647L, c.items.get(1).quantity);
    }

    @Test public void snapshotsDefensivelyCopyTheirCollections()
    {
        List<Snapshot.ItemRow> input = new ArrayList<>(); input.add(Fixtures.item(1, "A", 1));
        Snapshot.Container c = new Snapshot.Container(Fixtures.TIME, input);
        input.clear(); assertEquals(1, c.items.size());
        try { c.items.clear(); fail("Mutable snapshot"); } catch (UnsupportedOperationException expected) { }
    }

    @Test public void emptyReceivedContainerIsStillObserved()
    {
        Snapshot.Container c = ContainerCollector.observe(Fixtures.TIME, List.of());
        assertNotNull(c.observedAt); assertTrue(c.items.isEmpty());
        assertNull(Snapshot.Container.unknown().observedAt);
    }

    private ContainerCollector.Candidate candidate(int id, int quantity, int placeholder)
    { return new ContainerCollector.Candidate(id, quantity, "Item", "0", placeholder, -1); }
}
