package com.accountreport;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(AccountReportConfig.GROUP)
public interface AccountReportConfig extends Config
{
    String GROUP = "account-report-exporter";

    @ConfigItem(keyName = "automatic", name = "Automatic local export", description = "Write at most once a minute while logged in; local files only", position = 0)
    default boolean automatic() { return false; }

    @ConfigItem(keyName = "history", name = "Keep timestamped history", description = "Also keep a new file for each export; disabled by default", position = 1)
    default boolean history() { return false; }

    @ConfigItem(keyName = "skills", name = "Include skills", description = "Include all current client skills", position = 2)
    default boolean skills() { return true; }

    @ConfigItem(keyName = "quests", name = "Include quests", description = "Include all quest API entries", position = 3)
    default boolean quests() { return true; }

    @ConfigItem(keyName = "bank", name = "Include bank", description = "Include last-observed bank and its useful items", position = 4)
    default boolean bank() { return true; }

    @ConfigItem(keyName = "inventory", name = "Include inventory", description = "Include last-observed inventory and its useful items", position = 5)
    default boolean inventory() { return true; }

    @ConfigItem(keyName = "equipment", name = "Include equipment", description = "Include last-observed equipment and its useful items", position = 6)
    default boolean equipment() { return true; }

    @ConfigItem(keyName = "useful", name = "Include useful items", description = "Summarize bundled progression item catalog without combining location totals", position = 7)
    default boolean useful() { return true; }
}
