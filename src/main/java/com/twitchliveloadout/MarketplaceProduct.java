package com.twitchliveloadout;

import lombok.Getter;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;

public enum MarketplaceProduct {
	GRAVESTONE(41280),
	GOLDEN_GNOME(32303),
	COIN_TROPHY(32153),
	TOB_LOOT_CHEST(35425, (model, object) -> {
		makeSmall(model);
	}), // 35448, 35425, monumental chest
	BITS_TROPHY(35449, 8105, -1, -1),
	OPEN_CHEST_GOLD(35451),
	PARTY_BALLOONS(2227, 498, -1, -1),
	ORNATE_RS_BANNER(41694), // pedestal: 41692 (cube) / 41693 (round)
	GARDENING_BANNER(11937),

	SWORD_IN_STONE(25185),
	TOB_WEAPON_RACK(35436),
	INFERNAL_CAPE(33143),
	MAX_CAPE(32188),
	ANIMATED_ARMOUR(21262, 5603, -1, -1),
	JUSTICIAR_ARMOUR(35412), // 35426
	RECOLORABLE_ARMOUR(25069), // recolor 6067 to 50504
	ZUK_DISPLAY(34570),
	VERZIK_THRONE(35338),
	DEMONIC_THRONE(12592),
	GOLD_THRONE(12594),
	CRYSTAL_THRONE(12590),
	BIG_MYTHIC_STATUE(34377),
	SARADOMIN_STATUE(1488),
	ZAMORAK_STATUE(1400), // new version: 35277
	GUTHIX_STATUE(3662),
	KING_STATUE(1527),

	TECH_SPAWNING_PORTAL(43526, 9335, -1, -1),
	VERTICAL_SPAWNING_PORTAL(37828, 8456, -1, -1),
	GROUND_SPAWNING_PORTAL(42302, 9040, -1, -1),

	// spawning portal: 43526, with anim: 9335
	// spawning portal 2: 37828, with anim: 8456
	// spawning portal on ground: 42302, with anim: 9040

	DARK_CRYSTAL(30698),
	SMALL_FIRE(2260, 475, -1, -1),
	BIG_SMOKE(21817, 5745, -1, -1),
	FIRE_ENERGY(44057, 9400, -1, -1),
	SKELETON(1078), // alt: 1081 backstabbed one, blood stain: 1082
	DECAPITATED_TROLL(22146),
	BLACK_CAT(3006, 317, -1, -1), // body of cat: 3010
	MUSHROOM(14705),
	FISH_TROPHY(18254),
	PUFFER_FISH(33420),
	FISH_BARREL(17730, -1, -1, -1);

	// 28914; // golden gnome: 32303, scythe: 40614, gravestone: 41280 / 40493 / 38055 / 31619 /
	@Getter
	private final int objectModelId;
	@Getter
	private final int objectAnimationId;
	@Getter
	private final int playerGraphicId;
	@Getter
	private final int playerAnimationId;
	@Getter
	private final CustomizeAction customizeAction;

	MarketplaceProduct(int objectModelId, int objectAnimationId, int playerGraphicId, int playerAnimationId, CustomizeAction customizeAction)
	{
		this.objectModelId = objectModelId;
		this.objectAnimationId = objectAnimationId;
		this.playerGraphicId = playerGraphicId;
		this.playerAnimationId = playerAnimationId;
		this.customizeAction = customizeAction;
	}

	MarketplaceProduct(int objectModelId, int objectAnimationId, int playerGraphicId, int playerAnimationId)
	{
		this(objectModelId, objectAnimationId, playerGraphicId, playerAnimationId, null);
	}

	MarketplaceProduct(int objectModelId, int objectAnimationId)
	{
		this(objectModelId, objectAnimationId, -1, -1, null);
	}

	MarketplaceProduct(int objectModelId)
	{
		this(objectModelId, -1, -1, -1, null);
	}

	MarketplaceProduct(int objectModelId, CustomizeAction customizeAction)
	{
		this(objectModelId, -1, -1, -1, customizeAction);
	}

	public static void makeSmall(ModelData model)
	{
		model.scale(50, 50, 50);
	}

	public interface CustomizeAction {
		public void execute(ModelData model, RuneLiteObject object);
	}
}
