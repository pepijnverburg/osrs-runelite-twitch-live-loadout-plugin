package com.twitchliveloadout.marketplace;

import java.util.ArrayList;

public class EbsProduct {
	public String id;
	public Boolean enabled;
	public String category;
	public String name;
	public String description;
	public Behaviour behaviour;

	public class Behaviour {
		public String interfaceEffectType;
		public EbsProductInterval interfaceEffectInterval;
		public EbsProductMovementAnimations playerAnimations;
		public PlayerEquipment playerEquipment;
		public EbsProductInterval spawnInterval;
		public ArrayList<SpawnOption> spawnOptions;
	}

	public class ModelSet {
		public ArrayList<Integer> modelIds;
		public EbsProductRandomRange modelScale;
		public String modelRotationType;
		public EbsProductRandomRange modelRotation;
	}

	public class PlayerEquipment {
		public Integer amuletItemId;
		public Integer bootsItemId;
		public Integer chestItemId;
		public Integer glovesItemId;
		public Integer helmItemId;
		public Integer legsItemId;
		public Integer shieldItemId;
		public Integer weaponItemId;
	}

	public class Spawn {
		public ArrayList<ModelSet> modelSets;
		public Boolean spawnerEnabled;
		public ModelSet spawner;
		public EbsProductRandomRange spawnerDurationMs;
		public EbsModelPlacement modelPlacement;
		public Animation hideAnimation;
		public Animation showAnimation;
		public EbsProductMovementAnimations movementAnimations;
		public ArrayList<Animation> randomAnimations;
		public EbsProductInterval randomAnimationInterval;
	}

	public class Animation {
		public EbsProductAnimationFrame modelAnimation;
		public EbsProductAnimationFrame playerAnimation;
		public EbsProductAnimationFrame playerGraphic;
	}

	public class SpawnOption {
		public Double chance;
		public EbsProductRandomRange spawnAmount;
		public EbsProductRandomRange spawnDelayMs;
		public ArrayList<Spawn> spawns;
	}
}


