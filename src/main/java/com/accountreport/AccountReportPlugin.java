package com.accountreport;

import com.accountreport.core.Identity;
import com.accountreport.core.ReportFormatter;
import com.accountreport.core.ReportOptions;
import com.accountreport.core.ReportWriter;
import com.accountreport.core.SessionGate;
import com.accountreport.core.Snapshot;
import com.accountreport.core.SnapshotStore;
import com.accountreport.core.UsefulCatalog;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.LinkBrowser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(name = "Account Report Exporter", internalName = "account-report-exporter",
    description = "Export local TXT account reports from labeled observations", tags = {"account", "export", "ironman"})
public final class AccountReportPlugin extends Plugin
{
    private static final Logger log = LoggerFactory.getLogger(AccountReportPlugin.class);
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar toolbar;
    @Inject private AccountReportConfig config;

    private volatile boolean enabled;
    private volatile ExecutorService worker;
    private volatile String reportDirectory;
    private volatile long visibleToken = -1;
    private volatile AccountReportPanel panel;
    private NavigationButton navigation; // EDT only
    private final AtomicBoolean exporting = new AtomicBoolean();

    // Below: client-thread-owned. Worker tasks capture immutable values and store references.
    private final SessionGate gate = new SessionGate();
    private volatile Snapshot snapshot; // Immutable; shutdown may safely capture the last published value.
    private volatile SnapshotStore store;
    private ReportWriter writer;
    private ReportFormatter formatter;
    private RuneLiteCollector collector;
    private Instant sessionStart;
    private Instant lastPoll = Instant.EPOCH;
    private Instant lastQuestPoll = Instant.EPOCH;
    private Instant lastSave = Instant.EPOCH;
    private Instant lastAuto = Instant.EPOCH;
    private boolean bankReceived;
    private boolean inventoryDirty;
    private boolean equipmentDirty;
    private boolean unsaved;
    private int loggedInTicks;

    @Provides
    AccountReportConfig provideConfig(ConfigManager manager) { return manager.getConfig(AccountReportConfig.class); }

    @Override
    protected void startUp()
    {
        enabled = true;
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "account-report-io"); t.setDaemon(true); return t;
        });
        worker = executor;
        exporting.set(false);
        SwingUtilities.invokeLater(() -> {
            if (!active(executor)) { return; }
            panel = new AccountReportPanel(() -> requestExport(false, false), () -> requestExport(true, false), this::openFolder);
            navigation = NavigationButton.builder().tooltip("Account Report Exporter").icon(icon()).priority(8).panel(panel).build();
            toolbar.addNavigation(navigation);
        });
        clientThread.invokeLater(() -> {
            if (!active(executor)) { return; }
            gate.clear(); snapshot = null; store = null; writer = null; formatter = null;
            collector = new RuneLiteCollector(client);
            resetObservations();
        });
        executor.execute(() -> {
            try
            {
                Filepath root = getPluginDirectory(); // May touch disk; worker only.
                RuneLiteFiles cache = new RuneLiteFiles(root.joinSegment("cache"));
                Filepath reports = root.joinSegment("reports");
                RuneLiteFiles reportFiles = new RuneLiteFiles(reports);
                SnapshotStore readyStore = new SnapshotStore(cache);
                ReportWriter readyWriter = new ReportWriter(reportFiles);
                ReportFormatter readyFormatter = new ReportFormatter(UsefulCatalog.load(), ZoneId.systemDefault());
                reportDirectory = reports.toString();
                clientThread.invokeLater(() -> {
                    if (!active(executor)) { return; }
                    store = readyStore; writer = readyWriter; formatter = readyFormatter;
                    feedback("Ready. Log in and open your bank to refresh its snapshot.");
                });
            }
            catch (IOException | RuntimeException e)
            { log.debug("Account report initialization failed", e); feedback("Setup failed: " + e.getClass().getSimpleName() + ". Check RuneLite logs."); }
        });
    }

    @Override
    protected void shutDown()
    {
        enabled = false;
        visibleToken = -1;
        ExecutorService executor = worker;
        Snapshot finalSnapshot = snapshot;
        SnapshotStore finalStore = store;
        // Queue the last immutable observation immediately, even if the game thread is stopping.
        if (finalSnapshot != null && finalStore != null)
        {
            try
            {
                executor.execute(() -> {
                    try { finalStore.save(finalSnapshot); }
                    catch (IOException | RuntimeException e) { log.debug("Final account snapshot write failed", e); }
                });
            }
            catch (RejectedExecutionException e) { log.debug("Account report worker already stopped", e); }
        }
        executor.shutdown(); // Drain queued writes; never block client/UI threads.
        clientThread.invokeLater(() -> {
            if (worker == executor)
            {
                gate.clear(); snapshot = null;
            }
        });
        SwingUtilities.invokeLater(() -> {
            if (navigation != null) { toolbar.removeNavigation(navigation); navigation = null; }
            panel = null;
        });
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (!enabled) { return; }
        if (event.getGameState() != GameState.LOGGED_IN && event.getGameState() != GameState.LOADING)
        {
            persist(worker);
            gate.clear(); snapshot = null; visibleToken = -1;
            resetObservations();
            ui(p -> p.disconnected());
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!enabled || client.getGameState() != GameState.LOGGED_IN || !identify()) { return; }
        // An event establishes that data was received in this login, not left over from a prior account.
        if (event.getItemContainer() == null) { return; }
        if (event.getContainerId() == InventoryID.BANK) { bankReceived = true; }
        if (event.getContainerId() == InventoryID.INV) { inventoryDirty = true; }
        if (event.getContainerId() == InventoryID.WORN) { equipmentDirty = true; }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!enabled || store == null || client.getGameState() != GameState.LOGGED_IN || !identify()) { return; }
        loggedInTicks++;
        if (snapshot == null || loggedInTicks < 2) { return; }
        try
        {
            capture(false);
            Instant now = Instant.now();
            if (unsaved && now.isAfter(lastSave.plusSeconds(5))) { persist(worker); }
            if (config.automatic() && now.isAfter(lastAuto.plusSeconds(60))) { requestExport(false, true); }
        }
        catch (RuntimeException e)
        { log.debug("Account report observation failed", e); feedback("Observation failed; last valid snapshots retained. Check RuneLite logs."); }
    }

    private boolean identify()
    {
        if (store == null || client.getLocalPlayer() == null || client.getLocalPlayer().getName() == null
            || client.getAccountHash() == -1) { return false; }
        String profile = RuneScapeProfileType.getCurrent(client).name();
        String key = Identity.key(client.getAccountHash(), profile);
        if (key.equals(gate.key())) { return true; }
        persist(worker);
        long token = gate.select(key);
        snapshot = null; visibleToken = -1;
        resetObservations();
        sessionStart = Instant.now(); lastAuto = sessionStart;
        ExecutorService executor = worker;
        SnapshotStore currentStore = store;
        ui(p -> { p.disconnected(); p.feedback("Loading this character's local snapshots…"); });
        executor.execute(() -> {
            SnapshotStore.LoadResult loaded = currentStore.load(key);
            clientThread.invokeLater(() -> {
                if (!active(executor) || !gate.accepts(token, key) || !sameCharacter(key)) { return; }
                Snapshot.Builder b = loaded.snapshot == null ? new Snapshot.Builder(key) : loaded.snapshot.toBuilder();
                b.profile = profile;
                collector.identity(b, Instant.now()); // The fresh name replaces a pre-rename cached display name.
                snapshot = b.build();
                visibleToken = token;
                inventoryDirty = true; equipmentDirty = true;
                feedback(loaded.warning == null ? "Character identified. Open your bank to refresh." : loaded.warning);
                updatePanel();
            });
        });
        return true;
    }

    private boolean sameCharacter(String key)
    {
        return client.getGameState() == GameState.LOGGED_IN && client.getAccountHash() != -1
            && client.getLocalPlayer() != null && key.equals(Identity.key(client.getAccountHash(), RuneScapeProfileType.getCurrent(client).name()));
    }

    private void capture(boolean force)
    {
        Instant now = Instant.now();
        boolean poll = force || now.isAfter(lastPoll.plusSeconds(15));
        boolean questPoll = force || now.isAfter(lastQuestPoll.plusSeconds(60));
        Widget bank = client.getWidget(InterfaceID.Bankmain.ITEMS);
        boolean bankReady = bankReceived && bank != null && !bank.isHidden();
        if (!poll && !questPoll && !inventoryDirty && !equipmentDirty && !bankReady) { return; }
        Snapshot.Builder b = snapshot.toBuilder();
        collector.identity(b, now);
        if (poll) { collector.skills(b, now); lastPoll = now; }
        if (questPoll) { collector.quests(b, now); lastQuestPoll = now; }
        if (poll || inventoryDirty)
        {
            Snapshot.Container c = collector.container(InventoryID.INV, now);
            if (c != null) { b.inventory = c; }
            inventoryDirty = false;
        }
        if (poll || equipmentDirty)
        {
            Snapshot.Container c = collector.container(InventoryID.WORN, now);
            if (c != null) { b.equipment = c; }
            equipmentDirty = false;
        }
        if (bankReady)
        {
            // Widget is only a readiness gate. Never read widget items: searches/tabs hide those.
            Snapshot.Container c = collector.container(InventoryID.BANK, now);
            if (c != null) { b.bank = c; bankReceived = false; }
        }
        snapshot = b.build(); unsaved = true;
        updatePanel();
    }

    private void persist(ExecutorService executor)
    {
        if (snapshot == null || store == null || executor == null || !unsaved) { return; }
        Snapshot saved = snapshot;
        SnapshotStore target = store;
        unsaved = false; lastSave = Instant.now();
        try
        {
            executor.execute(() -> {
                try { target.save(saved); }
                catch (IOException | RuntimeException e)
                {
                    log.debug("Account report cache write failed", e);
                    feedback("Could not save the snapshot cache. Check disk space, permissions and RuneLite logs.");
                    clientThread.invokeLater(() -> {
                        if (worker == executor && snapshot != null && snapshot.key.equals(saved.key)) { unsaved = true; }
                    });
                }
            });
        }
        catch (RejectedExecutionException e) { unsaved = true; }
    }

    private void requestExport(boolean clipboard, boolean automatic)
    {
        ExecutorService executor = worker;
        if (!active(executor)) { return; }
        if (!exporting.compareAndSet(false, true))
        { if (!automatic) { feedback("An export is already in progress."); } return; }
        clientThread.invokeLater(() -> {
            if (!active(executor) || snapshot == null || loggedInTicks < 2 || !sameCharacter(snapshot.key))
            { exporting.set(false); feedback("Log in and wait for character observations before exporting."); return; }
            try { capture(true); }
            catch (RuntimeException e)
            { exporting.set(false); log.debug("Account report refresh failed", e); feedback("Refresh failed; no report exported. Check RuneLite logs."); return; }
            persist(executor);
            Snapshot exported = snapshot;
            ReportFormatter currentFormatter = formatter;
            ReportWriter currentWriter = writer;
            ReportOptions options = new ReportOptions(config.skills(), config.quests(), config.bank(), config.inventory(), config.equipment(), config.useful());
            boolean history = config.history();
            Instant generated = Instant.now();
            Instant session = sessionStart;
            long token = gate.token();
            if (automatic) { lastAuto = generated; }
            ui(p -> p.busy(true));
            try
            {
                executor.execute(() -> {
                    try
                    {
                        if (!active(executor)) { return; }
                        String text = currentFormatter.format(exported, options, generated, session);
                        if (clipboard)
                        {
                            SwingUtilities.invokeLater(() -> {
                                if (!active(executor) || visibleToken != token) { return; }
                                try
                                {
                                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                                    feedback("Report copied to clipboard.");
                                }
                                catch (RuntimeException e) { feedback("Clipboard unavailable. Use Export TXT report instead."); }
                            });
                        }
                        else
                        {
                            String name = currentWriter.write(exported, text, history, generated);
                            if (visibleToken == token) { feedback("Saved UTF-8 report: " + name + "\nUse Open report folder to find it."); }
                        }
                    }
                    catch (IOException | RuntimeException e)
                    { log.debug("Account report export failed", e); feedback("Export failed: " + e.getClass().getSimpleName() + ". Check disk permissions and RuneLite logs."); }
                    finally
                    {
                        exporting.set(false);
                        ui(p -> { if (visibleToken == token) { p.busy(false); } });
                    }
                });
            }
            catch (RejectedExecutionException e) { exporting.set(false); feedback("Plugin is stopping; export cancelled."); }
        });
    }

    private void openFolder()
    {
        String path = reportDirectory;
        if (path == null) { feedback("Report folder is not ready. Check RuneLite logs."); return; }
        LinkBrowser.open(path); // Supported RuneLite OS-folder helper, no network request.
        feedback("Opening report folder. If unavailable, RuneLite offers to copy its path.");
    }

    private void updatePanel()
    {
        Snapshot current = snapshot;
        ReportFormatter f = formatter;
        Instant session = sessionStart;
        long token = gate.token();
        ui(p -> {
            if (current != null && token == visibleToken)
            { p.update(current, f, session); if (exporting.get()) { p.busy(true); } }
        });
    }

    private void resetObservations()
    {
        bankReceived = false; inventoryDirty = false; equipmentDirty = false; unsaved = false;
        loggedInTicks = 0; lastPoll = Instant.EPOCH; lastQuestPoll = Instant.EPOCH; lastSave = Instant.EPOCH;
    }
    private boolean active(ExecutorService executor) { return enabled && worker == executor; }
    private void feedback(String message) { ui(p -> p.feedback(message)); }
    private void ui(java.util.function.Consumer<AccountReportPanel> action)
    {
        ExecutorService executor = worker;
        SwingUtilities.invokeLater(() -> { if (active(executor) && panel != null) { action.accept(panel); } });
    }
    private static BufferedImage icon()
    {
        BufferedImage icon = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setColor(new Color(226, 206, 155)); g.fillRect(5, 2, 14, 20);
        g.setColor(new Color(65, 57, 45));
        for (int y = 7; y <= 17; y += 5) { g.fillRect(8, y, 8, 2); }
        g.dispose(); return icon;
    }
}
