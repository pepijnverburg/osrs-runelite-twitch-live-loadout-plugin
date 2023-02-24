package com.twitchliveloadout.ui;

import com.twitchliveloadout.marketplace.MarketplaceConstants;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.MarketplaceProductSorter;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.StreamerProduct;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class MarketplacePanel extends JPanel
{
	private static final String PLAYBACK_PANEL = "PLAYBACK_PANEL";

	private final GridBagConstraints constraints = new GridBagConstraints();
	private final GridBagConstraints productListConstraints = new GridBagConstraints();
	private final GridBagConstraints transactionListConstraints = new GridBagConstraints();
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel wrapper = new JPanel(cardLayout);

	private final GridBagConstraints playbackConstraints = new GridBagConstraints();
	private final TextPanel statusPanel = new TextPanel("Status:", "<html><b color='orange'>SETTING UP</b></html>");
	private final TextPanel availableDonationsPanel = new TextPanel("Configured Donations:", "<html>No donations are configured.</html>");
	private final TextPanel queuedTransactionsPanel = new TextPanel("Queued Donations:", "<html>No donations are queued.</html>");

	private final JPanel playbackWrapper = new JPanel(new BorderLayout());
	private final TextPanel playbackControlsPanel = new TextPanel("Playback Controls:", "<html>Pause and start the random events for to temporarily block distractions.</html>");
	private final JPanel startPanel = new JPanel(new BorderLayout());
	private final JLabel startLabel = new JLabel();

	private final JPanel productListPanel = new JPanel(new GridBagLayout());
	private final TextPanel productListTitlePanel = new TextPanel("Active random events:", "<html>List of active random events.</html>");
	private final JPanel productListWrapper = new JPanel(new BorderLayout());
	private final CopyOnWriteArrayList<MarketplaceProductPanel> productPanels = new CopyOnWriteArrayList();

	private final JPanel transactionListPanel = new JPanel(new GridBagLayout());
	private final TextPanel transactionListTitlePanel = new TextPanel("Recent donations:", "<html>List of all recent donations.</html>");
	private final JPanel transactionListWrapper = new JPanel(new BorderLayout());
	private final CopyOnWriteArrayList<TwitchTransactionPanel> transactionPanels = new CopyOnWriteArrayList();

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

		productListConstraints.fill = GridBagConstraints.HORIZONTAL;
		productListConstraints.weightx = 1;
		productListConstraints.gridx = 0;
		productListConstraints.gridy = 0;

		transactionListConstraints.fill = GridBagConstraints.HORIZONTAL;
		transactionListConstraints.weightx = 1;
		transactionListConstraints.gridx = 0;
		transactionListConstraints.gridy = 0;

		playbackWrapper.setLayout(new GridBagLayout());
		playbackWrapper.setBorder(new EmptyBorder(10, 0, 10, 0));
		playbackWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		playbackWrapper.add(statusPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(availableDonationsPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(playbackControlsPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(startPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(queuedTransactionsPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(productListTitlePanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(productListWrapper, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(transactionListTitlePanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(transactionListWrapper, playbackConstraints);
		playbackConstraints.gridy++;

		productListWrapper.setLayout(new GridBagLayout());
		productListWrapper.setBorder(new EmptyBorder(10, 0, 10, 0));
		productListWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		productListPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
		productListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		productListWrapper.add(productListPanel, productListConstraints);
		productListConstraints.gridy++;

		transactionListWrapper.setLayout(new GridBagLayout());
		transactionListWrapper.setBorder(new EmptyBorder(10, 0, 10, 0));
		transactionListWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		transactionListPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
		transactionListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		transactionListWrapper.add(transactionListPanel, transactionListConstraints);
		transactionListConstraints.gridy++;

		wrapper.add(playbackWrapper, PLAYBACK_PANEL);
		constraints.gridy++;
		add(wrapper, BorderLayout.NORTH);

		// initialize all the panel slots without adding them to the UI
		for (int i = 0; i < MarketplaceConstants.MAX_MARKETPLACE_PRODUCT_AMOUNT_IN_MEMORY; i++)
		{
			MarketplaceProductPanel marketplaceProductPanel = new MarketplaceProductPanel();
			productPanels.add(marketplaceProductPanel);
		}

		// initialize all the panel slots without adding them to the UI
		for (int i = 0; i < MarketplaceConstants.MAX_TRANSACTION_AMOUNT_IN_MEMORY; i++)
		{
			TwitchTransactionPanel twitchTransactionPanel = new TwitchTransactionPanel();
			transactionPanels.add(twitchTransactionPanel);
		}
	}

	public void rebuild()
	{
		rebuildContent();
		repaint();
		revalidate();
	}

	private void rebuildContent()
	{
		final CopyOnWriteArrayList<MarketplaceProduct> activeProducts = marketplaceManager.getActiveProducts();
		final CopyOnWriteArrayList<StreamerProduct> streamerProducts = marketplaceManager.getStreamerProducts();
		final CopyOnWriteArrayList<TwitchTransaction> queuedTransactions = marketplaceManager.getQueuedTransactions();
		final CopyOnWriteArrayList<TwitchTransaction> archivedTransactions = marketplaceManager.getArchivedTransactions();

		final int streamerProductAmount = streamerProducts.size();
		final int queuedTransactionAmount = queuedTransactions.size();
		final int activeProductAmount = activeProducts.size();
		final int archivedTransactionAmount = archivedTransactions.size();

		final boolean isMarketplaceActive = marketplaceManager.isActive();
		int marketplaceProductPanelIndex = 0;
		int twitchTransactionPanelIndex = 0;
		String statusText = "<html>Receiving donations is <b color='green'>ACTIVE</b></html>";
		String availableDonationsText = "<html>You have <b color='green'>configured "+ streamerProductAmount +" donations</b>.</html>";

		if (!marketplaceManager.isActive())
		{
			statusText = "<html>All donations are temporarily <b color='red'>PAUSED</b></html>";
		}

		if (streamerProductAmount <= 0)
		{
			availableDonationsText = "<html>There are <b color='red'>no donations configured<b>. Go to the Live Loadout Twitch Extension configuration page where you copied your token to set them up.</html>";
		}

		initializePanelButton(startPanel, startLabel, "<html>"+ (isMarketplaceActive ? "PAUSE ALL" : "RESUME ALL") +"</html>", () -> {
			if (isMarketplaceActive) {
				marketplaceManager.pause();
			} else {
				marketplaceManager.start();
			}
			rebuild();
		});

		statusPanel.setText(statusText);
		availableDonationsPanel.setText(availableDonationsText);
		queuedTransactionsPanel.setText("There are "+ queuedTransactionAmount +" donations queued.");
		productListTitlePanel.setText("There are "+ activeProductAmount +" random events active.");
		transactionListTitlePanel.setText("There are "+ archivedTransactionAmount +" recent donations.");

		// order by started at
		Collections.sort(activeProducts, new MarketplaceProductSorter());

		// for now always show the playback panel
		cardLayout.show(wrapper, PLAYBACK_PANEL);

		// reset product list
		productListPanel.removeAll();
		productListConstraints.gridy = 0;

		// first clear all the panels
		for (MarketplaceProductPanel productPanel : productPanels)
		{
			productPanel.setMarketplaceProduct(null);
		}

		// directly add all the products again in the new order
		for (MarketplaceProduct marketplaceProduct : activeProducts)
		{
			MarketplaceProductPanel panel = productPanels.get(marketplaceProductPanelIndex);

			// guard: check if the panel is valid
			if (panel == null)
			{
				log.warn("An invalid marketplace product panel index was requested: "+ marketplaceProductPanelIndex);
				break;
			}

			panel.setMarketplaceProduct(marketplaceProduct);
			panel.rebuild();
			marketplaceProductPanelIndex ++;

			productListPanel.add(panel, productListConstraints);
			productListConstraints.gridy++;
		}

		// reset transaction list
		transactionListPanel.removeAll();
		transactionListConstraints.gridy = 0;

		// first clear all the panels
		for (TwitchTransactionPanel transactionPanel : transactionPanels)
		{
			transactionPanel.setTwitchTransaction(null);
		}

		// directly add all the products again in the new order
		for (TwitchTransaction twitchTransaction : archivedTransactions)
		{
			TwitchTransactionPanel panel = transactionPanels.get(twitchTransactionPanelIndex);

			// guard: check if the panel is valid
			if (panel == null)
			{
				log.warn("An invalid transaction product panel index was requested: "+ twitchTransactionPanelIndex);
				break;
			}

			panel.setTwitchTransaction(twitchTransaction);
			panel.rebuild();
			twitchTransactionPanelIndex ++;

			transactionListPanel.add(panel, transactionListConstraints);
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

	public static String humanizeDuration(Duration duration) {
		return duration.toString()
				.substring(2)
				.replaceAll("(\\d[HMS])(?!$)", "$1 ")
				.toLowerCase();
	}
}
