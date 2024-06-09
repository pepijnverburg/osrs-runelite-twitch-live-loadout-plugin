package com.twitchliveloadout.ui;

import com.twitchliveloadout.marketplace.LambdaIterator;
import com.twitchliveloadout.marketplace.MarketplaceConstants;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.MarketplaceProductSorter;
import com.twitchliveloadout.marketplace.products.ChannelPointReward;
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
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class MarketplacePanel extends JPanel
{
	private static final String PLAYBACK_PANEL = "PLAYBACK_PANEL";
	private final static String SUCCESS_TEXT_COLOR = "#00ff00";
	private final static String WARNING_TEXT_COLOR = "#ffa500";
	private final static String ERROR_TEXT_COLOR = "#ff0000";

	private final GridBagConstraints constraints = new GridBagConstraints();
	private final GridBagConstraints productListConstraints = new GridBagConstraints();
	private final GridBagConstraints transactionListConstraints = new GridBagConstraints();
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel wrapper = new JPanel(cardLayout);

	private final GridBagConstraints playbackConstraints = new GridBagConstraints();
	private final TextPanel statusPanel = new TextPanel("Status:", "<html><b color='"+ WARNING_TEXT_COLOR +"'>SETTING UP</b></html>");
	private final TextPanel availableRandomEventsPanel = new TextPanel("Configured Random Events:", "<html>No Random Event are configured.</html>");
	private final TextPanel availableChannelPointRewardsPanel = new TextPanel("Configured Channel Point Rewards:", "<html>No Channel Point Rewards are configured.</html>");

	private final TextPanel queuedTransactionsPanel = new TextPanel("Queued Random Events:", "<html>No Random Events are queued.</html>");

	private final JPanel playbackWrapper = new JPanel(new BorderLayout());
	private final TextPanel playbackControlsPanel = new TextPanel("Playback Controls:", "<html>Pause and start to temporarily block distractions. Enable Preview Mode to temporarily test events from the Extension Configuration page in Twitch.</html>");
	private final JPanel startPanel = new JPanel(new BorderLayout());
	private final JLabel startLabel = new JLabel();
	private final JPanel testModePanel = new JPanel(new BorderLayout());
	private final JLabel testModeLabel = new JLabel();
	private final JPanel chaosModePanel = new JPanel(new BorderLayout());
	private final JLabel chaosModeLabel = new JLabel();
	private final JPanel freeModePanel = new JPanel(new BorderLayout());
	private final JLabel freeModeLabel = new JLabel();

	private final JPanel productListPanel = new JPanel(new GridBagLayout());
	private final TextPanel productListTitlePanel = new TextPanel("Active Random Events:", "<html>List of active random events.</html>");
	private final JPanel productListWrapper = new JPanel(new BorderLayout());
	private final CopyOnWriteArrayList<MarketplaceProductPanel> productPanels = new CopyOnWriteArrayList<>();

	private final JPanel transactionListPanel = new JPanel(new GridBagLayout());
	private final TextPanel transactionListTitlePanel = new TextPanel("Recent Random Events:", "<html>List of all recent Random Events.</html>");
	private final JPanel transactionListWrapper = new JPanel(new BorderLayout());
	private final CopyOnWriteArrayList<TwitchTransactionPanel> transactionPanels = new CopyOnWriteArrayList<>();

	private final MarketplaceManager marketplaceManager;
	private boolean rebuildRequested = false;

	public MarketplacePanel(MarketplaceManager marketplaceManager)
	{
		super(new GridBagLayout());

		this.marketplaceManager = marketplaceManager;

		initializeLayout();
	}

	public void onGameTick()
	{
		if (rebuildRequested)
		{
			rebuild();
		}
	}

	public void requestRebuild()
	{
		rebuildRequested = true;
	}

	public void rebuild()
	{
		rebuildRequested = false;
		repopulatePanels();
		updateTexts();
	}

	private void initializeLayout()
	{
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
		playbackWrapper.add(playbackControlsPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(startPanel, playbackConstraints);
		playbackConstraints.gridy++;

		if (TEST_MODE_AVAILABLE)
		{
			playbackWrapper.add(testModePanel, playbackConstraints);
			playbackConstraints.gridy++;
		}

		if (CHAOS_MODE_AVAILABLE)
		{
			playbackWrapper.add(chaosModePanel, playbackConstraints);
			playbackConstraints.gridy++;
		}

		if (FREE_MODE_AVAILABLE)
		{
			playbackWrapper.add(freeModePanel, playbackConstraints);
			playbackConstraints.gridy++;
		}

		playbackWrapper.add(availableRandomEventsPanel, playbackConstraints);
		playbackConstraints.gridy++;
		playbackWrapper.add(availableChannelPointRewardsPanel, playbackConstraints);
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

		// for now always show the playback panel
		cardLayout.show(wrapper, PLAYBACK_PANEL);

		TwitchLiveLoadoutPanel.initializePanelButton(startPanel, startLabel, getPlaybackButtonTitle(), () -> {
			final boolean isMarketplaceActive = marketplaceManager.isActive();

			if (isMarketplaceActive) {
				marketplaceManager.pauseActiveProducts();
			} else {
				marketplaceManager.playActiveProducts();
			}

			updateTexts();
			rebuildProductPanels();
		});

		TwitchLiveLoadoutPanel.initializePanelButton(testModePanel, testModeLabel, getTestModeButtonTitle(), () -> {
			final boolean isTestModeActive = marketplaceManager.isTestModeActive();

			if (isTestModeActive) {
				marketplaceManager.disableTestMode();
			} else {
				marketplaceManager.enableTestMode();
			}

			updateTexts();
			rebuildProductPanels();
		});

		TwitchLiveLoadoutPanel.initializePanelButton(chaosModePanel, chaosModeLabel, getChaosModeButtonTitle(), () -> {
			final boolean isChaosModeActive = marketplaceManager.isChaosModeActive();

			if (isChaosModeActive) {
				marketplaceManager.disableChaosMode();
			} else {
				marketplaceManager.enableChaosMode();
			}

			updateTexts();
			rebuildProductPanels();
		});

		TwitchLiveLoadoutPanel.initializePanelButton(freeModePanel, freeModeLabel, getFreeModeButtonTitle(), () -> {
			final boolean isFreeModeActive = marketplaceManager.isFreeModeActive();

			if (isFreeModeActive) {
				marketplaceManager.disableFreeMode();
			} else {
				marketplaceManager.enableFreeMode();
			}

			updateTexts();
			rebuildProductPanels();
		});

		// initialize all the panel slots
		for (int i = 0; i < MarketplaceConstants.MAX_MARKETPLACE_PRODUCT_AMOUNT_IN_MEMORY; i++)
		{
			MarketplaceProductPanel marketplaceProductPanel = new MarketplaceProductPanel(this);
			productPanels.add(marketplaceProductPanel);
			productListPanel.add(marketplaceProductPanel, productListConstraints);
			productListConstraints.gridy++;
			marketplaceProductPanel.rebuild();
		}

		// initialize all the panel slots
		for (int i = 0; i < MarketplaceConstants.MAX_TRANSACTION_AMOUNT_IN_MEMORY; i++)
		{
			TwitchTransactionPanel twitchTransactionPanel = new TwitchTransactionPanel(this, marketplaceManager);
			transactionPanels.add(twitchTransactionPanel);
			transactionListPanel.add(twitchTransactionPanel, transactionListConstraints);
			transactionListConstraints.gridy++;
			twitchTransactionPanel.rebuild();
		}

		repaint();
		revalidate();
	}

	private void repopulatePanels()
	{
		final CopyOnWriteArrayList<MarketplaceProduct> activeProducts = marketplaceManager.getActiveProducts();
		final CopyOnWriteArrayList<TwitchTransaction> archivedTransactions = marketplaceManager.getArchivedTransactions();

		int marketplaceProductPanelIndex = 0;
		int twitchTransactionPanelIndex = 0;

		// order by started at
		Collections.sort(activeProducts, new MarketplaceProductSorter());

		// first clear all the panels
		LambdaIterator.handleAll(productPanels, (productPanel) -> {
			productPanel.setEntity(null);
		});
		LambdaIterator.handleAll(transactionPanels, (transactionPanel) -> {
			transactionPanel.setEntity(null);
		});

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

			panel.setEntity(marketplaceProduct);
			marketplaceProductPanelIndex ++;
		}

		// directly add all the products again in the new order
		for (TwitchTransaction twitchTransaction : archivedTransactions)
		{

			// guard: skip when too many transactions are there
			if (twitchTransactionPanelIndex >= transactionPanels.size()) {
				break;
			}

			TwitchTransactionPanel panel = transactionPanels.get(twitchTransactionPanelIndex);

			// guard: check if the panel is valid
			if (panel == null)
			{
				log.warn("An invalid transaction product panel index was requested: "+ twitchTransactionPanelIndex);
				break;
			}

			panel.setEntity(twitchTransaction);
			twitchTransactionPanelIndex ++;
		}

		// finally update the panels
		rebuildProductPanels();
		rebuildTransactionPanels();
	}

	public void rebuildProductPanels()
	{
		LambdaIterator.handleAll(productPanels, (panel) -> {
			panel.rebuild();
		});
	}

	public void rebuildTransactionPanels()
	{
		LambdaIterator.handleAll(transactionPanels, (panel) -> {
			panel.rebuild();
		});
	}

	public void updateTexts()
	{
		final CopyOnWriteArrayList<MarketplaceProduct> activeProducts = marketplaceManager.getActiveProducts();
		final CopyOnWriteArrayList<StreamerProduct> streamerProducts = marketplaceManager.getStreamerProducts();
		final CopyOnWriteArrayList<ChannelPointReward> channelPointRewards = marketplaceManager.getChannelPointRewards();
		final CopyOnWriteArrayList<TwitchTransaction> queuedTransactions = marketplaceManager.getQueuedTransactions();
		final CopyOnWriteArrayList<TwitchTransaction> archivedTransactions = marketplaceManager.getArchivedTransactions();

		final int streamerProductAmount = streamerProducts.size();
		final int queuedTransactionAmount = queuedTransactions.size();
		final int activeProductAmount = activeProducts.size();
		final int archivedTransactionAmount = archivedTransactions.size();
		final int channelPointRewardAmount = channelPointRewards.size();
		final boolean areChannelEventsActive = !marketplaceManager.getConfig().twitchOAuthAccessToken().isEmpty();

		String statusText = "<html><b color='"+ SUCCESS_TEXT_COLOR +"'>Receiving Random Events is ACTIVE. Preview mode is disabled. Channel Events are "+ (areChannelEventsActive ? "ACTIVE" : "NOT SETUP") +".</b></html>";
		String availableRandomEventsText = "<html>You have <b color='"+ SUCCESS_TEXT_COLOR +"'>configured "+ streamerProductAmount +" Random Events</b>.</html>";
		String availableChannelPointRewardsText = "<html>You have <b color='"+ SUCCESS_TEXT_COLOR +"'>configured "+ channelPointRewardAmount +" Channel Point Rewards</b>.</html>";

		if (marketplaceManager.isTestModeActive())
		{
			statusText = "<html><b color='"+ WARNING_TEXT_COLOR +"'>Receiving preview Random Events is ACTIVE when you are logged in. Disable preview mode when done.</b></html>";
		}

		if (marketplaceManager.isChaosModeActive())
		{
			statusText = "<html><b color='"+ WARNING_TEXT_COLOR +"'>Chaos Mode is active, multiplying spawns and other things for Random Events. Disable chaos mode when done.</b></html>";
		}

		if (marketplaceManager.isFreeModeActive())
		{
			statusText = "<html><b color='"+ WARNING_TEXT_COLOR +"'>All configured Random Events are temporarily FREE for viewers. Disable free mode when done.</b></html>";
		}

		if (!marketplaceManager.isActive())
		{
			statusText = "<html><b color='"+ ERROR_TEXT_COLOR +"'>Random Events are temporarily PAUSED. Click PLAY ALL below to resume.</b></html>";
		}

		if (!marketplaceManager.getConfig().marketplaceEnabled())
		{
			statusText = "<html><b color='"+ ERROR_TEXT_COLOR +"'>Random Events are DISABLED in the plugin settings</b></html>";
		}

		if (marketplaceManager.isFetchingEbsTransactionsErrored())
		{
			statusText = "<html><b color='"+ ERROR_TEXT_COLOR +"'>Random Events are NOT AVAILABLE right now and therefore disabled. Contact support if the issue persists.</b></html>";
		}

		if (streamerProductAmount <= 0)
		{
			availableRandomEventsText = "<html>There are <b color='"+ ERROR_TEXT_COLOR +"'>no Random Events configured<b>. Go to the Live Loadout Twitch Extension configuration page where you copied your token to set them up.</html>";
		}

		if (channelPointRewardAmount <= 0)
		{
			availableChannelPointRewardsText = "<html>There are <b color='"+ WARNING_TEXT_COLOR +"'>no Channel Point Rewards available<b>. Go to your channel to set them up if you want Random Events to trigger when they are redeemed.</html>";
		}

		if (!areChannelEventsActive)
		{
			availableChannelPointRewardsText = "<html><b color='"+ WARNING_TEXT_COLOR +"'>No Channel Point Rewards can be fetched<b>. Configure the Twitch Channel Token if you want to enable Random Events triggered by Channel Points, follows, subscriptions, etc.</html>";
		}

		statusPanel.setText(statusText);
		startLabel.setText(getPlaybackButtonTitle());
		testModeLabel.setText(getTestModeButtonTitle());
		chaosModeLabel.setText(getChaosModeButtonTitle());
		freeModeLabel.setText(getFreeModeButtonTitle());
		availableRandomEventsPanel.setText(availableRandomEventsText);
		availableChannelPointRewardsPanel.setText(availableChannelPointRewardsText);
		queuedTransactionsPanel.setText("There are "+ queuedTransactionAmount +" Random Events queued.");
		productListTitlePanel.setText("There are "+ activeProductAmount +" Random Events active.");
		transactionListTitlePanel.setText("There are "+ archivedTransactionAmount +" recent Random Events.");
	}

	private String getPlaybackButtonTitle()
	{
		return "<html><b color='yellow'>"+ (marketplaceManager.isActive() ? "PAUSE ALL" : "PLAY ALL") +"</b></html>";
	}

	private String getTestModeButtonTitle()
	{
		return "<html><b color='"+ (marketplaceManager.isTestModeActive() ? "red" : "yellow") +"'>"+ (marketplaceManager.isTestModeActive() ? "DISABLE PREVIEW MODE" : "ENABLE PREVIEW MODE ("+ MarketplaceManager.TEST_MODE_EXPIRY_TIME_READABLE +")") +"</b></html>";
	}

	private String getChaosModeButtonTitle()
	{
		return "<html><b color='"+ (marketplaceManager.isChaosModeActive() ? "red" : "yellow") +"'>"+ (marketplaceManager.isChaosModeActive() ? "DISABLE CHAOS MODE" : "ENABLE CHAOS MODE ("+ MarketplaceManager.CHAOS_MODE_EXPIRY_TIME_READABLE +")") +"</b></html>";
	}

	private String getFreeModeButtonTitle()
	{
		return "<html><b color='"+ (marketplaceManager.isFreeModeActive() ? "red" : "yellow") +"'>"+ (marketplaceManager.isFreeModeActive() ? "DISABLE FREE MODE" : "ENABLE FREE MODE ("+ MarketplaceManager.FREE_MODE_EXPIRY_TIME_READABLE +")") +"</b></html>";
	}
}
