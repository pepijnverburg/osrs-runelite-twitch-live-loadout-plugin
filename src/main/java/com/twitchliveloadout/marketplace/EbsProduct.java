package com.twitchliveloadout.marketplace;

import java.util.ArrayList;

public class EbsProduct {
	public String id;
	public Boolean enabled;
	public String type;
	public String name;
	public String description;
	public Behaviour behaviour;

	public class Behaviour {
		public String interfaceEffectType;
		public EbsProductInterval interfaceEffectInterval;
		public PlayerAnimation playerAnimation;
		public PlayerEquipment playerEquipment;
		public EbsProductInterval spawnBehaviourInterval;
		public ArrayList<SpawnBehaviourOption> spawnBehaviourOptions;
	}

	public class Model {
		public ArrayList<Integer> modelIds;
		public String modelRotationType;
		public Double modelScale;
	}

	public class PlayerAnimation {
		public Integer idleAnimationId;
		public Integer runAnimationId;
		public Integer walkAnimationId;
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

	public class SpawnBehaviour {
		public ArrayList<Model> models;
		public EbsModelPlacement modelPlacement;
		public Animation hideAnimation;
		public Animation showAnimation;
		public ArrayList<Animation> randomAnimations;
		public EbsProductInterval randomAnimationInterval;
	}

	public class Animation {
		public AnimationFrame modelAnimation;
		public AnimationFrame playerAnimation;
		public AnimationFrame playerGraphic;
	}

	public class AnimationFrame {
		public Integer id;
		public Integer delayMs;
		public Integer durationMs;
	}

	public class SpawnBehaviourOption {
		public Double chance;
		public Integer spawnAmountMin;
		public Integer spawnAmountMax;
		public ArrayList<SpawnBehaviour> spawnBehaviours;
	}
}


