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
	private static final String PLAYBACK_PANEL = "PLAYBACK_PANEL";

	private final GridBagConstraints constraints = new GridBagConstraints();
	private final GridBagConstraints transactionListConstraints = new GridBagConstraints();
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel wrapper = new JPanel(cardLayout);

	private final GridBagConstraints playbackConstraints = new GridBagConstraints();
	private final TextPanel statusPanel = new TextPanel("Status:", "<html><b color='orange'>SETTING UP</b></html>");
	private final TextPanel availableDonationsPanel = new TextPanel("Configured Donations:", "<html>No donations are configured.</html>");
	private final TextPanel queuedTransactionsPanel = new TextPanel("Queued Donations:", "<html>No transactions are queued.</html>");
	private final JPanel playbackWrapper = new JPanel(new BorderLayout());
	private final TextPanel playbackControlsPanel = new TextPanel("Playback Controls:", "<html>Pause and start the random events for to temporarily block distractions.</html>");
	private final JPanel startPanel = new JPanel(new BorderLayout());
	private final JLabel startLabel = new JLabel();

	private final JPanel productListPanel = new JPanel(new GridBagLayout());
	private final TextPanel productListTitlePanel = new TextPanel("Active donations:", "<html>List of active random event donations.</html>");
	private final JPanel productListWrapper = new JPanel(new BorderLayout());
	private final CopyOnWriteArrayList<MarketplaceProductPanel> productPanels = new CopyOnWriteArrayList();

//	private final JPanel transactionListPanel = new JPanel(new GridBagLayout());
//	private final TextPanel transactionTitlePanel = new TextPanel("Past donations:", "<html>List of all the donations of which the random events are finished.</html>");
//	private final JPanel transactionListWrapper = new JPanel(new BorderLayout());
//	private final CopyOnWriteArrayList<MarketplaceProductPanel> transactionPanels = new CopyOnWriteArrayList();


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

		productListWrapper.setLayout(new GridBagLayout());
		productListWrapper.setBorder(new EmptyBorder(10, 0, 10, 0));
		productListWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		productListPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
		productListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		productListWrapper.add(productListPanel, constraints);
		transactionListConstraints.gridy++;

		wrapper.add(playbackWrapper, PLAYBACK_PANEL);
		constraints.gridy++;
		add(wrapper, BorderLayout.NORTH);

		// initialize all the fight panel slots without adding them to the UI
		for (int i = 0; i < MarketplaceConstants.MAX_MARKETPLACE_PRODUCT_AMOUNT_IN_MEMORY; i++)
		{
			MarketplaceProductPanel marketplaceProductPanel = new MarketplaceProductPanel();
			productPanels.add(marketplaceProductPanel);
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
		final boolean isMarketplaceActive = marketplaceManager.isActive();
		int marketplaceProductPanelIndex = 0;
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

		initializePanelButton(startPanel, startLabel, "<html>"+ (isMarketplaceActive ? "Pause" : "Start") +"</html>", () -> {
			if (isMarketplaceActive) {
				marketplaceManager.pause();
			} else {
				marketplaceManager.start();
			}
			rebuild();
		});

		statusPanel.setText(statusText);
		availableDonationsPanel.setText(availableDonationsText);
		queuedTransactionsPanel.setText("There are "+ queuedTransactionAmount +" transactions queued.");

		// order by started at
		Collections.sort(activeProducts, new MarketplaceProductSorter());

		// for now always show the playback panel
		cardLayout.show(wrapper, PLAYBACK_PANEL);
		productListPanel.removeAll();
		transactionListConstraints.gridy = 0;

		// first clear all the fight in the panels
		for (MarketplaceProductPanel productPanel : productPanels)
		{
			productPanel.setMarketplaceProduct(null);
		}

		// directly add all the fights again in the new order
		for (MarketplaceProduct marketplaceProduct : activeProducts)
		{
			MarketplaceProductPanel productPanel = productPanels.get(marketplaceProductPanelIndex);

			// guard: check if the panel is valid
			if (productPanel == null)
			{
				log.warn("An invalid marketplace product panel index was requested: "+ marketplaceProductPanelIndex);
				break;
			}

			productPanel.setMarketplaceProduct(marketplaceProduct);
			productPanel.rebuild();
			marketplaceProductPanelIndex ++;

			productListPanel.add(productPanel, transactionListConstraints);
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
