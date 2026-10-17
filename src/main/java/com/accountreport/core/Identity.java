package com.accountreport.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Identity
{
    private Identity() { }

    /** Account hash is per RuneScape character, not Jagex login/email. World modes are isolated. */
    public static String key(long accountHash, String profile)
    {
        if (accountHash == -1) { throw new IllegalArgumentException("Character not identified yet"); }
        try
        {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                (Long.toUnsignedString(accountHash) + ":" + profile).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) { result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); }
            return result.toString();
        }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static String safeReportName(String name, String reportId)
    {
        // Fixed prefix prevents Windows device names; suffix is random, never an account identifier.
        String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
        if (safe.length() > 24) { safe = safe.substring(0, 24); }
        return "account-" + safe + "-" + java.util.UUID.fromString(reportId) + ".txt";
    }

    public static void checkKey(String key)
    {
        if (key == null || !key.matches("[a-f0-9]{64}")) { throw new IllegalArgumentException("Invalid cache key"); }
    }
}
