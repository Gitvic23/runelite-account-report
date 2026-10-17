package com.accountreport.core;

public final class ReportOptions
{
    public final boolean skills, quests, bank, inventory, equipment, useful;
    public ReportOptions(boolean skills, boolean quests, boolean bank, boolean inventory, boolean equipment, boolean useful)
    {
        this.skills = skills; this.quests = quests; this.bank = bank;
        this.inventory = inventory; this.equipment = equipment; this.useful = useful;
    }
    public static ReportOptions all() { return new ReportOptions(true, true, true, true, true, true); }
}
