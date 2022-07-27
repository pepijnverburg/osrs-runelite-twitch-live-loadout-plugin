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
import com.twitchliveloadout.skills.SkillStateManager;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.TwitchState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.twitchliveloadout.TwitchLiveLoadoutConfig.PLUGIN_CONFIG_GROUP;

/**
 * Manages polling and event listening mechanisms to synchronize the state
 * to the Twitch Configuration Service. All client data is fetched in this main entry point
 * and passed along to dedicated managers. Also you will see that this class if fairly 'polluted'
 * with try-catch statements. This helps making sure that any breaking changes to Oldschool Runescape and/or
 * RuneLite will less likely cause issues.
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

	@Inject
	private ScheduledExecutorService executor;

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
	 * Cache to check for player name changes as game state is not reliable for this
	 */
	private String lastPlayerName = null;

	/**
	 * Initialize this plugin
	 * @throws Exception
	 */
	@Override
	protected void startUp() throws Exception
	{
		super.startUp();

		initializeTwitch();
		initializeManagers();
		initializePanel();
	}

	private void initializeTwitch()
	{
		try {
			twitchState = new TwitchState(config);
			twitchApi = new TwitchApi(this, client, config, chatMessageManager);
		} catch (Exception exception) {
			log.warn("An error occurred when initializing Twitch: ", exception);
		}
	}

	private void initializeManagers()
	{
		try {
			fightStateManager = new FightStateManager(this, config, client);
			itemStateManager = new ItemStateManager(twitchState, client, itemManager, config);
			skillStateManager = new SkillStateManager(twitchState, client);
			collectionLogManager = new CollectionLogManager(this, twitchState, client);
			marketplaceManager = new MarketplaceManager(this, twitchState, client, config);
			minimapManager = new MinimapManager(this, twitchState, client);
		} catch (Exception exception) {
			log.warn("An error occurred when initializing the managers: ", exception);
		}
	}

	private void initializePanel()
	{
		try {
			pluginPanel = new TwitchLiveLoadoutPanel(twitchApi, fightStateManager);
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
	}

	private void shutDownTwitch()
	{
		try {
			// Only the API requires dedicated shutdown
			twitchApi.shutDown();

			twitchState = null;
			twitchApi = null;
		} catch (Exception exception) {
			log.warn("An error occurred when shutting down Twitch: ", exception);
		}
	}

	private void shutDownManagers()
	{
		try {
			// Only the fight state manager requires dedicated shutdown
			fightStateManager.shutDown();

			fightStateManager = null;
			itemStateManager = null;
			skillStateManager = null;
			collectionLogManager = null;
			marketplaceManager = null;
			minimapManager = null;
		} catch (Exception exception) {
			log.warn("An error occurred when shutting down the managers: ", exception);
		}
	}

	private void shutDownPanels()
	{
		try {
			pluginPanel = null;
			clientToolbar.removeNavigation(navigationButton);
		} catch (Exception exception) {
		log.warn("An error occurred when shutting down the UI panels: ", exception);
		}
	}

	/**
	 * Polling mechanism to update the state only when it has changed.
	 */
	@Schedule(period = 500, unit = ChronoUnit.MILLIS, asynchronous = true)
	public void syncState()
	{
		try {
			// Base this on the fact is the state is changed for a fixed time for throttling purposes.
			// This makes the updates more smooth when multiple changes occur fast after each other.
			// Take for example switching gear: when the first armour piece is worn a change is directly
			// triggered, causing the rest of the switch to come the update after (a few seconds later).
			// This throttling behaviour makes sure that quickly succeeding changes are batched and with that
			// let for example gear switches come through in one state update towards the viewer.
			final boolean isChangedLongEnough = twitchState.isChangedLongEnough();
			final boolean hasCyclicState = twitchState.hasCyclicState();
			final boolean shouldAlwaysSync = twitchState.shouldAlwaysSync();

			// Guard: check if something has changed to avoid unnecessary updates.
			if (!shouldAlwaysSync && !isChangedLongEnough && !hasCyclicState)
			{
				return;
			}

			final JsonObject filteredState = twitchState.getFilteredState();

			// We will not verify whether the set was successful here
			// because it is possible that the request is being delayed
			// due to the custom streamer delay
			final boolean isScheduled = twitchApi.scheduleBroadcasterState(filteredState);

			// guard: check if the scheduling was successful due to for example rate limiting
			// if not we will not acknowledge the change
			if (!isScheduled) {
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
		if (!config.fightStatisticsEnabled())
		{
			return;
		}

		try {
			JsonObject fightStatistics = fightStateManager.getFightStatisticsState();
			twitchState.setFightStatistics(fightStatistics);
		} catch (Exception exception) {
			log.warn("Could not update the fight statistics due to the following error: ", exception);
		}
	}

	/**
	 * Polling mechanism to sync player info.
	 * We cannot use the game state update events as the player name is not loaded then.
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncPlayerInfo()
	{
		if (!config.playerInfoEnabled())
		{
			return;
		}

		try {
			String playerName = getPlayerName();

			if (playerName == null || playerName.equals(lastPlayerName))
			{
				return;
			}

			twitchState.setPlayerName(playerName);
			collectionLogManager.onPlayerNameChanged(playerName);
			lastPlayerName = playerName;
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
		if (!config.marketplaceEnabled())
		{
			return;
		}

		try {
			minimapManager.updateMinimap();
		} catch (Exception exception) {
			log.warn("Could not sync mini map: ", exception);
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

			if (config.fightStatisticsEnabled())
			{
				fightStateManager.onGameTick();
			}
		} catch (Exception exception) {
			log.warn("Could not handle lobby game tick event: ", exception);
		}
	}

	/**
	 * Keep track of all marketplace transactions and apply them if they are not yet
	 */
	@Schedule(period = 1000, unit = ChronoUnit.MILLIS, asynchronous = true)
	public void syncMarketplaceTransactions()
	{
		if (isLoggedIn() && config.devPlayerGraphicId() > 0) {
			System.out.println("GRAPHIC SPAWNED!");
			Player player = client.getLocalPlayer();
			// https://everythingrs.com/tools/runescape/graphics
			player.setGraphic(config.devPlayerGraphicId());
			player.setSpotAnimFrame(0);

//			actor.setAnimation(currentAnimation.animationId);
//			actor.setGraphic(currentGfx.gfxId);
//			actor.setActionFrame(currentAnimation.startFrame);
//			actor.setSpotAnimFrame(currentGfx.startFrame);
			// 453: nice fire under feet
			// 469: puffs of air out of ears
			// 1180: puff of round smoke explodes and goes up (mind-blown?)
			// 481: red big wave under feet
			// 444: jad heal
			// 436: redemption heal
			// 437: retribution
			// 1176 / 1177: cool spirals around you
			// 1191: lava opens underneath and spews fire! neat
			// 199: level up fireworks
			//
		}

		if (!config.marketplaceEnabled())
		{
			return;
		}

		try {
			marketplaceManager.applyNewProducts();
		} catch (Exception exception) {
			log.warn("Could not apply marketplace transactions: ", exception);
		}

		try {
			marketplaceManager.cleanProducts();
		} catch (Exception exception) {
			log.warn("Could not clean marketplace products: ", exception);
		}
	}

	/**
	 * Make sure all marketplace objects are shown when chunks are loaded and viewport changes
	 */
	@Schedule(period = 1000, unit = ChronoUnit.MILLIS, asynchronous = true)
	public void syncMarketplaceObjectsToScene()
	{
		if (!config.marketplaceEnabled())
		{
			return;
		}

		try {
			marketplaceManager.syncMarketplaceObjectsToScene();
		} catch (Exception exception) {
			log.warn("Could not sync marketplace objects to scene: ", exception);
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
				skillStateManager.onStatChanged(event);
			}

			if (config.fightStatisticsEnabled())
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
		if (!config.fightStatisticsEnabled())
		{
			return;
		}

		try {
			fightStateManager.onFakeXpDrop(event);
		} catch (Exception exception) {
			log.warn("Could not handle fake XP drop event: ", exception);
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!config.fightStatisticsEnabled())
		{
			return;
		}

		try {
			fightStateManager.onAnimationChanged(event);
		} catch (Exception exception) {
			log.warn("Could not handle animation change event: ", exception);
		}
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		if (!config.fightStatisticsEnabled())
		{
			return;
		}

		try {
			fightStateManager.onGraphicChanged(event);
		} catch (Exception exception) {
			log.warn("Could not handle graphic change event: ", exception);
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!config.fightStatisticsEnabled())
		{
			return;
		}

		try {
			fightStateManager.onHitsplatApplied(event);
		} catch (Exception exception) {
			log.warn("Could not handle hitsplat event: ", exception);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		if (!config.fightStatisticsEnabled())
		{
			return;
		}

		try {
			fightStateManager.onNpcDespawned(npcDespawned);
		} catch (Exception exception) {
			log.warn("Could not handle NPC despawned event: ", exception);
		}
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned playerDespawned)
	{
		if (!config.fightStatisticsEnabled())
		{
			return;
		}

		try {
			fightStateManager.onPlayerDespawned(playerDespawned);
		} catch (Exception exception) {
			log.warn("Could not handle player despawned event: ", exception);
		}
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged interactingChanged)
	{
		if (!config.fightStatisticsEnabled())
		{
			return;
		}

		try {
			fightStateManager.onInteractingChanged(interactingChanged);
		} catch (Exception exception) {
			log.warn("Could not handle interacting change event: ", exception);
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!config.fightStatisticsEnabled())
		{
			return;
		}

		try {
			fightStateManager.onGameTick();
		} catch (Exception exception) {
			log.warn("Could not handle game tick event: ", exception);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (!config.marketplaceEnabled())
		{
			return;
		}

		try {
			marketplaceManager.onGameStateChanged(event);
		} catch (Exception exception) {
			log.warn("Could not handle game state event: ", exception);
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
		} catch (Exception exception) {
			log.warn("Could not collection log script execution:", exception);
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

			twitchState.forceChange();
		} catch (Exception exception) {
			log.warn("Could not handle config change event: ", exception);
		}
	}

	public boolean hasValidPanels()
	{
		return pluginPanel != null;
	}

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

	public void runOnClientThread(ClientThreadAction action)
	{
		try {
			clientThread.invokeLater(new Runnable() {
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

	public void scheduleOnClientThread(ClientThreadAction action, long delayMs)
	{
		try {
			executor.schedule(new Runnable() {
				@Override
				public void run() {
					runOnClientThread(action);
				}
			}, delayMs, TimeUnit.MILLISECONDS);
		} catch (Exception exception) {
			log.warn("Could not schedule an action on the client thread (delay: "+ delayMs +"): ", exception);
		}
	}

	public void setConfiguration(String configKey, Object payload)
	{
		try {
			String scopedConfigKey = getScopedConfigKey(configKey);
			configManager.setConfiguration(PLUGIN_CONFIG_GROUP, scopedConfigKey, payload);
		} catch (Exception exception) {
			log.warn("Could not set the configuration due to the following error: ", exception);
		}
	}

	public String getConfiguration(String configKey)
	{
		try {
			String scopedConfigKey = getScopedConfigKey(configKey);
			return configManager.getConfiguration(PLUGIN_CONFIG_GROUP, scopedConfigKey);
		} catch (Exception exception) {
			log.warn("Could not get the configuration due to the following error: ", exception);
		}

		return null;
	}

	/**
	 * Helper to get the current configuration.
	 * @param configManager
	 * @return
	 */
	@Provides
	TwitchLiveLoadoutConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TwitchLiveLoadoutConfig.class);
	}

	private String getScopedConfigKey(String configKey)
	{
		try {
			String playerName = getPlayerName();

			if (playerName == null)
			{
				playerName = "unknown";
			}

			String playerNamePrefix = playerName.replaceAll("\\s+","_").trim();
			String scopedConfigKey = playerNamePrefix +"-"+ configKey;
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

	public interface ClientThreadAction {
		public void execute();
	}
}
