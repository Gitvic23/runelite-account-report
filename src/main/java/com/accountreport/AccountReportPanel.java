package com.accountreport;

import com.accountreport.core.ReportFormatter;
import com.accountreport.core.Snapshot;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.Instant;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import net.runelite.client.ui.PluginPanel;

/** Created and updated on Swing's EDT. JTextArea avoids interpreting character names as HTML. */
final class AccountReportPanel extends PluginPanel
{
    private final JTextArea observations = new JTextArea();
    private final JTextArea feedback = new JTextArea();
    private final JButton export = new JButton("Export TXT report");
    private final JButton copy = new JButton("Copy report to clipboard");

    AccountReportPanel(Runnable onExport, Runnable onCopy, Runnable onOpen)
    {
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.add(new JLabel("Account Report Exporter"), BorderLayout.NORTH);
        configureText(observations); configureText(feedback);
        body.add(observations, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 8));
        JButton open = new JButton("Open report folder");
        export.addActionListener(e -> onExport.run());
        copy.addActionListener(e -> onCopy.run());
        open.addActionListener(e -> onOpen.run());
        buttons.add(export); buttons.add(copy); buttons.add(open);
        body.add(buttons, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER); add(feedback, BorderLayout.SOUTH);
        disconnected();
    }

    private static void configureText(JTextArea text)
    {
        text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setOpaque(false); text.setColumns(18);
    }

    void disconnected()
    {
        observations.setText("Log in to identify your character.\nCached account data is not selected while logged out.");
        export.setEnabled(false); copy.setEnabled(false);
    }

    void update(Snapshot s, ReportFormatter formatter, Instant session)
    {
        observations.setText("Character: " + s.name + "\n\nSkills: " + formatter.freshness(s.skillsAt, session)
            + "\n\nQuests: " + formatter.freshness(s.questsAt, session)
            + "\n\nBank: " + (s.bank.observedAt == null ? "Bank not yet observed" : formatter.freshness(s.bank.observedAt, session))
            + "\nOpen your bank to refresh.\n\nInventory: " + formatter.freshness(s.inventory.observedAt, session)
            + "\n\nEquipment: " + formatter.freshness(s.equipment.observedAt, session));
        export.setEnabled(true); copy.setEnabled(true);
    }

    void busy(boolean busy) { export.setEnabled(!busy); copy.setEnabled(!busy); }
    void feedback(String message) { feedback.setText(message); }
}
