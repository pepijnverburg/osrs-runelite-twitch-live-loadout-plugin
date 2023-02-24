package com.twitchliveloadout.ui;

import com.twitchliveloadout.marketplace.MarketplaceConstants;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.MarketplaceProductSorter;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.StreamerProduct;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
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
	private static final String NO_TRANSACTIONS_PANEL = "NO_TRANSACTIONS_PANEL";

	private final GridBagConstraints constraints = new GridBagConstraints();
	private final GridBagConstraints transactionListConstraints = new GridBagConstraints();
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel wrapper = new JPanel(cardLayout);

	private final GridBagConstraints playbackConstraints = new GridBagConstraints();
	private final TextPanel availableDonationsPanel = new TextPanel("Configured Donations:", "<html>No donations are configured.</html>");
	private final TextPanel queuedTransactionsPanel = new TextPanel("Queued Transactions:", "<html>No transactions are queued.</html>");
	private final JPanel playbackWrapper = new JPanel(new BorderLayout());
	private final TextPanel playbackControlsPanel = new TextPanel("Playback Controls:", "<html>Pause and start the random events for a specific period to temporarily block distractions.</html>");
	private final JPanel startPanel = new JPanel(new BorderLayout());
	private final JLabel startLabel = new JLabel();
	private final JPanel pauseShortPanel = new JPanel(new BorderLayout());
	private final JLabel pauseShortLabel = new JLabel();
	private final JPanel pauseLongPanel = new JPanel(new BorderLayout());
	private final JLabel pauseLongLabel = new JLabel();

	private final JPanel transactionListPanel = new JPanel(new GridBagLayout());
	private final TextPanel transactionTitlePanel = new TextPanel("Recent donations:", "<html>The list below shows recent donations with the newest at the top.</html>");
	private final JPanel transactionListWrapper = new JPanel(new BorderLayout());
	private final CopyOnWriteArrayList<MarketplaceProductPanel> marketplaceProductPanels = new CopyOnWriteArrayList();

	private final PluginErrorPanel noTransactionsPanel = new PluginErrorPanel();
	private final JPanel noTransactionsWrapper = new JPanel(new BorderLayout());

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

		playbackConstraints.fill = GridBagConstraints.HORIZONTAL;
		playbackConstraints.weightx = 1;
		playbackConstraints.gridx = 0;
		playbackConstraints.gridy = 0;

		transactionListConstraints.fill = GridBagConstraints.HORIZONTAL;
		transactionListConstraints.weightx = 1;
		transactionListConstraints.gridx = 0;
		transactionListConstraints.gridy = 0;

		transactionListPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
		transactionListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		initializePanelButton(startPanel, startLabel, "<html>Start</html>", () -> {
			marketplaceManager.startActiveProducts();
		});

		initializePanelButton(pauseShortPanel, pauseShortLabel, "<html>Pause for 10 minutes</html>", () -> {
			marketplaceManager.pauseActiveProducts(10 * 60 * 1000);
		});

		initializePanelButton(pauseLongPanel, pauseLongLabel, "<html>Pause for 30 minutes</html>", () -> {
			marketplaceManager.pauseActiveProducts(30 * 60 * 1000);
		});

		playbackWrapper.setLayout(new GridBagLayout());
		playbackWrapper.setBorder(new EmptyBorder(10, 0, 10, 0));
		playbackWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		playbackWrapper.add(availableDonationsPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(queuedTransactionsPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(playbackControlsPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(startPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(pauseShortPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(pauseLongPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(transactionTitlePanel, playbackConstraints);
		constraints.gridy++;

		transactionListWrapper.setLayout(new GridBagLayout());
		transactionListWrapper.setBorder(new EmptyBorder(10, 0, 10, 0));
		transactionListWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		transactionListWrapper.add(playbackWrapper, constraints);
		constraints.gridy++;

		transactionListWrapper.add(transactionListPanel, constraints);
		constraints.gridy++;

		noTransactionsPanel.setBorder(new EmptyBorder(50, 20, 20, 20));
		noTransactionsPanel.setContent("No recent donations", "<html>Let your viewers activate random events through donations. Make sure you have set them up via the Twitch Extension configuration page.</html>");

		noTransactionsWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		noTransactionsWrapper.add(playbackWrapper, BorderLayout.NORTH);
		noTransactionsWrapper.add(noTransactionsPanel, BorderLayout.CENTER);

		wrapper.add(transactionListWrapper, TRANSACTION_LIST_PANEL);
		wrapper.add(noTransactionsWrapper, NO_TRANSACTIONS_PANEL);
		add(wrapper, BorderLayout.NORTH);

		// initialize all the fight panel slots without adding them to the UI
		for (int i = 0; i < MarketplaceConstants.MAX_MARKETPLACE_PRODUCT_AMOUNT_IN_MEMORY; i++)
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
		final CopyOnWriteArrayList<MarketplaceProduct> activeProducts = marketplaceManager.getActiveProducts();
		final CopyOnWriteArrayList<StreamerProduct> streamerProducts = marketplaceManager.getStreamerProducts();
		final CopyOnWriteArrayList<TwitchTransaction> queuedTransactions = marketplaceManager.getQueuedTransactions();
		final int streamerProductAmount = streamerProducts.size();
		final int queuedTransactionAmount = queuedTransactions.size();
		int marketplaceProductPanelIndex = 0;
		String availableDonationsText = "<html>You have <b color='green'>configured "+ streamerProductAmount +" donations</b>.</html>";

		if (streamerProductAmount <= 0)
		{
			availableDonationsText = "<html>There are <b color='red'>no donations configured<b>. Go to the Live Loadout Twitch Extension configuration page where you copied your token to set them up.</html>";
		}

		availableDonationsPanel.setText(availableDonationsText);
		queuedTransactionsPanel.setText("There are "+ queuedTransactionAmount +" transactions queued.");

		// order by started at
		Collections.sort(activeProducts, new MarketplaceProductSorter());

		// guard: check if there are no fights to show
		if (activeProducts.size() <= 0)
		{
			cardLayout.show(wrapper, NO_TRANSACTIONS_PANEL);
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
				log.warn("An invalid marketplace product panel index was requested: "+ marketplaceProductPanelIndex);
				break;
			}

			marketplaceProductPanel.setMarketplaceProduct(marketplaceProduct);
			marketplaceProductPanel.rebuild();
			marketplaceProductPanelIndex ++;

			transactionListPanel.add(marketplaceProductPanel, transactionListConstraints);
			transactionListConstraints.gridy++;
		}
	}

	private void initializePanelButton(JPanel panel, JLabel label, String buttonTitle, ButtonCallback buttonCallback)
	{
		panel.setLayout(new BorderLayout());
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		panel.add(label, BorderLayout.CENTER);
		Styles.styleBigLabel(label, buttonTitle);
		panel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				buttonCallback.execute();
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				panel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				panel.setCursor(new Cursor(Cursor.HAND_CURSOR));
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				panel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			}
		});
	}

	public interface ButtonCallback {
		public void execute();
	}
}
