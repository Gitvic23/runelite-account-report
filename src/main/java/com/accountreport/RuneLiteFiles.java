package com.accountreport;

import com.accountreport.core.LocalFiles;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import net.runelite.client.util.Filepath;

/** All production disk access stays inside RuneLite's supported plugin Filepath. */
final class RuneLiteFiles implements LocalFiles
{
    private final Filepath root;
    RuneLiteFiles(Filepath root) throws IOException
    {
        this.root = root;
        root.createDirectories();
    }

    @Override
    public String read(String name) throws IOException
    {
        Filepath file = root.joinSegment(name);
        if (!file.exists()) { return null; }
        if (file.size() > 8_000_000) { throw new IOException("Cache exceeds size limit"); }
        StringBuilder text = new StringBuilder();
        try (Reader reader = file.openBufferedReader())
        {
            char[] buffer = new char[8192];
            int n;
            while ((n = reader.read(buffer)) >= 0)
            {
                text.append(buffer, 0, n);
                if (text.length() > 8_000_000) { throw new IOException("Cache exceeds size limit"); }
            }
        }
        return text.toString();
    }

    @Override
    public void atomicWrite(String name, String content) throws IOException
    {
        Filepath target = root.joinSegment(name);
        Filepath temp = root.createTempFile("report-", ".tmp");
        try
        {
            temp.write(content);
            try { temp.moveTo(target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e)
            { temp.moveTo(target, StandardCopyOption.REPLACE_EXISTING); }
        }
        finally { temp.deleteIfExists(); }
    }
}
