package com.twitchliveloadout.twitch;

import lombok.Getter;

public enum TwitchStateEntry {
	GAME_CLIENT_TYPE("gameClientType"),
	CONTENT_ID("contentId"),
	CONNECTION_STATUS("connectionStatus"),
	ACCOUNT_HASH("accountHash"),
	ACCOUNT_TYPE("accountType"),
	PLAYER_NAME("playerName"),
	REGION_ID("regionId"),
	INVENTORY_ITEMS("inventory", true),
	INVENTORY_PRICE("inventoryPrice", true),
	EQUIPMENT_ITEMS("equipment", true),
	EQUIPMENT_PRICE("equipmentPrice", true),
	LOOTING_BAG_ITEMS("lootingBag", true),
	LOOTING_BAG_PRICE("lootingBagPrice", true),
	GROUP_STORAGE_ITEMS("groupStorage", true),
	GROUP_STORAGE_PRICE("groupStoragePrice", true),
	DMM_DEPOSIT_BOX_ITEMS("dmmDepositBox", true),
	DMM_DEPOSIT_BOX_PRICE("dmmDepositBoxPrice", true),
	FIGHT_STATISTICS("fightStatistics", true),
	SKILL_EXPERIENCES("skillExperiences", true),
	BOOSTED_SKILL_LEVELS("boostedSkillLevels", true),
	VIRTUAL_LEVELS_ENABLED("virtualLevelsEnabled"),
	WEIGHT("weight", true),
	ITEM_GOALS("itemGoals", true),
	TOP_POSITION("topPosition"),
	THEME_TYPE("themeType"),
	VISIBILITY_TYPE("visibilityType"),
	BANK_TABBED_ITEMS("bankTabbedItems", true),
	BANK_PRICE("bankPrice", true),
	COLLECTION_LOG("collectionLog", true),
	COLLECTION_LOG_OBTAINED_AMOUNT("collectionLogObtainedAmount", true),
	COLLECTION_LOG_OBTAINABLE_AMOUNT("collectionLogObtainableAmount", true),
	INVOCATIONS("invocations", true),
	INVOCATIONS_RAID_LEVEL("invocationsRaidLevel", true),
	MARKETPLACE_ENABLED("marketplaceEnabled", true),
	MARKETPLACE_ACTIVE("marketplaceActive"),
	MARKETPLACE_CHANNEL_EVENTS_ACTIVE("marketplaceChannelEventsActive"),
	MARKETPLACE_TEST_MODE_ACTIVE("marketplaceTestModeActive"),
	MARKETPLACE_FREE_MODE_ACTIVE("marketplaceFreeModeActive"),
	MARKETPLACE_CHAOS_MODE_ACTIVE("marketplaceChaosModeActive"),
	MARKETPLACE_PROTECTION_ENABLED("marketplaceProtectionEnabled"),
	CURRENT_PRODUCT_COOLDOWNS("currentProductCooldowns", true),
	CURRENT_SHARED_COOLDOWN("currentSharedCooldown"),
	SHARED_COOLDOWN("sharedCooldown"),
	QUESTS("quests", true),
	COMBAT_ACHIEVEMENTS("combatAchievements", true),
	COMBAT_ACHIEVEMENT_PROGRESS("combatAchievementsProgress", true),
	SEASONAL_ITEMS("seasonalItems", true),
	STREAMER_PRODUCTS("streamerProducts"),
	CHANNEL_POINT_REWARDS("channelPointRewards"),
	;

	@Getter
	private final String key;
	@Getter
	private final boolean nullable;

	TwitchStateEntry(String key) {
		this.key = key;
		this.nullable = false;
	}

	TwitchStateEntry(String key, boolean nullable) {
		this.key = key;
		this.nullable = nullable;
	}
}
