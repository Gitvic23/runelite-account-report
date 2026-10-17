package com.accountreport;

import com.accountreport.core.ContainerCollector;
import com.accountreport.core.Snapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Experience;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;

/** Every method is called only on the client thread while LOGGED_IN. */
final class RuneLiteCollector
{
    private final Client client;
    RuneLiteCollector(Client client) { this.client = client; }

    void identity(Snapshot.Builder b, Instant at)
    {
        b.name = client.getLocalPlayer().getName();
        // Varbits.ACCOUNT_TYPE documents values 0..6, including unranked group ironman.
        int type = client.getVarbitValue(Varbits.ACCOUNT_TYPE);
        String[] types = {"Normal", "Ironman", "Ultimate Ironman", "Hardcore Ironman",
            "Group Ironman", "Hardcore Group Ironman", "Unranked Group Ironman"};
        b.accountType = type >= 0 && type < types.length ? types[type] : "Unknown (unsupported account type)";
        b.combatLevel = client.getLocalPlayer().getCombatLevel();
        b.totalLevel = client.getTotalLevel();
        b.identityAt = at;
    }

    void skills(Snapshot.Builder b, Instant at)
    {
        List<Snapshot.SkillRow> rows = new ArrayList<>();
        for (Skill skill : Skill.values())
        {
            int level = client.getRealSkillLevel(skill);
            int xp = client.getSkillExperience(skill);
            // Iterate the current enum (including Sailing), and respect the supported actual-level cap.
            Integer next = level < 1 || xp < 0 || level >= Experience.MAX_REAL_LEVEL ? null
                : Math.max(0, Experience.getXpForLevel(level + 1) - xp);
            rows.add(new Snapshot.SkillRow(skill.getName(), level < 1 ? null : level, xp < 0 ? null : xp, next));
        }
        b.skills = rows;
        b.skillsAt = at;
    }

    void quests(Snapshot.Builder b, Instant at)
    {
        List<Snapshot.QuestRow> rows = new ArrayList<>();
        for (Quest quest : Quest.values())
        {
            String state;
            try
            {
                QuestState result = quest.getState(client); // Runs QUEST_STATUS_GET; client thread only.
                state = result == QuestState.FINISHED ? "Completed" : result == QuestState.IN_PROGRESS
                    ? "In progress" : result == QuestState.NOT_STARTED ? "Not started" : "Unknown";
            }
            catch (RuntimeException e) { state = "Unknown"; }
            rows.add(new Snapshot.QuestRow(quest.getName(), state));
        }
        b.quests = rows;
        b.questsAt = at;
        b.questPoints = client.getVarpValue(VarPlayer.QUEST_POINTS);
    }

    Snapshot.Container container(int containerId, Instant at)
    {
        ItemContainer container = client.getItemContainer(containerId);
        if (container == null) { return null; } // Missing is never a valid empty snapshot.
        List<ContainerCollector.Candidate> rows = new ArrayList<>();
        Item[] contents = container.getItems();
        for (int i = 0; i < contents.length; i++)
        {
            Item item = contents[i];
            if (item == null || item.getId() < 0 || item.getQuantity() <= 0 || item.getId() == ItemID.BANK_FILLER) { continue; }
            ItemComposition definition = client.getItemDefinition(item.getId());
            if (definition == null) { return null; } // Do not persist a partial snapshot.
            String slot = Integer.toString(i);
            if (containerId == InventoryID.WORN)
            {
                for (EquipmentInventorySlot equipmentSlot : EquipmentInventorySlot.values())
                {
                    if (equipmentSlot.getSlotIdx() == i) { slot = equipmentSlot.name(); break; }
                }
            }
            rows.add(new ContainerCollector.Candidate(item.getId(), item.getQuantity(), definition.getName(),
                slot, definition.getPlaceholderTemplateId(), definition.getNote() == -1 ? -1 : definition.getLinkedNoteId()));
        }
        return ContainerCollector.observe(at, rows);
    }
}
