package com.accountreport.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Definition-independent filtering, shared by the adapter and the tests. */
public final class ContainerCollector
{
    private ContainerCollector() { }
    public static final class Candidate
    {
        public final int id;
        public final int quantity;
        public final String name;
        public final String slot;
        public final int placeholderTemplate;
        public final int notedOf;
        public Candidate(int id, int quantity, String name, String slot, int placeholderTemplate, int notedOf)
        {
            this.id = id; this.quantity = quantity; this.name = name; this.slot = slot;
            this.placeholderTemplate = placeholderTemplate; this.notedOf = notedOf;
        }
    }

    public static Snapshot.Container observe(Instant at, List<Candidate> received)
    {
        List<Snapshot.ItemRow> items = new ArrayList<>();
        for (Candidate c : received)
        {
            if (c.id < 0 || c.quantity <= 0 || c.placeholderTemplate != -1) { continue; }
            items.add(new Snapshot.ItemRow(c.id, c.name, c.quantity, c.slot, c.notedOf));
        }
        return new Snapshot.Container(at, items);
    }
}
