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
package net.runelite.client.plugins.twitchstreamer;

import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import com.google.gson.*;

import net.runelite.api.events.StatChanged;

import javax.inject.Inject;
import java.time.temporal.ChronoUnit;

@PluginDescriptor(
	name = "Twitch Streamer",
	description = "Send Real-time Equipment, Skills, Inventory, Bank and more to Twitch Extensions for additional viewer engagement.",
	enabledByDefault = false
)

/**
 * Manages polling and event listening mechanisms to synchronize the state
 * to the Twitch Configuration Service. All client data is fetched in this class
 * ad passed to a couple of other classes.
 */
public class TwitchStreamerPlugin extends Plugin
{
	@Inject
	private TwitchStreamerConfig twitchStreamerConfig;

	@Inject
	private Client client;

	/**
	 * Twitch Configuration Service state that can be mapped to a JSON.
	 */
	private ConfigurationServiceState configurationServiceState;

	/**
	 * Twitch Configuration Service API end-point helpers.
	 */
	private ConfigurationServiceApi configurationServiceApi;

	/**
	 * Initialize this plugin
	 * @throws Exception
	 */
	@Override
	protected void startUp() throws Exception
	{
		super.startUp();

		configurationServiceState = new ConfigurationServiceState(client);
		configurationServiceApi = new ConfigurationServiceApi();
	}

	/**
	 * Helper to get the current configuration.
	 * @param configManager
	 * @return
	 */
	@Provides
	TwitchStreamerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TwitchStreamerConfig.class);
	}

	/**
	 * Polling mechanism to update the state only when it has changed.
	 * This avoids data being pushed when any of part of the state changed
	 * and forces us to combine update requests in one.
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncState()
	{
		final boolean updateRequired = configurationServiceState.isChanged();

		// Guard: check if something has changed to avoid unnecessary updates.
		if (!updateRequired) {
			return;
		}

		final JsonObject state = configurationServiceState.getState();
		boolean setResult = configurationServiceApi.setBroadcasterState(state);

		// Guard: check if the update was successful.
		// If not this will automatically trigger a new attempt later.
		if (!setResult) {
			return;
		}

		configurationServiceState.acknowledgeChange();
	}

	/**
	 * Polling mechanism to update the player info
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncPlayerInfo()
	{

		// Guard: player info is not available when not loaded
		if (!playerIsLoaded())
		{
			return;
		}

		final String playerName = client.getLocalPlayer().getName();

		configurationServiceState.setPlayerName(playerName);
	}

	/**
	 * Polling mechanism to update the equipment/gear and weight data
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncEquipment()
	{

		// Guard: equipment is not available when not loaded
		if (!playerIsLoaded())
		{
			return;
		}

		final PlayerComposition playerComposition = client.getLocalPlayer().getPlayerComposition();
		final int[] equipmentIds = playerComposition.getEquipmentIds();
		final int weight = client.getWeight();

		configurationServiceState.setEquipmentIds(equipmentIds);
		configurationServiceState.setWeight(weight);
	}

	/**
	 * Skill and level information is synced through stat events.
	 * @param event
	 */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		final int[] skillExperiences = client.getSkillExperiences();
		final int combatLevel = client.getLocalPlayer().getCombatLevel();

		configurationServiceState.setSkillExperiences(skillExperiences);
		configurationServiceState.setCombatLevel(combatLevel);
	}

	/**
	 * Polling mechanism to update the bank data
	 */
	@Schedule(period = 2, unit = ChronoUnit.SECONDS, asynchronous = true)
	public void syncBank()
	{
		// TODO: figure out how to optimize this to fit the 5KB limit by Twitch...
		// final ItemContainer container = client.getItemContainer(InventoryID.BANK);
	}

	/**
	 * Check whether the player is currently logged in and loaded.
	 * This is used for several state polling mechanisms.
	 * @return true when logged in
	 */
	public boolean playerIsLoaded()
	{
		final boolean isLoggedIn = client.getGameState() == GameState.LOGGED_IN;

		// Guard: check if logged in.
		if (!isLoggedIn) {
			return false;
		}

		final Player player = client.getLocalPlayer();
		final PlayerComposition playerComposition = player.getPlayerComposition();
		final String playerName = player.getName();
		final boolean playerLoaded = (playerComposition != null && playerName != null);

		// Guard: check if player is loaded.
		if (!playerLoaded) {
			return false;
		}

		final Object equipmentIds = playerComposition.getEquipmentIds();
		final boolean equipmentLoaded = (equipmentIds != null);

		// Guard: check if equipment is loaded.
		if (!equipmentLoaded) {
			return false;
		}

		// TODO: more checks?

		return true;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged configChanged)
	{
		// A configuration change should be directly reflected?
	}
}
