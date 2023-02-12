package com.twitchliveloadout.ui;

import com.twitchliveloadout.fights.FightStateManager;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.MarketplaceProductSorter;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.PluginErrorPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class MarketplacePanel extends JPanel
{
	private static final String TRANSACTION_LIST_PANEL = "TRANSACTION_LIST_PANEL";
	private static final String ERROR_PANEL = "ERROR_PANEL";

	private final GridBagConstraints constraints = new GridBagConstraints();
	private final GridBagConstraints transactionListConstraints = new GridBagConstraints();
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel wrapper = new JPanel(cardLayout);

	private final JPanel transactionListPanel = new JPanel(new GridBagLayout());
	private final JPanel transactionListWrapper = new JPanel(new BorderLayout());
	private final CopyOnWriteArrayList<MarketplaceProductPanel> marketplaceProductPanels = new CopyOnWriteArrayList();
	private final JPanel pauseAllPanel = new JPanel(new BorderLayout());
	private final JLabel pauseAllLabel = new JLabel();

	private final PluginErrorPanel errorPanel = new PluginErrorPanel();
	private final JPanel errorWrapper = new JPanel(new BorderLayout());

	private final MarketplaceManager marketplaceManager;

	public MarketplacePanel(MarketplaceManager marketplaceManager)
	{
		super(new GridBagLayout());

		this.marketplaceManager = marketplaceManager;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		transactionListConstraints.fill = GridBagConstraints.HORIZONTAL;
		transactionListConstraints.weightx = 1;
		transactionListConstraints.gridx = 0;
		transactionListConstraints.gridy = 0;

		transactionListPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
		transactionListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		pauseAllPanel.setLayout(new BorderLayout());
		pauseAllPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		pauseAllPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		pauseAllPanel.add(pauseAllLabel, BorderLayout.CENTER);
		Styles.styleBigLabel(pauseAllLabel, "Pause all random events");
		pauseAllPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				int confirm = JOptionPane.showConfirmDialog(MarketplacePanel.this,
						"Are you sure you want to pause ALL random events?",
						"Warning", JOptionPane.OK_CANCEL_OPTION);

				if (confirm == 0)
				{
					marketplaceManager.pauseActiveProducts(0);
				}
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				pauseAllPanel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				pauseAllPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				pauseAllPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				pauseAllPanel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			}
		});

		transactionListWrapper.setLayout(new GridBagLayout());
		transactionListWrapper.setBorder(new EmptyBorder(10, 0, 10, 0));
		transactionListWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		transactionListWrapper.add(pauseAllPanel, constraints);
		constraints.gridy++;
		transactionListWrapper.add(transactionListPanel, constraints);
		constraints.gridy++;

		errorPanel.setBorder(new EmptyBorder(50, 20, 20, 20));
		errorPanel.setContent("No active random events", "Let your viewers activate them");

		errorWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		errorWrapper.add(errorPanel, BorderLayout.NORTH);

		wrapper.add(transactionListWrapper, TRANSACTION_LIST_PANEL);
		wrapper.add(errorWrapper, ERROR_PANEL);
		add(wrapper, BorderLayout.NORTH);

		// initialize all the fight panel slots without adding them to the UI
		for (int i = 0; i < FightStateManager.MAX_FIGHT_AMOUNT_IN_MEMORY; i++)
		{
			MarketplaceProductPanel marketplaceProductPanel = new MarketplaceProductPanel(marketplaceManager);
			marketplaceProductPanels.add(marketplaceProductPanel);
		}
	}

	public void rebuild()
	{
		rebuildTransactionList();
		repaint();
		revalidate();
	}

	private void rebuildTransactionList()
	{
		final CopyOnWriteArrayList<MarketplaceProduct> activeProducts = new CopyOnWriteArrayList();
		int marketplaceProductPanelIndex = 0;

		// add all active products by default
		activeProducts.addAll(marketplaceManager.getActiveProducts());

		// order by started at
		Collections.sort(activeProducts, new MarketplaceProductSorter());

		// guard: check if there are no fights to show
		if (activeProducts.size() <= 0)
		{
			cardLayout.show(wrapper, ERROR_PANEL);
			return;
		}

		cardLayout.show(wrapper, TRANSACTION_LIST_PANEL);
		transactionListPanel.removeAll();
		transactionListConstraints.gridy = 0;

		// first clear all the fight in the panels
		for (MarketplaceProductPanel marketplaceProductPanel : marketplaceProductPanels)
		{
			marketplaceProductPanel.setMarketplaceProduct(null);
		}

		// directly add all the fights again in the new order
		for (MarketplaceProduct marketplaceProduct : activeProducts)
		{
			MarketplaceProductPanel marketplaceProductPanel = marketplaceProductPanels.get(marketplaceProductPanelIndex);

			// guard: check if the panel is valid
			if (marketplaceProductPanel == null)
			{
				log.warn("An invalid transaction panel index was requested: "+ marketplaceProductPanelIndex);
				break;
			}

			marketplaceProductPanel.setMarketplaceProduct(marketplaceProduct);
			marketplaceProductPanel.rebuild();
			marketplaceProductPanelIndex ++;

			transactionListPanel.add(marketplaceProductPanel, transactionListConstraints);
			transactionListConstraints.gridy++;
		}
	}
}
