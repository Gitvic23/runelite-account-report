package com.accountreport.core;

import java.util.Objects;

/** Client-thread generation guard: late cache callbacks cannot revive an old character/session. */
public final class SessionGate
{
    private long generation;
    private String key;
    public long select(String nextKey)
    {
        Identity.checkKey(nextKey);
        if (!Objects.equals(key, nextKey)) { generation++; key = nextKey; }
        return generation;
    }
    public void clear() { generation++; key = null; }
    public boolean accepts(long token, String candidate)
    { return generation == token && Objects.equals(key, candidate) && key != null; }
    public String key() { return key; }
    public long token() { return generation; }
}
