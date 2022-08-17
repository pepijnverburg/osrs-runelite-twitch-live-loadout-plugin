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
import com.twitchliveloadout.marketplace.MarketplaceProduct;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.TwitchThemeEntry;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("twitchstreamer")
public interface TwitchLiveLoadoutConfig extends Config
{
	String PLUGIN_CONFIG_GROUP = "twitch-live-loadout";
	String COLLECTION_LOG_CONFIG_KEY = "collection-log";
	String LOOTING_BAG_ITEMS_CONFIG_KEY = "looting-bag-items";
	String LOOTING_BAG_PRICE_CONFIG_KEY = "looting-bag-price";
	String BANK_TABBED_ITEMS_CONFIG_KEY = "bank-items";
	String BANK_PRICE_CONFIG_KEY = "bank-price";

	@ConfigSection(
			name = "Twitch Extension",
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

	@ConfigItem(
			keyName = "overlayTopPosition",
			name = "Overlay top position",
			description = "The percentual position of the viewer overlay in % of the screen height. '0' falls back to default of viewer.",
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
			name = "Data syncing",
			description = "Syncing conditions and multi-account settings",
			position = 2
	)
	String syncingSection = "syncing";

	@ConfigItem(
			keyName = "syncEnabled",
			name = "Sync enabled",
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
			name = "Active time check enabled",
			description = "Toggle off to disable the active time check for anti multi-logging purposes",
			position = 4,
			section = multiLogSection
	)
	default boolean minWidowFocusTimeEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "minWindowFocusTime",
			name = "Active time to sync (seconds)",
			description = "Seconds of focus on RuneLite window it takes to start syncing (against multi-logging)",
			position = 6,
			section = multiLogSection
	)
	default int minWindowFocusTime()
	{
		return 5;
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

	@ConfigItem(
			keyName = "bankItemAmount",
			name = "Max bank items",
			description = "Maximum amount of items chosen by stack value.",
			position = 10,
			section = itemsSection
	)
	default int bankItemAmount()
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
			name = "Marketplace",
			description = "Settings for the marketplace",
			position = 12
	)
	String marketplaceSection = "marketplace";

	@ConfigItem(
			keyName = "marketplaceEnabled",
			name = "Enable marketplace",
			description = "Synchronize the marketplace configuration, such as enabled and featured items.",
			position = 2,
			hidden = true,
			section = marketplaceSection
	)
	default boolean marketplaceEnabled()
	{
		return false;
	}

	@ConfigItem(
			keyName = "featuredMarketplaceProduct",
			name = "Featured marketplace product",
			description = "The marketplace product you want to highlight on stream.",
			position = 4,
			hidden = true,
			section = marketplaceSection
	)
	default MarketplaceProduct featuredMarketplaceProduct()
	{
		return MarketplaceProduct.NONE;
	}

	@ConfigItem(
			keyName = "devPlayerGraphicId",
			name = "Dev Player Graphic ID",
			description = "Testing Graphic ID on player.",
			position = 97,
			hidden = true,
			section = marketplaceSection
	)
	default int devPlayerGraphicId()
	{
		return 1160;
	}

	@ConfigItem(
			keyName = "devObjectSpawnModelId",
			name = "Dev Object Spawn Model ID",
			description = "Testing model ID when spawning objects for the marketplace.",
			position = 97,
			hidden = true,
			section = marketplaceSection
	)
	default int devObjectSpawnModelId()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "devObjectSpawnAnimationId",
			name = "Dev Object Spawn Animation ID",
			description = "Testing animation ID when spawning objects for the marketplace.",
			position = 98,
			hidden = true,
			section = marketplaceSection
	)
	default int devObjectSpawnAnimationId()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "devMarketplaceProductSpawn",
			name = "Dev Marketplace Product Spawn",
			description = "Testing product.",
			position = 99,
			hidden = true,
			section = marketplaceSection
	)
	default MarketplaceProduct devMarketplaceProductSpawn()
	{
		return MarketplaceProduct.NONE;
	}

	@ConfigSection(
			name = "Advanced",
			description = "Settings for advanced usage",
			position = 99
	)
	String advancedSection = "advanced";

	@ConfigItem(
			keyName = "twitchEbsBaseUrl",
			name = "Twitch EBS Base URL",
			description = "The base URL of the Twitch Extension Back-end Service used to switch environments during testing.",
			position = 2,
			hidden = true,
			section = advancedSection
	)
	default String twitchEbsBaseUrl()
	{
		return TwitchApi.DEFAULT_TWITCH_EBS_BASE_URL;
	}
}
