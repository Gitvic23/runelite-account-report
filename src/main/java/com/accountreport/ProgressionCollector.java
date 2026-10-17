package com.accountreport;

import com.accountreport.core.Progression;
import com.accountreport.core.Snapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/** All calls are client-thread-only, gated by the plugin's stable current-character session. */
final class ProgressionCollector
{
    private final Client client;
    private static final Map<Progression.Unlock, Integer> VARBITS = new EnumMap<>(Progression.Unlock.class);
    static
    {
        VARBITS.put(Progression.Unlock.KNIGHT_WAVES, VarbitID.KR_KNIGHTWAVES_STATE);
        VARBITS.put(Progression.Unlock.RIGOUR, VarbitID.PRAYER_RIGOUR_UNLOCKED);
        VARBITS.put(Progression.Unlock.AUGURY, VarbitID.PRAYER_AUGURY_UNLOCKED);
        VARBITS.put(Progression.Unlock.PRESERVE, VarbitID.PRAYER_PRESERVE_UNLOCKED);
        VARBITS.put(Progression.Unlock.DEADEYE, VarbitID.PRAYER_DEADEYE_UNLOCKED);
        VARBITS.put(Progression.Unlock.MYSTIC_VIGOUR, VarbitID.PRAYER_MYSTIC_VIGOUR_UNLOCKED);
    }

    ProgressionCollector(Client client) { this.client = client; }

    void unlocks(Snapshot.Builder b, Instant at)
    {
        List<Snapshot.UnlockRow> rows = new ArrayList<>();
        for (Progression.Unlock kind : Progression.Unlock.values())
        {
            try
            {
                String value = Progression.unlockValue(kind, client.getVarbitValue(VARBITS.get(kind)));
                rows.add(new Snapshot.UnlockRow(kind, value, at));
            }
            catch (RuntimeException e)
            {
                // A failed read retains the last valid observation and its original time.
                rows.add(b.unlocks.stream().filter(r -> r.kind == kind).findFirst()
                    .orElse(new Snapshot.UnlockRow(kind, "Unknown", null)));
            }
        }
        b.unlocks = rows;
    }

    Snapshot.BossCount fromChat(ChatMessage event, Instant at)
    {
        // Never parse public/private/clan/friends-chat text or inspect another player's KC.
        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM) { return null; }
        return Progression.fromGameMessage(event.getMessage(), at);
    }

    Snapshot.BossCount fromPersonalBossPage(Instant at)
    {
        Widget nameRoot = client.getWidget(InterfaceID.CaBoss.BOSS_NAME);
        Widget stats = client.getWidget(InterfaceID.CaBoss.CA_BOSS_STATS);
        if (nameRoot == null || stats == null || nameRoot.isHidden() || stats.isHidden()) { return null; }
        // ca_boss_set_name creates the name as dynamic child 0, not the root widget text.
        Widget[] names = nameRoot.getChildren();
        if (names == null || names.length == 0) { return null; }
        Widget name = names[0];
        Widget[] children = stats.getChildren();
        if (name == null || children == null || name.isHidden()) { return null; }
        Snapshot.BossCount found = null;
        for (Widget row : children)
        {
            if (row == null || row.isHidden()) { continue; }
            Snapshot.BossCount count = Progression.fromBossPage(name.getText(), row.getText(), at);
            if (count == null) { continue; }
            if (found != null) { return null; } // Ambiguous/unexpected layout: do not guess.
            found = count;
        }
        return found;
    }
}
