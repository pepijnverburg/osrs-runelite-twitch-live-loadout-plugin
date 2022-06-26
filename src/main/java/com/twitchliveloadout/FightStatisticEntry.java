package com.twitchliveloadout;

import lombok.Getter;

public enum FightStatisticEntry {
	TOTAL("total", true, true),
//	MELEE("melee", true, true),
//	MAGIC("magic", true, true),
//	RANGED("ranged", true, true),
	OTHER("other", true, false),

	FREEZE("freeze", true, true),
	ENTANGLE("entangle", true, true),
	HIT_HEAL("hitHeal", true, true),
	BLOOD_HEAL("bloodHeal", true, true),
	SPELL("spell", true, true),
	POISON("poison", false, true),
//	VENGEANCE("vengeance", true, true),
//	FOOD_HEAL("foodHeal", false, true),
//	SPECIAL_ATTACK("specialAttack", true, true),

//	PRAYER("prayer", false, true),
//	RANGED_PRAYER("rangedPrayer", false, true),
//	MELEE_PRAYER("meleePrayer", false, true),
//	MAGIC_PRAYER("magicPrayer", false, true),
	SMITE("smite", false, true);

	@Getter
	private final String key;

	@Getter
	private final boolean durationInfluencer;

	@Getter
	private final boolean updatedAtInfluencer;

	FightStatisticEntry(String key, boolean durationInfluencer, boolean updatedAtInfluencer)
	{
		this.key = key;
		this.durationInfluencer = durationInfluencer;
		this.updatedAtInfluencer = updatedAtInfluencer;
	}
}
