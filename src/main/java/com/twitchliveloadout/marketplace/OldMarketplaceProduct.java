package com.twitchliveloadout.marketplace;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

import java.awt.Color;
import java.util.Collection;
import java.util.HashMap;

@Slf4j
public enum OldMarketplaceProduct {
//	NONE(0, new MarketplaceModel[][] {}),
//	GRAVESTONE(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {
//		{new MarketplaceModel(1367)}, // old stone
//		{new MarketplaceModel(1368)}, // old stone
//		{new MarketplaceModel(1080)}, // skeleton
//		//new MarketplaceModel(41280) // modern stone
//	}),
//	FIRE(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {{
//		new MarketplaceModel(26585, 6853),
//	}}, (model, modelId) -> {
//		resizeRandomly(model, 80, 110);
//	}, SpawnPointFactory.createDefaultOutwardSpawner(2), (product) -> {
//		product.setPlayerGraphicId(1191);
//		product.setUseSpawners(false);
//	}),
//	COX_LOOT_BEAM(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {
//		{Models.COX_LOOT_BEAM, new MarketplaceModel(32799)}, // twisted bow
//		{Models.COX_LOOT_BEAM, new MarketplaceModel(32784)}, // claws
//		{Models.COX_LOOT_BEAM, new MarketplaceModel(32792)}, // elder maul
//		{Models.COX_LOOT_BEAM, new MarketplaceModel(32794)}, // ancestral hat
//		{Models.COX_LOOT_BEAM, new MarketplaceModel(32790)}, // ancestral top
//		{Models.COX_LOOT_BEAM, new MarketplaceModel(32787)}, // ancestral bottom
//		{Models.COX_LOOT_BEAM, new MarketplaceModel(32770)}, // dex
//		{Models.COX_LOOT_BEAM, new MarketplaceModel(32770)}, // arcane
//		{Models.COX_LOOT_BEAM, new MarketplaceModel(32793)}, // buckler
//		{Models.COX_LOOT_BEAM, new MarketplaceModel(32797)}, // dhcb
//		{Models.COX_LOOT_BEAM, new MarketplaceModel(32805)}, // kodai
//		//{Models.COX_LOOT_BEAM, new MarketplaceModel(ModelIds.OLMLET, 7396)}, // olm pet, bugged anim
//	}, (model, modelId) -> {
//		if (modelId == ModelIds.COX_LOOT_BEAM) {
//			recolorAllFaces(model, ModelColors.PURPLE, 1.0d);
//		}
//	}),
//	TOB_LOOT_CHEST(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {{
//		new MarketplaceModel(35425), // 35448, 35425, monumental chest
//	}}, (model, modelId) -> {
//		resizeSmall(model);
//	}),
//	PARTY_BALLOONS(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {
//		{new MarketplaceModel(2226, AnimationIds.PARTY_BALLOON, AnimationDurations.PARTY_BALLOON)},
//		{new MarketplaceModel(2227, AnimationIds.PARTY_BALLOON, AnimationDurations.PARTY_BALLOON)},
//		{new MarketplaceModel(2228, AnimationIds.PARTY_BALLOON, AnimationDurations.PARTY_BALLOON)},
//	}, (model, modelId) -> {
//		double brightness = 1.0d;
//		if (Math.random() < 0.2) {
//			recolorAllFaces(model, ModelColors.PURPLE, brightness);
//		}
//	}, SpawnPointFactory.createDefaultOutwardSpawner(5), (product) -> {
//		product.setUseSpawners(false);
//		product.setRandomSpawnDelayMs(2000);
//	}),
//	PK_LOOT(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {
//		{new MarketplaceModel(5412)}, // whip
//		{new MarketplaceModel(28075)}, // ags
//	}, null, null, (product) -> {
//		product.setUseSpawners(false);
//	}),
//	CRAB(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {
//		{new MarketplaceModel(13799, 3424)}, // crab
//		// 3428 = attack animation, 3429 = defend animation, 3430 = death animation
//	}),
//
//	GOLDEN_GNOME(ProductExpiryTimes.LONG, new MarketplaceModel[][] {{
//		new MarketplaceModel(32303),
//	}}),
//	COIN_TROPHY(ProductExpiryTimes.LONG, new MarketplaceModel[][] {{
//		new MarketplaceModel(32153),
//	}}),
//	ARMADYL_GODSWORD(ProductExpiryTimes.LONG, new MarketplaceModel[][] {{
//		new MarketplaceModel(28075),
//	}}, null, null, (product) -> {
//		product.setUseSpawners(false);
//	}),
//	ABYSSAL_WHIP(ProductExpiryTimes.LONG, new MarketplaceModel[][] {{
//		new MarketplaceModel(5412),
//	}}, null, null, (product) -> {
//		product.setUseSpawners(false);
//	}),
//	TWISTED_BOW(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {{
//		new MarketplaceModel(32799),
//	}}, null, null, (product) -> {
//		product.setUseSpawners(false);
//	}),
//	BITS_TROPHY(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {{
//		new MarketplaceModel(35449, 8105),
//	}}),
//	INFERNAL_CAPE(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {{
//		new MarketplaceModel(33143),
//	}}),
//	MAX_CAPE(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {{
//		new MarketplaceModel(32188),
//	}}),
//	ANIMATED_ARMOUR(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {{
//		new MarketplaceModel(21262, 5603),
//	}}),
//	JUSTICIAR_ARMOUR(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {{
//		new MarketplaceModel(35412), // new style: 35426
//	}}),
//	ZUK_DISPLAY(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {{
//			new MarketplaceModel(34570),
//	}}, (model, modelId) -> {
//		resizeSmall(model);
//	}),
//
//	GROUND_SPAWNING_PORTAL(ProductExpiryTimes.SHORT, new MarketplaceModel[][] {
//		{new MarketplaceModel(42302, 9040)}
//	}, (model, modelId) -> {
//		recolorAllFaces(model, ModelColors.PURPLE, 1.0d);
//	});

	// TODO: spade and bones, more NPCS, PK loot is always same direction, rope,
	// messing with PKers:
	// randomize location of spec bar
	// randomize inventory locations
	// enemy armour switcher for 30 seconds
	// 'special attack weapon becomes a shark'
	// 'drunk effect' on screen
	// other walking animation

//	GOLDEN_GNOME(new int[] {32303}),
//	COIN_TROPHY(new int[] {32153}),
//	BITS_TROPHY(new int[] {35449}, 8105, -1, -1),
//	OPEN_CHEST_GOLD(new int[] {35451}),
//	PARTY_BALLOONS(new int[] {2227}, 498, -1, -1),
//	ORNATE_RS_BANNER(new int[] {41694}), // pedestal: 41692 (cube) / 41693 (round)
//	GARDENING_BANNER(new int[] {11937}),
//
//	SWORD_IN_STONE(new int[] {25185}),
//	TOB_WEAPON_RACK(new int[] {35436}),
//	INFERNAL_CAPE(new int[] {33143}),
//	MAX_CAPE(new int[] {32188}),
//	ANIMATED_ARMOUR(new int[] {21262}, 5603, -1, -1),
//	JUSTICIAR_ARMOUR(new int[] {35412}), // 35426
//	RECOLORABLE_ARMOUR(new int[] {25069}), // recolor 6067 to 50504
//	ZUK_DISPLAY(new int[] {34570}),
//	VERZIK_THRONE(new int[] {35338}),
//	DEMONIC_THRONE(new int[] {12592}),
//	GOLD_THRONE(new int[] {12594}),
//	CRYSTAL_THRONE(new int[] {12590}),
//	BIG_MYTHIC_STATUE(new int[] {34377}),
//	SARADOMIN_STATUE(new int[] {1488}),
//	ZAMORAK_STATUE(new int[] {1400}), // new version: 35277
//	GUTHIX_STATUE(new int[] {3662}),
//	KING_STATUE(new int[] {1527}),
//
//	TECH_SPAWNING_PORTAL(new int[] {43526}, 9335, -1, -1),
//	VERTICAL_SPAWNING_PORTAL(new int[] {37828}, 8456, -1, -1),
//	GROUND_SPAWNING_PORTAL(new int[] {42302}, 9040, (model, object) -> {
//		recolorAllFaces(model, 145, 70, 255, 1.0d);
//	}),
//
//	// spawning portal: 43526, with anim: 9335
//	// spawning portal 2: 37828, with anim: 8456
//	// spawning portal on ground: 42302, with anim: 9040
//
//	DARK_CRYSTAL(new int[] {30698}),
//	SMALL_FIRE(new int[] {2260}, 475, -1, -1),
//	BIG_SMOKE(new int[] {21817}, 5745, -1, -1),
//	FIRE_ENERGY(new int[] {44057}, 9400, -1, -1),
//	SKELETON(new int[] {1081, 1082}), // 1078, alt: 1081 backstabbed one, blood stain: 1082
//	DECAPITATED_TROLL(new int[] {22146}),
//	BLACK_CAT(new int[] {3006}, 317, -1, -1), // body of cat: 3010
//	MUSHROOM(new int[] {14705}),
//	FISH_TROPHY(new int[] {18254}),
//	PUFFER_FISH(new int[] {33420}),
//	FROG_SPIT(new int[] {23353, 23354}, 6039),
//	FISH_BARREL(new int[] {17730}, -1, -1, -1);

	// 28914; // golden gnome: 32303, scythe: 40614, gravestone: 41280 / 40493 / 38055 / 31619 /

}
