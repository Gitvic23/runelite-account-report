package com.accountreport;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Official external-plugin development launcher. Does not install into normal RuneLite. */
public final class AccountReportLauncher
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(AccountReportPlugin.class);
        RuneLite.main(args);
    }
}
