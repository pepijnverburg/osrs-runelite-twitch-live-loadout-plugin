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

import com.twitchliveloadout.fights.FightStateManager;
import com.twitchliveloadout.items.ItemStateManager;
import com.twitchliveloadout.twitch.TwitchThemeEntry;
import net.runelite.client.config.*;

@ConfigGroup("twitchstreamer")
public interface TwitchLiveLoadoutConfig extends Config
{
	public final static String PLUGIN_CONFIG_GROUP = "twitch-live-loadout";
	public final static String COLLECTION_LOG_CONFIG_KEY = "collection-log";
	public final static String LOOTING_BAG_ITEMS_CONFIG_KEY = "looting-bag-items";
	public final static String LOOTING_BAG_PRICE_CONFIG_KEY = "looting-bag-price";
	public final static String BANK_TABBED_ITEMS_CONFIG_KEY = "bank-items";
	public final static String BANK_PRICE_CONFIG_KEY = "bank-price";
	public final static String INVOCATIONS_CONFIG_KEY = "invocations";
	public final static String INVOCATIONS_RAID_LEVEL_CONFIG_KEY = "invocations-raid-level";
	public final static String QUESTS_CONFIG_KEY = "quests";

	public final static int MIN_OVERLAY_TOP_POSITION = 25;
	public final static int MAX_OVERLAY_TOP_POSITION = 75;

	@ConfigSection(
			name = "Twitch Extension & Token",
			description = "Authentication and extension configuration.",
			position = 0
	)
	String twitchSection = "twitch";

	@ConfigItem(
			keyName = "twitchToken",
			name = "Your copied Twitch Extension Token",
			description = "Your token can be found when configuring the Twitch Extension.",
			secret = true,
			position = 2,
			section = twitchSection
	)
	default String twitchToken()
	{
		return "";
	}

	@Range(
			min = MIN_OVERLAY_TOP_POSITION,
			max = MAX_OVERLAY_TOP_POSITION
	)
	@ConfigItem(
			keyName = "overlayTopPosition",
			name = "Overlay top position",
			description = "The position from the top left of the overlay in % of the screen height. Should be between "+ MIN_OVERLAY_TOP_POSITION +" and "+ MAX_OVERLAY_TOP_POSITION +".",
			position = 4,
			section = twitchSection
	)
	default int overlayTopPosition()
	{
		return 35;
	}

	@ConfigItem(
			keyName = "twitchTheme",
			name = "Twitch Extension Theme",
			description = "The theme of the Twitch Extension interface for viewers",
			position = 6,
			section = twitchSection
	)
	default TwitchThemeEntry twitchTheme()
	{
		return TwitchThemeEntry.LIGHT;
	}

	@ConfigSection(
			name = "Data Syncing",
			description = "Syncing conditions and multi-account settings",
			position = 2
	)
	String syncingSection = "syncing";

	@ConfigItem(
			keyName = "syncEnabled",
			name = "Enable syncing",
			description = "Toggle off to disable all syncing, hide extension to viewers and clear data.",
			position = 0,
			section = syncingSection
	)
	default boolean syncEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "syncDelay",
			name = "Stream delay (seconds)",
			description = "The amount of seconds to delay the sending of data to match your stream delay.",
			position = 2,
			section = syncingSection
	)
	default int syncDelay()
	{
		return 0;
	}

	@ConfigSection(
			name = "Anti Multi Logging",
			description = "Multi logging section to determine which account to sync",
			position = 3
	)
	String multiLogSection = "multiLog";

	@ConfigItem(
			keyName = "minWidowFocusTimeEnabled",
			name = "Window focus check enabled",
			description = "Enabled when you want to sync the RuneLite window that is in focus.",
			position = 4,
			section = multiLogSection
	)
	default boolean minWidowFocusTimeEnabled()
	{
		return true;
	}

	@Range(
			min = 0,
			max = 60
	)
	@ConfigItem(
			keyName = "minWindowFocusTime",
			name = "Focus time to sync (seconds)",
			description = "Seconds of focus on RuneLite window it takes to start syncing (against multi-logging)",
			position = 6,
			section = multiLogSection
	)
	default int minWindowFocusTime()
	{
		return 8;
	}

	@ConfigSection(
			name = "Items",
			description = "Syncing of items in inventory, equipment and bank.",
			position = 5
	)
	String itemsSection = "items";

	@ConfigItem(
			keyName = "inventoryEnabled",
			name = "Sync inventory items",
			description = "Synchronize all inventory items.",
			position = 2,
			section = itemsSection
	)
	default boolean inventoryEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "equipmentEnabled",
			name = "Sync equipment items",
			description = "Synchronize all equipment items.",
			position = 4,
			section = itemsSection
	)
	default boolean equipmentEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "lootingBagEnabled",
			name = "Sync looting bag items",
			description = "Synchronize all looting bag items.",
			position = 5,
			section = itemsSection
	)
	default boolean lootingBagEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "bankEnabled",
			name = "Sync bank items",
			description = "Synchronize bank value and top items based on GE value and configured maximum amount.",
			position = 6,
			section = itemsSection
	)
	default boolean bankEnabled()
	{
		return true;
	}

	@Range(
			min = 0,
			max = ItemStateManager.MAX_BANK_ITEMS
	)
	@ConfigItem(
			keyName = "bankItemsAmount",
			name = "Max bank items",
			description = "Maximum amount of items chosen by stack value.",
			position = 10,
			section = itemsSection
	)
	default int bankItemsAmount()
	{
		return ItemStateManager.MAX_BANK_ITEMS;
	}

	@ConfigItem(
			keyName = "bankPriceEnabled",
			name = "Sync bank value",
			description = "Synchronize bank value of all items.",
			position = 11,
			section = itemsSection
	)
	default boolean bankPriceEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "collectionLogEnabled",
			name = "Sync collection log",
			description = "Synchronize the collection log quantities and kill counts.",
			position = 12,
			section = itemsSection
	)
	default boolean collectionLogEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "collectionLogFilter",
			name = "Collection log filter (comma separated)",
			description = "Only include entry titles that include one of the keywords separated with a comma (e.g. 'abyssal, raids')",
			position = 14,
			section = itemsSection
	)
	default String collectionLogFilter()
	{
		return "";
	}

	@ConfigSection(
			name = "Combat",
			description = "Syncing of weapon damage, smite drains, poison damage, etc. per enemy.",
			position = 6
	)
	String combatSection = "combat";

	@ConfigItem(
			keyName = "fightStatisticsEnabled",
			name = "Sync combat statistics",
			description = "Synchronize statistics about PvM and PvP, such as DPS, freezes, splashes, etc.",
			position = 2,
			section = combatSection
	)
	default boolean fightStatisticsEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "fightStatisticsSpellsEnabled",
			name = "Track magic spells",
			description = "Enable tracking of freezes, entangles, blood spells and splashes.",
			position = 4,
			section = combatSection
	)
	default boolean fightStatisticsSpellsEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "fightStatisticsOthersEnabled",
			name = "Track damage by others",
			description = "Enable tracking of hitsplats of other players.",
			position = 6,
			section = combatSection
	)
	default boolean fightStatisticsOthersEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "fightStatisticsUnattackedEnabled",
			name = "Track un-attacked enemies",
			description = "Enable tracking of hitsplats on enemies you have not attacked, recommended in team settings such as Cox and ToB.",
			position = 8,
			section = combatSection
	)
	default boolean fightStatisticsUnattackedEnabled()
	{
		return false;
	}

	@Range(
			min = 0,
			max = FightStateManager.MAX_FIGHT_AMOUNT
	)
	@ConfigItem(
			keyName = "fightStatisticsMaxFightAmount",
			name = "Max combat fights",
			description = "Maximum amount of tracked fights with fixed upper limit of "+ FightStateManager.MAX_FIGHT_AMOUNT +".",
			position = 10,
			section = combatSection
	)
	default int fightStatisticsMaxFightAmount()
	{
		return FightStateManager.MAX_FIGHT_AMOUNT;
	}

	@Range(
			min = 0,
			max = 24 * 60
	)
	@ConfigItem(
			keyName = "fightStatisticsExpiryTime",
			name = "Fight expiry time (minutes)",
			description = "Reset a fight after the configured minutes of inactivity.",
			position = 12,
			section = combatSection
	)
	default int fightStatisticsExpiryTime()
	{
		return 180;
	}

	@ConfigItem(
			keyName = "fightStatisticsAutoIdling",
			name = "Auto idling of fight timer",
			description = "Stop fight timer when logged out or enemy is not visible.",
			position = 14,
			section = combatSection
	)
	default boolean fightStatisticsAutoIdling()
	{
		return true;
	}

	@ConfigItem(
			keyName = "fightStatisticsProtectionEnabled",
			name = "Enable Hardcore protection",
			description = "Disable tracking of fight stats on Hardcore Ironmen.",
			position = 16,
			section = combatSection
	)
	default boolean fightStatisticsProtectionEnabled()
	{
		return true;
	}

	@ConfigSection(
			name = "Raids",
			description = "Syncing of invocations",
			position = 7
	)
	String raidsSection = "raids";

	@ConfigItem(
			keyName = "invocationsEnabled",
			name = "Sync ToA Invocations",
			description = "Synchronize Tombs of Amascut raids invocations.",
			position = 2,
			section = raidsSection
	)
	default boolean invocationsEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "invocationsRaidLevelEnabled",
			name = "Sync ToA Raid Level",
			description = "Synchronize Tombs of Amascut raids level.",
			position = 4,
			section = raidsSection
	)
	default boolean invocationsRaidLevelEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "autoDetectInToaRaidEnabled",
			name = "Only Sync in ToA Raid",
			description = "Only synchronize invocations when in the ToA raid.",
			position = 6,
			section = raidsSection
	)
	default boolean autoDetectInToaRaidEnabled()
	{
		return true;
	}

	@ConfigSection(
			name = "Skills",
			description = "Syncing of skill experience, virtual levels, etc.",
			position = 8
	)
	String skillsSection = "skills";

	@ConfigItem(
			keyName = "skillsEnabled",
			name = "Sync skill levels",
			description = "Synchronize skill experience, level boosts and combat level.",
			position = 2,
			section = skillsSection
	)
	default boolean skillsEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "virtualLevelsEnabled",
			name = "Virtual levels",
			description = "Use maximum level of 126 instead of 99.",
			position = 4,
			section = skillsSection
	)
	default boolean virtualLevelsEnabled()
	{
		return true;
	}

	@ConfigSection(
			name = "Quests",
			description = "Syncing of quests and their status.",
			position = 8
	)
	String questsSection = "quests";

	@ConfigItem(
			keyName = "questsEnabled",
			name = "Sync quests",
			description = "Synchronize quests and their completion status.",
			position = 2,
			section = questsSection
	)
	default boolean questsEnabled()
	{
		return true;
	}

	@ConfigSection(
			name = "General info",
			description = "Syncing of display name, player weight, etc.",
			position = 10
	)
	String generalInfoSection = "general-info";

	@ConfigItem(
			keyName = "playerInfoEnabled",
			name = "Sync display name",
			description = "Synchronize basic player info such as display name.",
			position = 2,
			section = generalInfoSection
	)
	default boolean playerInfoEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "weightEnabled",
			name = "Sync weight of carried items",
			description = "Synchronize the weight of the equipment and inventory items, including weight reduction.",
			position = 4,
			section = generalInfoSection
	)
	default boolean weightEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "itemGoalsEnabled",
			name = "Sync item goals",
			description = "Synchronize the configured item wanted items, progress is automatic from inventory, gear and bank items.",
			position = 14,
			hidden = true,
			section = itemsSection
	)
	default boolean itemGoalsEnabled()
	{
		return false;
	}

	@ConfigSection(
			name = "Random Event Donations",
			description = "Settings for the Random Event triggered by donations",
			position = 3
	)
	String marketplaceSection = "donations";

	@ConfigItem(
			keyName = "marketplaceEnabled",
			name = "Enable Random Event donations",
			description = "Enable viewers to make donations to trigger in-game Random Events (requires Twitch Extension configuration!).",
			position = 4,
			hidden = false,
			section = marketplaceSection
	)
	default boolean marketplaceEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "marketplaceProtectionEnabled",
			name = "Enable Hardcore protection",
			description = "Automatically disable random events that could be dangerous for Hardcore Ironmen.",
			position = 6,
			hidden = false,
			section = marketplaceSection
	)
	default boolean marketplaceProtectionEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "marketplaceSoundsEnabled",
			name = "Enable Random Event sounds",
			description = "Disable to not play any sounds triggered by the random event effects.",
			position = 8,
			hidden = false,
			section = marketplaceSection
	)
	default boolean marketplaceSoundsEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "marketplaceStartOnLoadedAt",
			name = "Start on received time",
			description = "Base the expiry of the effects when it is received and not the transaction time, which can have delays.",
			position = 10,
			hidden = false,
			section = marketplaceSection
	)
	default boolean marketplaceStartOnLoadedAt()
	{
		return true;
	}

	@ConfigSection(
			name = "Advanced",
			description = "Settings for advanced usage",
			position = 99
	)
	String advancedSection = "advanced";
}
