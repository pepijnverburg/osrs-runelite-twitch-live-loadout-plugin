package com.twitchliveloadout.ui;

import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.products.ManualEbsProductEntry;
import com.twitchliveloadout.marketplace.transactions.TwitchTransactionOrigin;
import com.twitchliveloadout.marketplace.transactions.TwitchTransactionProductType;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class EbsProductPanel extends JPanel
{
    private final MarketplacePanel marketplacePanel;
    private final MarketplaceManager marketplaceManager;
    private final JLabel titleLabel = new JLabel();
    private final JLabel detailsLabel = new JLabel();
    private final JButton runButton = new JButton("Run");
    private final JButton deleteButton = new JButton("Delete");
    private ManualEbsProductEntry entity;

    public EbsProductPanel(MarketplacePanel marketplacePanel, MarketplaceManager marketplaceManager)
    {
        super(new BorderLayout(8, 0));
        this.marketplacePanel = marketplacePanel;
        this.marketplaceManager = marketplaceManager;

        setBorder(new EmptyBorder(0, 0, 10, 0));
        setOpaque(false);

        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        wrapper.setBorder(new EmptyBorder(10, 10, 10, 10));
        wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JPanel textPanel = new JPanel(new BorderLayout(0, 4));
        textPanel.setOpaque(false);
        Styles.styleBigLabel(titleLabel, "Invalid custom Random Event");
        Styles.styleLabel(detailsLabel, "");
        textPanel.add(titleLabel, BorderLayout.NORTH);
        textPanel.add(detailsLabel, BorderLayout.CENTER);
        wrapper.add(textPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.setOpaque(false);
        styleButton(runButton);
        styleButton(deleteButton);

        runButton.addActionListener((event) -> runEntry());
        deleteButton.addActionListener((event) -> deleteEntry());

        buttonPanel.add(runButton);
        buttonPanel.add(deleteButton);
        wrapper.add(buttonPanel, BorderLayout.SOUTH);

        add(wrapper, BorderLayout.NORTH);
    }

    public void setEntity(ManualEbsProductEntry entity)
    {
        this.entity = entity;
    }

    public void rebuild()
    {
        if (entity == null || !entity.isValid())
        {
            titleLabel.setText("Invalid custom Random Event");
            detailsLabel.setText("");
            setVisible(false);
            return;
        }

        String triggerSummary = entity.getTriggerSummary();
        String triggerText = triggerSummary.isEmpty() ? "Manual only" : triggerSummary;
        String category = entity.ebsProduct.category != null ? entity.ebsProduct.category : "Uncategorized";
        titleLabel.setText("<html><b color='yellow'>" + entity.ebsProduct.name + "</b></html>");
        detailsLabel.setText("<html><b>" + category + "</b><br/>Trigger: " + triggerText + "</html>");
        setVisible(true);
    }

    private void runEntry()
    {
        if (entity == null || entity.ebsProduct == null)
        {
            return;
        }

        marketplaceManager.testEbsProduct(entity.ebsProduct, TwitchTransactionProductType.MANUAL, TwitchTransactionOrigin.MANUAL);
        marketplacePanel.rebuild();
    }

    private void deleteEntry()
    {
        if (entity == null)
        {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
            marketplacePanel,
            "Delete the saved Random Event '" + entity.ebsProduct.name + "'?",
            "Delete Random Event",
            JOptionPane.OK_CANCEL_OPTION
        );

        if (confirm == JOptionPane.OK_OPTION)
        {
            marketplacePanel.deleteManualEbsProduct(entity);
        }
    }

    private void styleButton(JButton button)
    {
        button.setFocusable(false);
        button.setBackground(ColorScheme.DARK_GRAY_COLOR);
        button.setForeground(Color.WHITE);
    }
}
