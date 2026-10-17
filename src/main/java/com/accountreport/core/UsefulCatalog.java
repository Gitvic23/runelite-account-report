package com.accountreport.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Curated IDs from the official ItemID source; grouping never canonicalizes variants. */
public final class UsefulCatalog
{
    public final Map<String, Set<Integer>> groups;
    public UsefulCatalog(Map<String, Set<Integer>> input)
    {
        Map<String, Set<Integer>> copy = new TreeMap<>();
        input.forEach((k, v) -> copy.put(k, Collections.unmodifiableSet(new TreeSet<>(v))));
        groups = Collections.unmodifiableMap(copy);
    }
    public static UsefulCatalog load() throws IOException
    {
        InputStream in = UsefulCatalog.class.getResourceAsStream("/useful-items.tsv");
        if (in == null) { throw new IOException("Missing useful item catalog"); }
        Map<String, Set<Integer>> groups = new TreeMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.startsWith("#") || line.isEmpty()) { continue; }
                String[] fields = line.split("\t");
                groups.computeIfAbsent(fields[0], k -> new TreeSet<>()).add(Integer.parseInt(fields[1]));
            }
        }
        return new UsefulCatalog(groups);
    }
}
