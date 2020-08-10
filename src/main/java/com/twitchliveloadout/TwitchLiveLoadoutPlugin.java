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
package net.runelite.client.plugins.twitchliveloadout;

import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.attackstyles.AttackStylesPlugin;
import net.runelite.client.task.Schedule;
import com.google.gson.*;

import javax.inject.Inject;
import java.time.temporal.ChronoUnit;

/**
 * Manages polling and event listening mechanisms to synchronize the state
 * to the Twitch Configuration Service. All client data is fetched in this class
 * ad passed to a couple of other classes.
 */
@PluginDescriptor(
	name = "Twitch Live Loadout",
	description = "Send Real-time Equipment, Skills, Combat Statistics, Inventory, Bank and more to Twitch Extensions for additional viewer engagement.",
	enabledByDefault = false
)
@PluginDependency(AttackStylesPlugin.class)
public class TwitchLiveLoadoutPlugin extends Plugin
{
	@Inject
	private TwitchLiveLoadoutConfig config;

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChatMessageManager chatMessageManager;

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
	 * Initialize this plugin
	 * @throws Exception
	 */
	@Override
	protected void startUp() throws Exception
	{
		super.startUp();

		twitchState = new TwitchState(config, itemManager);
		twitchApi = new TwitchApi(config, chatMessageManager);

		fightStateManager = new FightStateManager(config, twitchState, client);
		itemStateManager = new ItemStateManager(twitchState, client, itemManager, config);
		skillStateManager = new SkillStateManager(twitchState, client);
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

	/**
	 * Polling mechanism to update the state only when it has changed.
	 * This avoids data being pushed when any of part of the state changed
	 * and forces us to combine update requests in one.
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncState()
	{
		final boolean updateRequired = twitchState.isChanged();

		// Guard: check if something has changed to avoid unnecessary updates.
		if (!updateRequired)
		{
			return;
		}

		final JsonObject filteredState = twitchState.getFilteredState();

		// We will not verify whether the set was successful here
		// because it is possible that the request is being delayed
		// due to the custom streamer delay
		twitchApi.scheduleBroadcasterState(filteredState);

		final String filteredStateString = filteredState.toString();
		final String newFilteredStateString = twitchState.getFilteredState().toString();

		// Guard: check if the state has changed in the mean time,
		// because the request takes some time, in this case we will
		// not acknowledge the change
		if (!filteredStateString.equals(newFilteredStateString))
		{
			return;
		}

		twitchState.acknowledgeChange();
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
		JsonObject fightStatistics = fightStateManager.getFightStatisticsState();

		// TMP: debugging
//		System.out.println(fightStatistics.toString());

		twitchState.setFightStatistics(fightStatistics);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		final String playerName = client.getLocalPlayer().getName();
		twitchState.setPlayerName(playerName);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		itemStateManager.onItemContainerChanged(event);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		skillStateManager.onStatChanged(event);
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		fightStateManager.onGraphicChanged(event);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		 fightStateManager.onHitsplatApplied(event);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned npcDespawned)
	{
		fightStateManager.onNpcDespawned(npcDespawned);
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned playerDespawned)
	{
		fightStateManager.onPlayerDespawned(playerDespawned);
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		 fightStateManager.onGameTick(tick);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
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
	}
}
