package com.twitchliveloadout.twitch;

import lombok.Getter;

public enum TwitchStateEntry {
	CONTENT_ID("contentId"),
	CONNECTION_STATUS("connectionStatus"),
	ACCOUNT_HASH("accountHash"),
	ACCOUNT_TYPE("accountType"),
	PLAYER_NAME("playerName"),
	INVENTORY_ITEMS("inventory", true),
	INVENTORY_PRICE("inventoryPrice", true),
	EQUIPMENT_ITEMS("equipment", true),
	EQUIPMENT_PRICE("equipmentPrice", true),
	LOOTING_BAG_ITEMS("lootingBag", true),
	LOOTING_BAG_PRICE("lootingBagPrice", true),
	FIGHT_STATISTICS("fightStatistics", true),
	SKILL_EXPERIENCES("skillExperiences", true),
	BOOSTED_SKILL_LEVELS("boostedSkillLevels", true),
	VIRTUAL_LEVELS_ENABLED("virtualLevelsEnabled"),
	WEIGHT("weight", true),
	ITEM_GOALS("itemGoals", true),
	TOP_POSITION("topPosition"),
	THEME_TYPE("themeType"),
	BANK_TABBED_ITEMS("bankTabbedItems", true),
	BANK_PRICE("bankPrice", true),
	COLLECTION_LOG("collectionLog", true),
	INVOCATIONS("invocations", true),
	INVOCATIONS_RAID_LEVEL("invocationsRaidLevel", true),
	MARKETPLACE_ENABLED("marketplaceEnabled", true),
	MARKETPLACE_ACTIVE("marketplaceActive"),
	MARKETPLACE_PROTECTION_ENABLED("marketplaceProtectionEnabled"),
	CURRENT_PRODUCT_COOLDOWNS("currentProductCooldowns", true),
	CURRENT_SHARED_COOLDOWN("currentSharedCooldown"),
	SHARED_COOLDOWN("sharedCooldown"),
	QUESTS("quests", true),

	STREAMER_PRODUCTS("streamerProducts"),
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
