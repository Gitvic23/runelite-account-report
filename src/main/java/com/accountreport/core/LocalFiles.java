package com.accountreport.core;

import java.io.IOException;

/** Implementations must confine names to their owned directory and replace atomically. */
public interface LocalFiles
{
    String read(String name) throws IOException; // null when absent
    void atomicWrite(String name, String content) throws IOException;
}
