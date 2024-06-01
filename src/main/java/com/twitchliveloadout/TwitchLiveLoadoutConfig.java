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
import com.twitchliveloadout.marketplace.MarketplaceConstants;
import com.twitchliveloadout.twitch.TwitchThemeEntry;
import com.twitchliveloadout.twitch.TwitchVisibilityEntry;
import net.runelite.client.config.*;

import java.awt.Color;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.CHAOS_MODE_AVAILABLE;

@ConfigGroup("twitchstreamer")
public interface TwitchLiveLoadoutConfig extends Config
{
	public final static String PLUGIN_CONFIG_GROUP = "twitch-live-loadout";

	public final static String TWITCH_OAUTH_ACCESS_TOKEN_KEY = "twitchOAuthAccessToken";
	public final static String TWITCH_OAUTH_REFRESH_TOKEN_KEY = "twitchOAuthRefreshToken";

	public final static String COLLECTION_LOG_CONFIG_KEY = "collection-log";
	public final static String LOOTING_BAG_ITEMS_CONFIG_KEY = "looting-bag-items";
	public final static String LOOTING_BAG_PRICE_CONFIG_KEY = "looting-bag-price";
	public final static String BANK_TABBED_ITEMS_CONFIG_KEY = "bank-items";
	public final static String BANK_PRICE_CONFIG_KEY = "bank-price";
	public final static String INVOCATIONS_CONFIG_KEY = "invocations";
	public final static String INVOCATIONS_RAID_LEVEL_CONFIG_KEY = "invocations-raid-level";
	public final static String QUESTS_CONFIG_KEY = "quests";
	public final static String SEASONAL_RELICS_CONFIG_KEY = "seasonal-relics";
	public final static String SEASONAL_AREAS_CONFIG_KEY = "seasonal-areas";

	public final static int MIN_OVERLAY_TOP_POSITION = 25;
	public final static int MAX_OVERLAY_TOP_POSITION = 75;

	@ConfigSection(
			name = "Twitch Extension",
			description = "Authentication and layout config for the extension on Twitch.",
			position = 0
	)
	String twitchSection = "twitch";

	@ConfigItem(
			keyName = "twitchToken",
			name = "<html><body style=\"color: #fdff00;\">Your copied Twitch Extension Token</body></html>",
			description = "This token is copied when configuring the Twitch Extension.",
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
			name = "Vertical position",
			description = "The position from the top left of the overlay in % of the screen height. Should be between "+ MIN_OVERLAY_TOP_POSITION +" and "+ MAX_OVERLAY_TOP_POSITION +".",
			position = 4,
			section = twitchSection
	)
	@Units(Units.PERCENT)
	default int overlayTopPosition()
	{
		return 35;
	}

	@ConfigItem(
			keyName = "twitchTheme",
			name = "Extension theme",
			description = "The theme of the Twitch Extension interface for viewers",
			position = 6,
			section = twitchSection
	)
	default TwitchThemeEntry twitchTheme()
	{
		return TwitchThemeEntry.LIGHT;
	}

	@ConfigItem(
			keyName = "twitchVisibility",
			name = "Extension visibility",
			description = "Always show extension or only when viewers hover the video stream",
			position = 8,
			hidden = false,
			section = twitchSection
	)
	default TwitchVisibilityEntry twitchVisibility()
	{
		return TwitchVisibilityEntry.NORMAL;
	}

	@ConfigSection(
			name = "Data Syncing",
			description = "Syncing conditions and multi-account settings",
			position = 4
	)
	String syncingSection = "syncing";

	@ConfigItem(
			keyName = "syncEnabled",
			name = "Enable data syncing",
			description = "Toggle off to disable all syncing, hide extension to viewers and clear data.",
			position = 0,
			section = syncingSection
	)
	default boolean syncEnabled()
	{
		return true;
	}

	@Range(
			min = 0,
			max = 120
	)
	@ConfigItem(
			keyName = "syncDelay",
			name = "Stream delay",
			description = "The amount of seconds to delay the sending of data to match your stream delay.",
			position = 2,
			section = syncingSection
	)
	@Units(Units.SECONDS)
	default int syncDelay()
	{
		return 0;
	}

	@ConfigSection(
			name = "Items",
			description = "Syncing of items in inventory, equipment and bank.",
			position = 15
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

	@ConfigItem(
			keyName = "collectionLogSkipEmpty",
			name = "Skip empty collection logs",
			description = "Skip collection log pages where no items are logged yet.",
			position = 16,
			section = itemsSection
	)
	default boolean collectionLogSkipEmpty()
	{
		return false;
	}

	@ConfigSection(
			name = "Combat",
			description = "Syncing of weapon damage, smite drains, poison damage, etc. per enemy.",
			position = 14
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
			name = "Fight expiry time",
			description = "Reset a fight after the configured minutes of inactivity.",
			position = 12,
			section = combatSection
	)
	@Units(Units.MINUTES)
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
			name = "Disable tracking on Hardcores",
			description = "Disable tracking of fight stats on Hardcore Ironmen to interfere as little as possible with combat.",
			position = 16,
			section = combatSection,
			warning = "Are you sure you want to toggle fight stats to be tracked on Hardcore Ironmen?"
	)
	default boolean fightStatisticsProtectionEnabled()
	{
		return true;
	}

	@ConfigSection(
			name = "Raids",
			description = "Syncing of invocations",
			position = 16
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
			position = 16
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
			position = 17
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
			name = "Seasonals",
			description = "Syncing of seasonal stats",
			position = 18
	)
	String seasonalsSection = "seasonals";

	@ConfigItem(
			keyName = "seasonalsEnabled",
			name = "Sync seasonals",
			description = "Synchronize leagues/DMM info, such as regions and relics.",
			position = 2,
			section = seasonalsSection
	)
	default boolean seasonalsEnabled()
	{
		return true;
	}

	@ConfigSection(
			name = "General info",
			description = "Syncing of display name, player weight, etc.",
			position = 20
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
			name = "Random Events",
			description = "Settings for the Random Event triggered by donations or channel events",
			position = 6
	)
	String marketplaceSection = "donations";

	@ConfigItem(
			keyName = "marketplaceEnabled",
			name = "Enable Random Events",
			description = "Enable viewers to make donations or channel events to trigger in-game Random Events (requires Twitch Extension configuration!).",
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
			section = marketplaceSection,
			warning = "Disabling this allows Random Events that are potentially dangerous in terms of gameplay to be activated by viewers when you have them configured. Are you sure?"
	)
	default boolean marketplaceProtectionEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "marketplaceSoundsEnabled",
			name = "Enable sounds",
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
			name = "Correct transaction delay",
			description = "Base the expiry of the effects when it is received and not the transaction time, which can have delays.",
			position = 10,
			hidden = true,
			section = marketplaceSection
	)
	default boolean marketplaceStartOnLoadedAt()
	{
		return true;
	}

	@Range(
			min = 1,
			max = 50
	)
	@ConfigItem(
			keyName = "marketplaceMaxActiveProducts",
			name = "Max active amount",
			description = "How many random events can be active at once. New ones are queued when maximum is exceeded.",
			position = 12,
			hidden = false,
			section = marketplaceSection
	)
	default int marketplaceMaxActiveProducts()
	{
		return 25;
	}

	@Range(
			min = 0,
			max = 60 * 30 // half hour max?
	)
	@ConfigItem(
			keyName = "marketplaceSharedCooldownS",
			name = "Shared cooldown",
			description = "Cooldown time shared between all random events. This works together with the cooldown time per random event configured in Twitch.",
			position = 14,
			hidden = false,
			section = marketplaceSection
	)
	@Units(Units.SECONDS)
	default int marketplaceSharedCooldownS()
	{
		return 0;
	}

	@Range(
			min = 1,
			max = MarketplaceConstants.MAX_TRANSACTION_AMOUNT_IN_MEMORY
	)
	@ConfigItem(
			keyName = "marketplaceTransactionHistoryAmount",
			name = "Transaction history amount",
			description = "How many transactions are shown in the 'recent donations' panel.",
			position = 16,
			hidden = false,
			section = marketplaceSection
	)
	default int marketplaceTransactionHistoryAmount()
	{
		return MarketplaceConstants.MAX_TRANSACTION_AMOUNT_IN_MEMORY;
	}

	@ConfigItem(
			keyName = "chaosModeSpawnMultiplier",
			name = "Chaos Mode spawn multiplier",
			description = "The amount all NPC/item spawns are multiplied when Chaos Mode is enabled.",
			position = 28,
			section = marketplaceSection,
			hidden = !CHAOS_MODE_AVAILABLE
	)
	default int chaosModeSpawnMultiplier()
	{
		return 3;
	}

	@ConfigItem(
			keyName = "chaosModeRangeMultiplier",
			name = "Chaos Mode range multiplier",
			description = "The multiplier for the distance where the spawns allowed to be put. When you have a spawn multiplier increasing the range as well is recommended.",
			position = 28,
			section = marketplaceSection,
			hidden = !CHAOS_MODE_AVAILABLE
	)
	default int chaosModeRangeMultiplier()
	{
		return 2;
	}

	@Range(
			min = 5,
			max = 60 * 5
	)
	@ConfigItem(
			keyName = "testRandomEventsDuration_v2", // NOTE: different key to force update to new default
			name = "Preview Duration",
			description = "Duration of the Random Event when requested as a preview.",
			position = 30,
			section = marketplaceSection
	)
	@Units(Units.SECONDS)
	default int testRandomEventsDuration()
	{
		return 30;
	}

	@ConfigSection(
			name = "Channel Events",
			description = "Authentication and configuration for channel events, such as follows, subs, etc.",
			position = 10
	)
	String channelEventsSection = "channel-events";

	@ConfigItem(
			keyName = "marketplaceChannelEventsEnabled",
			name = "Enable Channel Events",
			description = "Enable listening to channel events, such as follows and subs to activate Random Events",
			position = 1,
			hidden = false,
			section = channelEventsSection
	)
	default boolean marketplaceChannelEventsEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = TWITCH_OAUTH_ACCESS_TOKEN_KEY,
			name = "Twitch Channel Token (optional)",
			description = "Optional token to access events such as channel point redeems, subscriptions, etc.",
			secret = true,
			position = 2,
			section = channelEventsSection
	)
	default String twitchOAuthAccessToken()
	{
		return "";
	}

	@ConfigItem(
			keyName = TWITCH_OAUTH_REFRESH_TOKEN_KEY,
			name = "Twitch Channel Refresh Token (optional)",
			description = "Optional refresh token to access events such as channel point redeems, subscriptions, etc.",
			secret = true,
			position = 4,
			section = channelEventsSection
	)
	default String twitchOAuthRefreshToken()
	{
		return "";
	}

	@ConfigSection(
			name = "Notifications",
			description = "enable and disable specific notifications along with customised colours and messages",
			position = 12,
			closedByDefault = true
	)
	String notificationsSection = "notifications";

	@ConfigItem(
			keyName = "overheadDonationMessageEnabled",
			name = "Enable overhead messages",
			description = "Allow messages as overhead text.",
			position = 4,
			section = notificationsSection
	)
	default boolean overheadMessagesEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "popupDonationMessageEnabled",
			name = "Enable pop-up messages",
			description = "Allow messages as a pop-up.",
			position = 6,
			section = notificationsSection
	)
	default boolean popupMessagesEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "chatDonationMessageEnabled",
			name = "Enable chat messages",
			description = "Allow messages as a chat message.",
			position = 8,
			section = notificationsSection
	)
	default boolean chatMessagesEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "chatMessageColor",
			name = "Chat message color",
			description = "The color in which the chat messages are shown.",
			position = 9,
			section = notificationsSection
	)
	default Color chatMessageColor()
	{
		// return new Color(145, 70, 255); // light purple
		return new Color(96, 33, 191); // dark purple
	}

	@Range(
			min = 1,
			max = 10
	)
	@ConfigItem(
			keyName = "overheadMessageDurationS",
			name = "Overhead text duration",
			description = "How long overhead notifications, such as thank you's are shown above the player.",
			position = 10,
			hidden = false,
			section = notificationsSection
	)
	@Units(Units.SECONDS)
	default int overheadMessageDurationS()
	{
		return 2;
	}

	@ConfigItem(
			keyName = "marketplaceDefaultDonationMessage", // NOTE: keep old key for migration purposes
			name = "Default bits donation message",
			description = "Default message shown when bits are donated. Use '{viewerName}', '{currencyAmount}' and '{currencyType}' to replace with values from the transaction.",
			position = 12,
			section = notificationsSection
	)
	default String defaultBitsDonationMessage()
	{
		return "Thank you {viewerName} for donating {currencyAmount} {currencyType}!";
	}

	@ConfigItem(
			keyName = "followEventMessageEnabled",
			name = "Enable follow message",
			description = "Enable follow event message",
			position = 13,
			section = notificationsSection
	)
	default boolean followEventMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "followEventMessage",
			name = "Message on follow",
			description = "Message shown when there is a new channel follower.",
			position = 14,
			section = notificationsSection
	)
	default String followEventMessage()
	{
		return "Thanks {viewerName} for following!";
	}

	@ConfigItem(
			keyName = "channelPointsRedeemEventMessageEnabled",
			name = "Enable channel point message",
			description = "Enable message shown when there is a channel point redeem.",
			position = 15,
			section = notificationsSection
	)
	default boolean channelPointsRedeemEventMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "channelPointsRedeemEventMessage",
			name = "Message on channel point redeem",
			description = "Message shown when there is a channel point redeem.",
			position = 16,
			section = notificationsSection
	)
	default String channelPointsRedeemEventMessage()
	{
		return "Thanks {viewerName} for redeeming {currencyAmount} channel points!";
	}

	@ConfigItem(
			keyName = "subscribeEventMessageEnabled",
			name = "Enable sub message",
			description = "Enable message shown when there is a new sub.",
			position = 17,
			section = notificationsSection
	)
	default boolean subscribeEventMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "subscribeEventMessageOnGiftEnabled",
			name = "Enable sub message when gifted",
			description = "Enable message when the sub is gifted.",
			position = 17,
			section = notificationsSection
	)
	default boolean subscribeEventMessageOnGiftEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "subscribeEventMessage",
			name = "Message on new sub",
			description = "Message shown when there is a new sub.",
			position = 18,
			section = notificationsSection
	)
	default String subscribeEventMessage()
	{
		return "Thanks {viewerName} for the sub!";
	}

	@ConfigItem(
			keyName = "resubscribeEventMessageEnabled",
			name = "Enable resub message",
			description = "Enable message shown when there is a resub.",
			position = 19,
			section = notificationsSection
	)
	default boolean resubscribeEventMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "resubscribeEventMessage",
			name = "Message on resub",
			description = "Message shown when there is a resub.",
			position = 20,
			section = notificationsSection
	)
	default String resubscribeEventMessage()
	{
		return "Thanks {viewerName} for the {subMonths} month resub for a total of {subTotalMonths}!";
	}

	@ConfigItem(
			keyName = "giftSubscriptionEventMessageEnabled",
			name = "Enable gift sub message",
			description = "Enable message shown when there is a gift subscription.",
			position = 21,
			section = notificationsSection
	)
	default boolean giftSubscriptionEventMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "giftSubscriptionEventMessage",
			name = "Message on gift",
			description = "Message shown when there is a gift subscription.",
			position = 22,
			section = notificationsSection
	)
	default String giftSubscriptionEventMessage()
	{
		return "Thanks {viewerName} for the {giftedAmount} gifted for a total of {giftedTotalAmount}!";
	}

	@ConfigItem(
			keyName = "raidEventMessageEnabled",
			name = "Enable raid message",
			description = "Enable message shown when there is a raid.",
			position = 23,
			section = notificationsSection
	)
	default boolean raidEventMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "raidEventMessage",
			name = "Message on raid",
			description = "Message shown when you receive a raid.",
			position = 24,
			section = notificationsSection
	)
	default String raidEventMessage()
	{
		return "Thanks {raiderChannelName} for the raid with {raidViewerAmount} viewers!";
	}

	@ConfigItem(
			keyName = "addedModMessageEnabled",
			name = "Enable add mod message",
			description = "Enable message shown a user is promoted to mod.",
			position = 25,
			section = notificationsSection
	)
	default boolean addedModMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "addedModMessage",
			name = "Message on mod added",
			description = "Message shown a user is promoted to mod.",
			position = 26,
			section = notificationsSection
	)
	default String addedModMessage()
	{
		return "Congrats {viewerName} with the promotion!";
	}

	@ConfigItem(
			keyName = "removedModMessageEnabled",
			name = "Enable remove mod message",
			description = "Enable message shown when a user is demoted from mod.",
			position = 27,
			section = notificationsSection
	)
	default boolean removedModMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "removedModMessage",
			name = "Message on mod removal",
			description = "Message shown when a user is demoted from mod.",
			position = 28,
			section = notificationsSection
	)
	default String removedModMessage()
	{
		return "No mod for you anymore, {viewerName}!";
	}

	@ConfigItem(
			keyName = "beginHypeTrainMessageEnabled",
			name = "Enable hype train begin message",
			description = "Enable message when a hype train begins.",
			position = 29,
			section = notificationsSection
	)
	default boolean beginHypeTrainMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "beginHypeTrainMessage",
			name = "Message on hype train begin",
			description = "Message when a hype train begins",
			position = 30,
			section = notificationsSection
	)
	default String beginHypeTrainMessage()
	{
		return "Thanks all for starting the level {hypeTrainLevel} hype train!";
	}

	@ConfigItem(
			keyName = "progressHypeTrainMessageEnabled",
			name = "Enable hype train progression message",
			description = "Enable message when a hype train progresses in percentage or level.",
			position = 31,
			section = notificationsSection
	)
	default boolean progressHypeTrainMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "progressHypeTrainMessage",
			name = "Message on hype train progression",
			description = "Message when a hype train progresses in percentage or level.",
			position = 32,
			section = notificationsSection
	)
	default String progressHypeTrainMessage()
	{
		return "Thanks all for contributing to the level {hypeTrainLevel} hype train!";
	}

	@ConfigItem(
			keyName = "endHypeTrainMessageEnabled",
			name = "Enable hype train end message",
			description = "Enable message when a hype train ends.",
			position = 33,
			section = notificationsSection
	)
	default boolean endHypeTrainMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "endHypeTrainMessage",
			name = "Message on hype train end",
			description = "Message when a hype train ends.",
			position = 34,
			section = notificationsSection
	)
	default String endHypeTrainMessage()
	{
		return "Thanks all for the level {hypeTrainLevel} hype train!";
	}

	@ConfigItem(
			keyName = "donateCharityCampaignMessageEnabled",
			name = "Enable charity donation message",
			description = "Enable message when a charity campaign receives a donation.",
			position = 35,
			section = notificationsSection
	)
	default boolean donateCharityCampaignMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "donateCharityCampaignMessage",
			name = "Message on charity donation",
			description = "Message when a charity campaign receives a donation.",
			position = 36,
			section = notificationsSection
	)
	default String donateCharityCampaignMessage()
	{
		return "Thanks {viewerName} for donating {currencyAmount} {currencyType} to {charityName}!";
	}

	@ConfigItem(
			keyName = "startCharityCampaignMessageEnabled",
			name = "Enable charity start message",
			description = "Enable message when a charity campaign starts.",
			position = 37,
			section = notificationsSection
	)
	default boolean startCharityCampaignMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "startCharityCampaignMessage",
			name = "Message on charity start",
			description = "Message when a charity campaign starts",
			position = 38,
			section = notificationsSection
	)
	default String startCharityCampaignMessage()
	{
		return "Welcome to the charity campaign for {charityName} with a goal of {charityTargetAmount} {charityTargetCurrency}!";
	}

	@ConfigItem(
			keyName = "progressCharityCampaignMessageEnabled",
			name = "Enable charity progression message",
			description = "Enable message when a charity progresses in amount donated.",
			position = 39,
			section = notificationsSection
	)
	default boolean progressCharityCampaignMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "progressCharityCampaignMessage",
			name = "Message on charity progression",
			description = "Message when a charity progresses in amount donated.",
			position = 40,
			section = notificationsSection
	)
	default String progressCharityCampaignMessage()
	{
		return "Thanks all for donating {charityCurrentAmount} {charityCurrentCurrency} up until now towards our goal of {charityTargetAmount} {charityTargetCurrency}!";
	}

	@ConfigItem(
			keyName = "stopCharityCampaignMessageEnabled",
			name = "Enable charity end message",
			description = "Enable message when a charity campaign ends.",
			position = 41,
			section = notificationsSection
	)
	default boolean stopCharityCampaignMessageEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "stopCharityCampaignMessage",
			name = "Message on charity end",
			description = "Message when a charity campaign ends.",
			position = 42,
			section = notificationsSection
	)
	default String stopCharityCampaignMessage()
	{
		return "Thanks all for donating {charityCurrentAmount} {charityCurrentCurrency} to the charity! It's a wrap!";
	}

	@ConfigSection(
			name = "Advanced",
			description = "Settings for advanced usage",
			position = 99
	)
	String advancedSection = "advanced";

	@ConfigItem(
			keyName = "supportDebugEnabled",
			name = "Support debug mode",
			description = "Enable more extensive logging for support purposes.",
			position = 0,
			section = advancedSection
	)
	default boolean supportDebugEnabled()
	{
		return false;
	}

	@ConfigItem(
			keyName = "twitchReviewModeEnabled",
			name = "Twitch review mode",
			description = "Force syncing of data while being logged out, meant for the Twitch review process.",
			position = 2,
			hidden = !TwitchLiveLoadoutPlugin.IN_DEVELOPMENT,
			section = advancedSection
	)
	default boolean twitchReviewModeEnabled()
	{
		return false;
	}

	@ConfigItem(
			keyName = "debugMenuOptionClicks",
			name = "Debug menu clicks",
			description = "Debug menu option click events.",
			position = 4,
			hidden = !TwitchLiveLoadoutPlugin.IN_DEVELOPMENT,
			section = advancedSection
	)
	default boolean debugMenuOptionClicks()
	{
		return false;
	}

	@ConfigItem(
			keyName = "testRandomEvents",
			name = "Test Random Events",
			description = "Periodically spawn a random event and move to the next one.",
			position = 6,
			hidden = !TwitchLiveLoadoutPlugin.IN_DEVELOPMENT,
			section = advancedSection
	)
	default boolean testRandomEventsEnabled()
	{
		return false;
	}

	@ConfigItem(
			keyName = "testRandomEventsRandomly",
			name = "Test Randomly",
			description = "True when selecting randomly instead of cyclic.",
			position = 8,
			hidden = !TwitchLiveLoadoutPlugin.IN_DEVELOPMENT,
			section = advancedSection
	)
	default boolean testRandomEventsRandomly()
	{
		return false;
	}

	@ConfigItem(
			keyName = "testRandomEventsDelay",
			name = "Test Delay",
			description = "The amount of time before the next Random Event is tested.",
			position = 12,
			hidden = !TwitchLiveLoadoutPlugin.IN_DEVELOPMENT,
			section = advancedSection
	)
	@Units(Units.SECONDS)
	default int testRandomEventsDelay()
	{
		return 5;
	}
}
