package com.twitchliveloadout;

public enum MarketplaceProduct {
	GRAVESTONE(41280, -1, -1, -1),
	GOLDEN_GNOME(28914, -1, -1, -1),
	COIN_TROPHY(32153, -1, -1, -1),
	TOB_LOOT_CHEST(35425, -1, -1, -1), // 35448, 35425, monumental chest
	BITS_TROPHY(35449, 8105, -1, -1),
	OPEN_CHEST_GOLD(35451, -1, -1, -1),
	PARTY_BALLOONS(2227, 498, -1, -1),
	ORNATE_RS_BANNER(41694, -1, -1, -1), // pedestal: 41692 (cube) / 41693 (round)
	GARDENING_BANNER(11937, -1, -1, -1),

	SWORD_IN_STONE(25185, -1, -1, -1),
	TOB_WEAPON_RACK(35436, -1, -1, -1),
	INFERNAL_CAPE(33143, -1, -1, -1),
	MAX_CAPE(32188, -1, -1, -1),
	ANIMATED_ARMOUR(21262, 5603, -1, -1),
	JUSTICIAR_ARMOUR(35412, -1, -1, -1), // 35426
	RECOLORABLE_ARMOUR(25069, -1, -1, -1), // recolor 6067 to 50504
	ZUK_DISPLAY(34570, -1, -1, -1),
	VERZIK_THRONE(35338, -1, -1, -1),
	DEMONIC_THRONE(12592, -1, -1, -1),
	GOLD_THRONE(12594, -1, -1, -1),
	CRYSTAL_THRONE(12590, -1, -1, -1),
	BIG_MYTHIC_STATUE(34377, -1, -1, -1),
	SARADOMIN_STATUE(1488, -1, -1, -1),
	ZAMORAK_STATUE(1400, -1, -1, -1), // new version: 35277
	GUTHIX_STATUE(3662, -1, -1, -1),
	KING_STATUE(1527, -1, -1, -1),

	TECH_SPAWNING_PORTAL(43526, 9335, -1, -1),
	VERTICAL_SPAWNING_PORTAL(37828, 8456, -1, -1),
	GROUND_SPAWNING_PORTAL(42302, 9040, -1, -1),

	// spawning portal: 43526, with anim: 9335
	// spawning portal 2: 37828, with anim: 8456
	// spawning portal on ground: 42302, with anim: 9040

	DARK_CRYSTAL(30698, -1, -1, -1),
	SMALL_FIRE(2260, 475, -1, -1),
	BIG_SMOKE(21817, 5745, -1, -1),
	FIRE_ENERGY(44057, 9400, -1, -1),
	SKELETON(1078, -1, -1, -1), // alt: 1081 backstabbed one, blood stain: 1082
	DECAPITATED_TROLL(22146, -1, -1, -1),
	BLACK_CAT(3006, 317, -1, -1), // body of cat: 3010
	MUSHROOM(14705, -1, -1, -1),
	FISH_TROPHY(18254, -1, -1, -1),
	PUFFER_FISH(33420, -1, -1, -1),
	FISH_BARREL(17730, -1, -1, -1);

	// 28914; // golden gnome: 32303, scythe: 40614, gravestone: 41280 / 40493 / 38055 / 31619 /

	private final int objectModelId;
	private final int playerGraphicId;
	private final int playerAnimationId;

	MarketplaceProduct(int objectModelId, int objectAnimationId, int playerGraphicId, int playerAnimationId)
	{
		this.objectModelId = objectModelId;
		this.playerGraphicId = playerGraphicId;
		this.playerAnimationId = playerAnimationId;
	}

	public int getObjectModelId()
	{
		return objectModelId;
	}

	public int getPlayerGraphicId()
	{
		return playerGraphicId;
	}

	public int getPlayerAnimationId()
	{
		return playerAnimationId;
	}
}
