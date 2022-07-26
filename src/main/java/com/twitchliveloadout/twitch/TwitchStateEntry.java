package com.twitchliveloadout.twitch;

public enum TwitchStateEntry {
	PLAYER_NAME("playerName"),
	INVENTORY_ITEMS("inventory"),
	INVENTORY_PRICE("inventoryPrice"),
	EQUIPMENT_ITEMS("equipment"),
	EQUIPMENT_PRICE("equipmentPrice"),
	LOOTING_BAG_ITEMS("lootingBag"),
	LOOTING_BAG_PRICE("lootingBagPrice"),
	FIGHT_STATISTICS("fightStatistics"),
	SKILL_EXPERIENCES("skillExperiences"),
	BOOSTED_SKILL_LEVELS("boostedSkillLevels"),
	VIRTUAL_LEVELS_ENABLED("virtualLevelsEnabled"),
	WEIGHT("weight"),
	ITEM_GOALS("itemGoals"),
	TOP_POSITION("topPosition"),
	BANK_TABBED_ITEMS("bankTabbedItems"),
	BANK_PRICE("bankPrice"),
	COLLECTION_LOG("collectionLog");

	private final String key;

	TwitchStateEntry(String key) {
		this.key = key;
	}

	public String getKey()
	{
		return key;
	}
}