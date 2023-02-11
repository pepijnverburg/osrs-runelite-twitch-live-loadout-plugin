/*
 * Copyright (c) 2020, Pepijn Verburg <pepijn.verburg@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.twitchliveloadout;

import com.google.inject.Provides;
import com.twitchliveloadout.fights.FightStateManager;
import com.twitchliveloadout.items.CollectionLogManager;
import com.twitchliveloadout.items.ItemStateManager;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.minimap.MinimapManager;
import com.twitchliveloadout.raids.InvocationsManager;
import com.twitchliveloadout.skills.SkillStateManager;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.TwitchSegmentType;
import com.twitchliveloadout.twitch.TwitchState;
import com.twitchliveloadout.twitch.TwitchStateEntry;
import com.twitchliveloadout.ui.CanvasListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.vars.AccountType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import com.twitchliveloadout.ui.TwitchLiveLoadoutPanel;
import net.runelite.client.task.Schedule;
import com.google.gson.*;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.twitchliveloadout.TwitchLiveLoadoutConfig.PLUGIN_CONFIG_GROUP;

/**
 * Manages polling and event listening mechanisms to synchronize the state
 * to the Twitch Configuration Service. All client data is fetched in this main entry point
 * and passed along to dedicated managers. Also you will see that this class if fairly 'polluted'
 * with try-catch statements. This helps making sure that any breaking changes to Oldschool Runescape and/or
 * RuneLite will less likely cause issues.
 *
 * Find events via: net.runelite.api.events
 */
@PluginDescriptor(
	name = "Twitch Live Loadout",
	description = "Send live Equipment, Collection Log, Combat Statistics and more to Twitch Extensions as a streamer.",
	enabledByDefault = true
)
@Slf4j
public class TwitchLiveLoadoutPlugin extends Plugin
{
	@Inject
	private TwitchLiveLoadoutConfig config;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	/**
	 * Scheduled executor that does not run on the client thread.
	 */
	private ScheduledThreadPoolExecutor scheduledExecutor;

	/**
	 * The plugin panel to manage data such as combat fights.
	 */
	private static final String ICON_FILE = "/panel_icon.png";
	private TwitchLiveLoadoutPanel pluginPanel;
	private NavigationButton navigationButton;

	/**
	 * Twitch Configuration Service state that can be mapped to a JSON.
	 */
	private TwitchState twitchState;

	/**
	 * Twitch Configuration Service API end-point helpers.
	 */
	private TwitchApi twitchApi;

	/**
	 * Dedicated manager for all fight information.
	 */
	private FightStateManager fightStateManager;

	/**
	 * Dedicated manager for all item information.
	 */
	private ItemStateManager itemStateManager;

	/**
	 * Dedicated manager for all skill / stat information.
	 */
	private SkillStateManager skillStateManager;

	/**
	 * Dedicated manager for collection log information.
	 */
	private CollectionLogManager collectionLogManager;

	/**
	 * Dedicated manager for marketplace products information.
	 */
	private MarketplaceManager marketplaceManager;

	/**
	 * Dedicated manager for minimap information.
	 */
	private MinimapManager minimapManager;

	/**
	 * Dedicated manager for ToA invocations raid information.
	 */
	private InvocationsManager invocationsManager;

	/**
	 * Cache to check for player name changes as game state is not reliable for this
	 */
	private String lastPlayerName = null;

	/**
	 * Listener for any events of the canvas (e.g. focus and unfocus)
	 */
	private CanvasListener canvasListener = null;

	/**
	 * Temporary flags to disable features while still in staging
	 */
	private final static boolean ENABLE_MINIMAP = false;

	/**
	 * Initialize this plugin
	 * @throws Exception
	 */
	@Override
	protected void startUp() throws Exception
	{
		super.startUp();

		initializeExecutors();
		initializeCanvasListeners();
		initializeTwitch();
		initializeManagers();
		initializePanel();

		// tasks to execute immediately on boot
		updateMarketplaceStreamerProducts();
		updateMarketplaceEbsProducts();
		skillStateManager.updateSkills();
	}

	private void initializeExecutors()
	{
		try {
			scheduledExecutor = new ScheduledThreadPoolExecutor(1);
		} catch (Exception exception) {
			log.warn("An error occurred when initializing the executors: ", exception);
		}
	}

	private void initializeTwitch()
	{
		try {
			twitchState = new TwitchState(this, config, canvasListener);
			twitchApi = new TwitchApi(this, client, config, chatMessageManager);
		} catch (Exception exception) {
			log.warn("An error occurred when initializing Twitch: ", exception);
		}
	}

	private void initializeManagers()
	{
		try {
			fightStateManager = new FightStateManager(this, config, client);
			itemStateManager = new ItemStateManager(this, twitchState, client, itemManager, config);
			skillStateManager = new SkillStateManager(twitchState, client);
			collectionLogManager = new CollectionLogManager(this, twitchState, client);
			marketplaceManager = new MarketplaceManager(this, twitchApi, client, config, chatMessageManager);
			minimapManager = new MinimapManager(this, twitchState, client);
			invocationsManager = new InvocationsManager(this, twitchState, client);
		} catch (Exception exception) {
			log.warn("An error occurred when initializing the managers: ", exception);
		}
	}

	private void initializePanel()
	{
		try {
			pluginPanel = new TwitchLiveLoadoutPanel(twitchApi, fightStateManager, canvasListener, config);
			pluginPanel.rebuild();

			final BufferedImage icon = ImageUtil.loadImageResource(getClass(), ICON_FILE);

			navigationButton = NavigationButton.builder()
					.tooltip("Twitch Live Loadout Status")
					.icon(icon)
					.priority(99)
					.panel(pluginPanel)
					.build();

			clientToolbar.addNavigation(navigationButton);
		} catch (Exception exception) {
			log.warn("An error occurred when initializing the UI panels: ", exception);
		}
	}

	private void initializeCanvasListeners()
	{
		try {
			canvasListener = new CanvasListener(config);
		} catch (Exception exception) {
			log.warn("An error occurred when initializing the canvas listeners: ", exception);
		}
	}

	/**
	 * Cleanup properly after disabling the plugin
	 * @throws Exception
	 */
	@Override
	protected void shutDown() throws Exception
	{
		super.shutDown();

		shutDownPanels();
		shutDownManagers();
		shutDownTwitch();
		shutDownCanvasListeners();
		shutDownSchedulers();
	}

	private void shutDownCanvasListeners()
	{
		try {
			client.getCanvas().removeFocusListener(canvasListener);
		} catch (Exception exception) {
			log.warn("An error occurred when removing the canvas listeners: ", exception);
		}
	}

	private void shutDownTwitch()
	{
		try {
			// only the API requires dedicated shutdown
			twitchApi.shutDown();
		} catch (Exception exception) {
			log.warn("An error occurred when shutting down Twitch: ", exception);
		}
	}

	private void shutDownManagers()
	{
		try {
			// Only some managers require a shutdown as well
			fightStateManager.shutDown();
			marketplaceManager.shutDown();
		} catch (Exception exception) {
			log.warn("An error occurred when shutting down the managers: ", exception);
		}
	}

	private void shutDownPanels()
	{
		try {
			clientToolbar.removeNavigation(navigationButton);
		} catch (Exception exception) {
			log.warn("An error occurred when shutting down the UI panels: ", exception);
		}
	}

	private void shutDownSchedulers()
	{
		scheduledExecutor.getQueue().clear();
		scheduledExecutor.shutdown();
	}

	/**
	 * Polling mechanism to update the state only when it has changed.
	 */
	@Schedule(period = 500, unit = ChronoUnit.MILLIS, asynchronous = true)
	public void syncState()
	{
		try {
			final JsonObject filteredState = twitchState.getFilteredState();

			// We will not verify whether the set was successful here
			// because it is possible that the request is being delayed
			// due to the custom streamer delay
			final boolean isScheduled = twitchApi.scheduleBroadcasterState(filteredState);

			// guard: check if the scheduling was successful due to for example rate limiting
			// if not we will not acknowledge the change
			if (!isScheduled)
			{
				return;
			}

			final String filteredStateString = filteredState.toString();
			final String newFilteredStateString = twitchState.getFilteredState().toString();

			// Guard: check if the state has changed in the mean time,
			// because the request takes some time, in this case we will
			// not acknowledge the change
			if (!filteredStateString.equals(newFilteredStateString))
			{
				return;
			}

			// when all is scheduled and there are no in-between changes we can move
			// to the next state slice
			twitchState.nextCyclicState();
			twitchState.acknowledgeChange();
		} catch (Exception exception) {
			log.warn("Could not sync the current state to Twitch due to the following error: ", exception);
		}
	}

	/**
	 * Polling mechanism to update the fight statistics as many
	 * events are continuously updating various properties (e.g. game ticks).
	 * This would overload the update life cycle too much so a
	 * small penalty in the form of the (polling) delay is worthwhile.
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncFightStatisticsState()
	{
		try {
			if (shouldTrackFightStatistics())
			{
				JsonObject fightStatistics = fightStateManager.getFightStatisticsState();
				twitchState.setFightStatistics(fightStatistics);
			}
		} catch (Exception exception) {
			log.warn("Could not update the fight statistics due to the following error: ", exception);
		}
	}

	/**
	 * Polling mechanism to sync player info.
	 * We cannot use the game state update events as the player name is not loaded then.
	 */
	@Schedule(period = 1, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncPlayerInfo()
	{
		try {
			// account type can only be fetched on client thread
			runOnClientThread(() -> {
				long accountHash = client.getAccountHash();
				AccountType accountType = client.getAccountType();
				String playerName = getPlayerName();

				// only handle on changes
				if (playerName != null && !playerName.equals(lastPlayerName))
				{
					if (config.playerInfoEnabled())
					{
						twitchState.setPlayerName(playerName);
					}

					twitchState.onPlayerNameChanged(playerName);
					lastPlayerName = playerName;
				}

				twitchState.setAccountHash(accountHash);
				twitchState.setAccountType(accountType);
			});
		} catch (Exception exception) {
			log.warn("Could not sync player info to state: ", exception);
		}
	}

	/**
	 * Polling mechanism to sync minimap as there is no update event for this.
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncMiniMap()
	{
		try {
			if (ENABLE_MINIMAP && config.marketplaceEnabled())
			{
				minimapManager.updateMinimap();
			}
		} catch (Exception exception) {
			log.warn("Could not sync mini map: ", exception);
		}
	}

	/**
	 * Polling mechanism to update the configuration segment cache
	 * Note that this request is subject to rate limits by twitch of 20 times per minute.
	 * We keep it at a safe rate to also support the fetching when someone is alting.
	 * Documentation: https://dev.twitch.tv/docs/api/reference#get-extension-configuration-segment
	 */
	@Schedule(period = 10, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void updateMarketplaceStreamerProducts()
	{
		try {
			if (config.syncEnabled())
			{
				twitchApi.updateConfigurationSegment(TwitchSegmentType.BROADCASTER);
				twitchApi.updateConfigurationSegment(TwitchSegmentType.DEVELOPER);
			}
			if (config.marketplaceEnabled())
			{
				// streamer products are based on the broadcaster configuration segment
				// making it dependant on the updating of the configuration segments
				marketplaceManager.updateStreamerProducts();
			}
		} catch (Exception exception) {
			log.warn("Could not update the configuration segments due to the following error: ", exception);
		}
	}

	/**
	 * Polling mechanism to update the EBS products configured in Twitch.
	 */
	@Schedule(period = 1, unit = ChronoUnit.SECONDS, asynchronous = true) // TODO: change BACK
	public void updateMarketplaceEbsProducts()
	{
		try {
			if (config.marketplaceEnabled())
			{
				// update the EBS products
				marketplaceManager.updateEbsProducts();
			}
		} catch (Exception exception) {
			log.warn("Could not update the EBS products due to the following error: ", exception);
		}
	}

	/**
	 * Polling mechanism to get new Twitch transactions and manage activation and de-activation of products
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void updateMarketplaceTransactions()
	{
		try {
			if (config.marketplaceEnabled())
			{
				// get new transactions from Twitch
				marketplaceManager.handleNewTwitchTransactions();

				// apply new products and clean expired ones
				runOnClientThread(() -> {
					marketplaceManager.applyQueuedTransactions();
					marketplaceManager.cleanExpiredProducts();
				});
			}
		} catch (Exception exception) {
			log.warn("Could not update the extension transactions due to the following error: ", exception);
		}
	}

	/**
	 * Polling mechanism to check whether we are in ToA
	 */
	@Schedule(period = 5, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void checkIfInToA()
	{
		try {
			if (config.invocationsEnabled() && config.autoDetectInToaRaidEnabled())
			{
				invocationsManager.checkIfInToA();
			}
		} catch (Exception exception) {
			log.warn("Could not check if in ToA: ", exception);
		}
	}

	/**
	 * Simulate game ticks when not logged in to still register for idling fight time when not logged in
	 */
	@Schedule(period = 600, unit = ChronoUnit.MILLIS, asynchronous = true)
	public void onLobbyGameTick()
	{
		try {
			if (client.getGameState() != GameState.LOGIN_SCREEN)
			{
				return;
			}

			if (shouldTrackFightStatistics())
			{
				fightStateManager.onGameTick();
			}
		} catch (Exception exception) {
			log.warn("Could not handle lobby game tick event: ", exception);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		try {
			itemStateManager.onItemContainerChanged(event);
		} catch (Exception exception) {
			log.warn("Could not handle item container change event: ", exception);
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		try {
			if (config.skillsEnabled())
			{
				skillStateManager.updateSkills();
			}

			if (shouldTrackFightStatistics())
			{
				fightStateManager.onStatChanged(event);
			}
		} catch (Exception exception) {
			log.warn("Could not handle stat change event: ", exception);
		}
	}

	@Subscribe
	public void onFakeXpDrop(FakeXpDrop event)
	{
		try {
			if (shouldTrackFightStatistics())
			{
				fightStateManager.onFakeXpDrop(event);
			}
		} catch (Exception exception) {
			log.warn("Could not handle fake XP drop event: ", exception);
		}
	}

	//@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		try {
			if (config.collectionLogEnabled())
			{
				collectionLogManager.onNpcLootReceived(event);
			}
		} catch (Exception exception) {
			log.warn("Could not handle on NPC loot received event: ", exception);
		}
	}

	@Subscribe
	public void onFocusChanged(FocusChanged event)
	{
		try {
			final boolean isFocused = event.isFocused();

			if (isFocused)
			{
				canvasListener.enableFocus();
			}
			else {
				canvasListener.disableFocus();
			}
		} catch (Exception exception) {
			log.warn("Could not handle on focus change event: ", exception);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		try {
			// alternative method to enable focus on window if somehow the other focus listener
			// any menu click will enable focus, this includes walking and stuff
			canvasListener.enableFocus();

			if (config.marketplaceEnabled())
			{
				marketplaceManager.onMenuOptionClicked(event);
			}
		} catch (Exception exception) {
			log.warn("Could not handle menu option clicked event: ", exception);
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		try {
			if (shouldTrackFightStatistics())
			{
				fightStateManager.onAnimationChanged(event);
			}
		} catch (Exception exception) {
			log.warn("Could not handle animation change event: ", exception);
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		try {
			if (shouldTrackFightStatistics())
			{
				fightStateManager.onGraphicChanged(event);
			}
		} catch (Exception exception) {
			log.warn("Could not handle graphic change event: ", exception);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		try {
			if (shouldTrackFightStatistics())
			{
				fightStateManager.onHitsplatApplied(event);
			}
		} catch (Exception exception) {
			log.warn("Could not handle hitsplat event: ", exception);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		try {
			if (shouldTrackFightStatistics())
			{
				fightStateManager.onNpcDespawned(npcDespawned);
			}
		} catch (Exception exception) {
			log.warn("Could not handle NPC despawned event: ", exception);
		}
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned playerDespawned)
	{
		try {
			if (shouldTrackFightStatistics())
			{
				fightStateManager.onPlayerDespawned(playerDespawned);
			}
		} catch (Exception exception) {
			log.warn("Could not handle player despawned event: ", exception);
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged interactingChanged)
	{
		try {
			if (shouldTrackFightStatistics())
			{
				fightStateManager.onInteractingChanged(interactingChanged);
			}
		} catch (Exception exception) {
			log.warn("Could not handle interacting change event: ", exception);
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		try {
			if (config.marketplaceEnabled())
			{
				marketplaceManager.onGameTick();
			}

			if (shouldTrackFightStatistics())
			{
				fightStateManager.onGameTick();
			}
		} catch (Exception exception) {
			log.warn("Could not handle game tick event: ", exception);
		}
	}

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		try {
			if (config.marketplaceEnabled())
			{
				marketplaceManager.onClientTick();
			}
		} catch (Exception exception) {
			log.warn("Could not handle client tick event: ", exception);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		try {
			if (config.marketplaceEnabled())
			{
				marketplaceManager.onGameStateChanged(gameStateChanged);
			}

			// always update on game state change as well to instantly react to logout and login
			twitchState.setAccountHash(client.getAccountHash());
			twitchState.setAccountType(client.getAccountType());
		} catch (Exception exception) {
			log.warn("Could not handle game state event: ", exception);
		}
	}

	/**
	 * Handle player changes
	 */
	@Subscribe
	public void onPlayerChanged(PlayerChanged playerChanged)
	{
		try {
			if (config.marketplaceEnabled())
			{
				marketplaceManager.onPlayerChanged(playerChanged);
			}
		} catch (Exception exception) {
			log.warn("Could not handle player changed event: ", exception);
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired scriptPostFired)
	{
		try {
			if (config.collectionLogEnabled())
			{
				collectionLogManager.onScriptPostFired(scriptPostFired);
			}

			if (config.invocationsEnabled())
			{
				invocationsManager.onScriptPostFired(scriptPostFired);
			}
		} catch (Exception exception) {
			log.warn("Could not handle script post fired event:", exception);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		try {
			if (config.collectionLogEnabled())
			{
				collectionLogManager.onVarbitChanged(varbitChanged);
			}
		} catch (Exception exception) {
			log.warn("Could not handle varbit change event: ", exception);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		try {
			String key = configChanged.getKey();

			// Always clear the scheduled state updates
			// when either the value is increased of decreased
			// it can mess up the state updates badly
			if (key.equals("syncDelay"))
			{
				twitchApi.clearScheduledBroadcasterStates();
			}

			// Handle keys that should trigger an update of the state as well.
			// Note that on load these events are not triggered, meaning that
			// in the constructor of the TwitchState class one should also load
			// this configuration value!
			if (key.equals("overlayTopPosition"))
			{
				twitchState.setOverlayTopPosition(config.overlayTopPosition());
			}
			else if (key.equals("virtualLevelsEnabled"))
			{
				twitchState.setVirtualLevelsEnabled(config.virtualLevelsEnabled());
			}
			else if (key.equals("twitchTheme"))
			{
				twitchState.setTwitchTheme(config.twitchTheme());
			}

			// somehow when in the settings tab the focus is lost, which means
			// that when changing configs the focus stays lost and it hard to get feedback
			// whether a setting is changed correctly. By overriding the focus flag when
			// changing configs the data is being synced anyways.
			canvasListener.enableFocus();
		} catch (Exception exception) {
			log.warn("Could not handle config change event: ", exception);
		}
	}

	/**
	 * Periodically update the connectivity panel to show the latest status
	 */
	@Schedule(period = 1, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void updateConnectivityPanel()
	{
		try {
			if (!hasValidPanels())
			{
				return;
			}

			pluginPanel.getConnectivityPanel().rebuild();
		} catch (Exception exception) {
			log.warn("Could not update the connectivity panel due to the following error: ", exception);
		}
	}

	public void updateCombatPanel()
	{
		try {
			if (!hasValidPanels())
			{
				return;
			}

			pluginPanel.getCombatPanel().rebuild();
		} catch (Exception exception) {
			log.warn("Could not update the combat panel due to the following error: ", exception);
		}
	}

	public boolean hasValidPanels()
	{
		return pluginPanel != null;
	}

	public void runOnClientThread(ClientThreadAction action)
	{
		try {
			clientThread.invoke(new Runnable() {
				@Override
				public void run() {
					try {
						action.execute();
					} catch (Exception exception) {
						log.warn("Could not execute action on client thread: ", exception);
					}
				}
			});
		} catch (Exception exception) {
			log.warn("Could not invoke an action later on client thread: ", exception);
		}
	}

	public ScheduledFuture scheduleOnClientThread(ClientThreadAction action, long delayMs)
	{

		// guard: check if we should execute immediately
		if (delayMs <= 0)
		{
			runOnClientThread(action);
			return null;
		}

		try {
			return scheduledExecutor.schedule(new Runnable() {
				@Override
				public void run() {
					runOnClientThread(action);
				}
			}, delayMs, TimeUnit.MILLISECONDS);
		} catch (Exception exception) {
			log.warn("Could not schedule an action on the client thread (delay: "+ delayMs +"): ", exception);
		}

		return null;
	}

	public interface ClientThreadAction {
		public void execute();
	}

	public void setConfiguration(String configKey, Object payload)
	{
		try {
			String accountHash = Long.toString(client.getAccountHash());
			String scopedConfigKey = getScopedConfigKey(accountHash, configKey);
			configManager.setConfiguration(PLUGIN_CONFIG_GROUP, scopedConfigKey, payload);
		} catch (Exception exception) {
			log.warn("Could not set the configuration due to the following error: ", exception);
		}
	}

	public String getConfiguration(String configKey)
	{
		try {
			String playerName = getPlayerName();
			String accountHash = Long.toString(client.getAccountHash());

			if (playerName == null)
			{
				playerName = "unknown";
			}

			String scopedConfigKey = getScopedConfigKey(accountHash, configKey);
			String configuration = configManager.getConfiguration(PLUGIN_CONFIG_GROUP, scopedConfigKey);

			// MIGRATION TO ACCOUNT HASH FROM PLAYER NAME
			// TODO: remove the migration with player name after a while that the new account hash is used
			// only migrate when there is no hash configuration yet
			String oldNameScopedConfigKey = getScopedConfigKey(playerName, configKey);
			String oldNameConfiguration = configManager.getConfiguration(PLUGIN_CONFIG_GROUP, oldNameScopedConfigKey);
			boolean oldConfigurationIsEmpty = (oldNameConfiguration == null || oldNameConfiguration.trim().equals(""));
			boolean configurationIsEmpty = (configuration == null || configuration.trim().equals(""));
			if (!oldConfigurationIsEmpty && configurationIsEmpty)
			{
				configManager.setConfiguration(PLUGIN_CONFIG_GROUP, scopedConfigKey, oldNameConfiguration);
				configManager.setConfiguration(PLUGIN_CONFIG_GROUP, oldNameScopedConfigKey, "");

				// after migrate set to old value for now to return it properly
				configuration = oldNameConfiguration;
				log.info("Migration of config is completed. Moved: "+ oldNameScopedConfigKey +", to: "+ scopedConfigKey);
			}

			return configuration;
		} catch (Exception exception) {
			log.warn("Could not get the configuration due to the following error: ", exception);
		}

		return null;
	}

	private String getScopedConfigKey(String accountIdentifier, String configKey)
	{
		try {
			String accountIdentifierPrefix = accountIdentifier.replaceAll("\\s+","_").trim();
			String scopedConfigKey = accountIdentifierPrefix +"-"+ configKey;
			return scopedConfigKey;
		} catch (Exception exception) {
			log.warn("Could not get the scoped config key due to the following error: ", exception);
		}

		return null;
	}

	public String getPlayerName()
	{
		try {
			if (!isLoggedIn())
			{
				return null;
			}

			return client.getLocalPlayer().getName();
		} catch (Exception exception) {
			log.warn("Could not get the player name due to the following error: ", exception);
		}

		return null;
	}

	public boolean isDangerousAccountType()
	{
		JsonElement accountTypeRaw = twitchState.getState().get(TwitchStateEntry.ACCOUNT_TYPE.getKey());
		String hardcoreRegular = AccountType.HARDCORE_IRONMAN.toString();
		String hardcoreGroup = AccountType.HARDCORE_GROUP_IRONMAN.toString();

		// guard: check if account type can be found
		if (accountTypeRaw == null)
		{
			return false;
		}

		String accountType = accountTypeRaw.getAsString();

		if (accountType == null)
		{
			return false;
		}

		// guard: check if regular HC or HCGIM
		if (!accountType.equals(hardcoreRegular) && !accountType.equals(hardcoreGroup))
		{
			return false;
		}

		return true;
	}

	public boolean shouldTrackFightStatistics()
	{
		boolean isDisabledGeneral = !config.fightStatisticsEnabled();
		boolean isDisabledDangerous = config.fightStatisticsProtectionEnabled() && isDangerousAccountType();

		return !isDisabledGeneral && !isDisabledDangerous;
	}

	public boolean isLoggedIn()
	{

		try {
			// guard: check game state
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return false;
			}

			// guard: check local player instance
			if (client.getLocalPlayer() == null)
			{
				return false;
			}

			return true;
		} catch (Exception exception) {
			log.warn("Could not get the whether the player is logged in due to the following error: ", exception);
		}

		return false;
	}

	@Provides
	TwitchLiveLoadoutConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TwitchLiveLoadoutConfig.class);
	}
}
