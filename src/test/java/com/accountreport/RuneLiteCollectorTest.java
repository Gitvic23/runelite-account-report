package com.accountreport;

import com.accountreport.core.Fixtures;
import com.accountreport.core.Snapshot;
import java.lang.reflect.Proxy;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;
import static org.junit.Assert.*;

/** API-only fakes; no client is started and no game input is generated. */
public class RuneLiteCollectorTest
{
    @Test public void fullBankContainerIgnoresVisibleWidgetsAndKeepsRawVariants()
    {
        Item[] contents = { new Item(12791, 1), new Item(27281, 1), new Item(9999, 1), new Item(ItemID.BANK_FILLER, 1) };
        ItemContainer bank = fake(ItemContainer.class, (method, args) -> {
            if (method.equals("getItems")) { return contents; }
            throw new AssertionError(method);
        });
        Client client = fake(Client.class, (method, args) -> {
            if (method.equals("getItemContainer")) { assertEquals(InventoryID.BANK, args[0]); return bank; }
            if (method.equals("getItemDefinition"))
            {
                int id = (Integer) args[0];
                return definition(id == 9999 ? 14401 : -1, -1, -1, id == 27281 ? "Divine rune pouch" : "Rune pouch");
            }
            throw new AssertionError("Unexpected client access (including widget filtering): " + method);
        });
        Snapshot.Container result = new RuneLiteCollector(client).container(InventoryID.BANK, Fixtures.TIME);
        assertEquals(2, result.items.size());
        assertEquals(12791, result.items.get(0).id); assertEquals(27281, result.items.get(1).id);
    }

    @Test public void unavailableContainerAndUnavailableDefinitionNeverBecomeEmptyOrPartial()
    {
        Client absent = fake(Client.class, (method, args) -> null);
        assertNull(new RuneLiteCollector(absent).container(InventoryID.BANK, Fixtures.TIME));
        ItemContainer container = fake(ItemContainer.class, (method, args) -> new Item[] {new Item(9185, 1)});
        Client missingDefinition = fake(Client.class, (method, args) -> method.equals("getItemContainer") ? container : null);
        assertNull(new RuneLiteCollector(missingDefinition).container(InventoryID.BANK, Fixtures.TIME));
    }

    @Test public void equipmentSlotsAndNotedIdsAreExplicit()
    {
        Item[] contents = new Item[14]; contents[3] = new Item(99999, 2);
        ItemContainer container = fake(ItemContainer.class, (method, args) -> contents);
        Client client = fake(Client.class, (method, args) -> method.equals("getItemContainer") ? container
            : definition(-1, 799, 9185, "Rune crossbow"));
        Snapshot.ItemRow result = new RuneLiteCollector(client).container(InventoryID.WORN, Fixtures.TIME).items.get(0);
        assertEquals("WEAPON", result.slot); assertEquals(99999, result.id); assertEquals(9185, result.notedOf);
    }

    @Test public void skillsCoverTheCurrentEnumAndMaximumLevels()
    {
        Client client = fake(Client.class, (method, args) -> {
            if (method.equals("getRealSkillLevel")) { return args[0] == Skill.ATTACK ? 99 : 1; }
            if (method.equals("getSkillExperience")) { return args[0] == Skill.ATTACK ? 13_034_431 : 0; }
            throw new AssertionError(method);
        });
        Snapshot.Builder b = Fixtures.character(); new RuneLiteCollector(client).skills(b, Fixtures.TIME);
        assertEquals(Skill.values().length, b.skills.size());
        assertTrue(b.skills.stream().anyMatch(s -> s.name.equals("Sailing")));
        assertNull(b.skills.stream().filter(s -> s.name.equals("Attack")).findFirst().get().nextXp);
        assertEquals(Integer.valueOf(83), b.skills.stream().filter(s -> s.name.equals("Magic")).findFirst().get().nextXp);
    }

    @Test public void questStatesUseTheSupportedScriptApiAndFailuresStayUnknown()
    {
        int[] currentId = {-1};
        Quest[] quests = Quest.values();
        Client client = fake(Client.class, (method, args) -> {
            if (method.equals("runScript"))
            {
                Object[] scriptArgs = (Object[]) args[0];
                assertEquals(ScriptID.QUEST_STATUS_GET, scriptArgs[0]);
                currentId[0] = (Integer) scriptArgs[1];
                if (currentId[0] == quests[1].getId()) { throw new IllegalStateException("Unavailable quest"); }
                return null;
            }
            if (method.equals("getIntStack"))
            { return new int[] {currentId[0] == quests[0].getId() ? 2 : 1}; }
            if (method.equals("getVarpValue")) { return 150; }
            throw new AssertionError(method);
        });
        Snapshot.Builder b = Fixtures.character(); new RuneLiteCollector(client).quests(b, Fixtures.TIME);
        assertEquals(quests.length, b.quests.size());
        assertEquals("Completed", b.quests.get(0).state);
        assertEquals("Unknown", b.quests.get(1).state);
        assertEquals("Not started", b.quests.get(2).state);
        assertEquals(Integer.valueOf(150), b.questPoints);
        assertEquals(Fixtures.TIME, b.questsAt);
    }

    @Test public void uninitializedSkillIsNeverReportedAsLevelZero()
    {
        Client client = fake(Client.class, (method, args) -> method.equals("getRealSkillLevel") ? 0 : -1);
        Snapshot.Builder b = Fixtures.character(); new RuneLiteCollector(client).skills(b, Fixtures.TIME);
        assertNull(b.skills.get(0).level); assertNull(b.skills.get(0).xp); assertNull(b.skills.get(0).nextXp);
    }

    private static ItemComposition definition(int placeholder, int note, int unnoted, String name)
    {
        return fake(ItemComposition.class, (method, args) -> {
            switch (method)
            {
                case "getName": return name;
                case "getPlaceholderTemplateId": return placeholder;
                case "getNote": return note;
                case "getLinkedNoteId": return unnoted;
                default: throw new AssertionError(method);
            }
        });
    }

    // Reflection is confined to test fakes; no reflection exists in the distributed plugin.
    private static <T> T fake(Class<T> type, BiFunction<String, Object[], Object> behavior)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> behavior.apply(method.getName(), args)));
    }
}
