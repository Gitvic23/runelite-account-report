package com.accountreport;

import com.accountreport.core.Fixtures;
import com.accountreport.core.Progression;
import com.accountreport.core.Snapshot;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.BiFunction;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProgressionCollectorTest
{
    @Test public void readsNamedUnlockFlagsAndDoesNotMistakeActivePrayersForUnlocks()
    {
        Client client = fake(Client.class, (method, args) -> {
            assertEquals("getVarbitValue", method);
            int id = (Integer) args[0];
            if (id == VarbitID.KR_KNIGHTWAVES_STATE) { return 8; }
            if (id == VarbitID.PRAYER_RIGOUR_UNLOCKED) { return 1; }
            if (List.of(VarbitID.PRAYER_AUGURY_UNLOCKED, VarbitID.PRAYER_PRESERVE_UNLOCKED,
                VarbitID.PRAYER_DEADEYE_UNLOCKED, VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED).contains(id)) { return 0; }
            throw new AssertionError("Unverified flag " + id);
        });
        Snapshot.Builder b = Fixtures.character(); new ProgressionCollector(client).unlocks(b, Fixtures.TIME);
        assertEquals("Completed", b.unlocks.get(0).value);
        assertEquals("Unlocked", b.unlocks.get(1).value);
        assertEquals("Not unlocked", b.unlocks.get(2).value);
        assertEquals(Fixtures.TIME, b.unlocks.get(0).observedAt);
    }

    @Test public void failedFlagReadRetainsItsOwnOldTimestampOrUnknown()
    {
        Client client = fake(Client.class, (method, args) -> { throw new IllegalStateException("Unavailable"); });
        Snapshot.Builder b = Fixtures.character();
        b.unlocks = List.of(new Snapshot.UnlockRow(Progression.Unlock.KNIGHT_WAVES, "Completed", Fixtures.TIME));
        new ProgressionCollector(client).unlocks(b, Fixtures.TIME.plusSeconds(60));
        assertEquals("Completed", b.unlocks.get(0).value); assertEquals(Fixtures.TIME, b.unlocks.get(0).observedAt);
        assertEquals("Unknown", b.unlocks.get(1).value); assertNull(b.unlocks.get(1).observedAt);
    }

    @Test public void neverCapturesAnotherPlayersChatCount()
    {
        ProgressionCollector collector = new ProgressionCollector(null);
        String text = "Your Zulrah kill count is: <col=ff0000>42</col>.";
        for (ChatMessageType type : List.of(ChatMessageType.PUBLICCHAT, ChatMessageType.PRIVATECHAT,
            ChatMessageType.FRIENDSCHATNOTIFICATION, ChatMessageType.CLAN_CHAT, ChatMessageType.TRADE))
        { assertNull(collector.fromChat(new ChatMessage(null, type, "Other player", text, null, 0), Fixtures.TIME)); }
        Snapshot.BossCount count = collector.fromChat(new ChatMessage(null, ChatMessageType.GAMEMESSAGE, "", text, null, 0), Fixtures.TIME);
        assertEquals(42, count.count);
    }

    @Test public void readsPersonalBossNameChildAndCountWithoutMixingPersonalBest()
    {
        Widget name = widget("Zulrah", null, false);
        Widget nameRoot = widget("Wrong root text", new Widget[] {name}, false);
        Widget stats = widget("", new Widget[] {widget("Personal Best: 0:42", null, false),
            widget("Kill Count: <col=ffffff>123</col>", null, false), widget("Combat Level: 725", null, false)}, false);
        Snapshot.BossCount count = new ProgressionCollector(pageClient(nameRoot, stats)).fromPersonalBossPage(Fixtures.TIME);
        assertNotNull(count); assertEquals("Zulrah", count.name); assertEquals(123, count.count);
        assertEquals(Progression.BossSource.COMBAT_ACHIEVEMENTS, count.source);
    }

    @Test public void explicitZeroPageIsValidButHiddenMissingAndMalformedPagesAreUnknown()
    {
        Widget nameRoot = widget("", new Widget[] {widget("Zulrah", null, false)}, false);
        Widget zeroStats = widget("", new Widget[] {widget("Kill Count: 0", null, false)}, false);
        assertEquals(0, new ProgressionCollector(pageClient(nameRoot, zeroStats)).fromPersonalBossPage(Fixtures.TIME).count);
        assertNull(new ProgressionCollector(pageClient(null, zeroStats)).fromPersonalBossPage(Fixtures.TIME));
        assertNull(new ProgressionCollector(pageClient(widget("", new Widget[0], false), zeroStats)).fromPersonalBossPage(Fixtures.TIME));
        assertNull(new ProgressionCollector(pageClient(widget("", null, true), zeroStats)).fromPersonalBossPage(Fixtures.TIME));
        Widget malformed = widget("", new Widget[] {widget("Kill Count: N/A", null, false)}, false);
        assertNull(new ProgressionCollector(pageClient(nameRoot, malformed)).fromPersonalBossPage(Fixtures.TIME));
        Widget ambiguous = widget("", new Widget[] {widget("Kill Count: 1", null, false), widget("Kill Count: 2", null, false)}, false);
        assertNull(new ProgressionCollector(pageClient(nameRoot, ambiguous)).fromPersonalBossPage(Fixtures.TIME));
    }

    private static Client pageClient(Widget name, Widget stats)
    {
        return fake(Client.class, (method, args) -> {
            assertEquals("getWidget", method);
            int id = (Integer) args[0];
            if (id == InterfaceID.CaBoss.BOSS_NAME) { return name; }
            if (id == InterfaceID.CaBoss.CA_BOSS_STATS) { return stats; }
            throw new AssertionError("Unexpected interface read, including another player's POH log: " + id);
        });
    }

    private static Widget widget(String text, Widget[] children, boolean hidden)
    {
        return fake(Widget.class, (method, args) -> {
            switch (method)
            {
                case "getText": return text;
                case "getChildren": return children;
                case "getChild": return children == null ? null : children[(Integer) args[0]];
                case "isHidden": return hidden;
                default: throw new AssertionError(method);
            }
        });
    }
    // Interface fakes only; production code contains no reflection.
    private static <T> T fake(Class<T> type, BiFunction<String, Object[], Object> behavior)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (proxy, method, args) -> behavior.apply(method.getName(), args)));
    }
}
