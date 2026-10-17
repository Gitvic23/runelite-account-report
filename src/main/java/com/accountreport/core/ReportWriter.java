package com.accountreport.core;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class ReportWriter
{
    private final LocalFiles files;
    public ReportWriter(LocalFiles files) { this.files = files; }
    public String write(Snapshot snapshot, String text, boolean history, Instant generated) throws IOException
    {
        String id = UUID.fromString(snapshot.reportId).toString();
        // Stable across renames; this random ID is unrelated to the game's internal identity.
        String latest = "account-report-" + id + ".txt";
        files.atomicWrite(latest, text);
        if (history)
        {
            String at = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC).format(generated);
            files.atomicWrite("history-" + at + "-" + Identity.safeReportName(snapshot.name, id), text);
        }
        return latest;
    }
}
