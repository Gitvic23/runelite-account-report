package com.accountreport.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

/** Generates docs/example-report.txt using the real formatter. Never reads an account. */
public final class SyntheticExample
{
    public static void main(String[] args) throws Exception
    {
        Snapshot.Builder b = Fixtures.character();
        b.bank = new Snapshot.Container(Fixtures.TIME.minusSeconds(86400), List.of(
            Fixtures.item(12791, "Rune pouch", 1), Fixtures.item(13226, "Herb sack", 1),
            Fixtures.item(554, "Fire rune", 2500), Fixtures.item(9185, "Rune crossbow", 1)));
        b.inventory = new Snapshot.Container(Fixtures.TIME, List.of(Fixtures.item(12791, "Rune pouch", 1)));
        b.equipment = new Snapshot.Container(Fixtures.TIME, List.of(new Snapshot.ItemRow(6570, "Fire cape", 1, "CAPE", -1)));
        String text = "SYNTHETIC EXAMPLE ONLY — FICTIONAL ACCOUNT, ABBREVIATED SKILLS/QUESTS\n"
            + "The real plugin lists every skill and quest API entry. These fixture values are not an account assessment.\n\n"
            + new ReportFormatter(UsefulCatalog.load(), ZoneId.of("America/Chicago")).format(
                b.build(), ReportOptions.all(), Fixtures.TIME.plusSeconds(120), Fixtures.TIME.minusSeconds(30));
        Files.writeString(Path.of(args[0]), text, StandardCharsets.UTF_8);
    }
}
