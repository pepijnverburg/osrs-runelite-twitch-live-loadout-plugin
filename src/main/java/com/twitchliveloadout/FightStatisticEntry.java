package net.runelite.client.plugins.twitchliveloadout;

public enum FightStatisticEntry {
	SHARED("shared", true),
//	MELEE("melee", true),
//	MAGIC("magic", true),
//	RANGED("ranged", true),

	FREEZE("freeze", true),
	ENTANGLE("entangle", true),
	BLOOD_HEAL("bloodHeal", true),
	SPELL("spell", true),
	POISON("poison", false),
//	VENGEANCE("vengeance", true),
//	FOOD_HEAL("foodHeal", false),
//	SPECIAL_ATTACK("specialAttack", true),

//	PRAYER("prayer", false),
//	RANGED_PRAYER("rangedPrayer", false),
//	MELEE_PRAYER("meleePrayer", false),
//	MAGIC_PRAYER("magicPrayer", false),
	SMITE("smite", false);

	private final String key;
	private final boolean durationInfluencer;

	FightStatisticEntry(String key, boolean durationInfluencer)
	{
		this.key = key;
		this.durationInfluencer = durationInfluencer;
	}

	public String getKey()
	{
		return key;
	}

	public boolean isDurationInfluencer()
	{
		return durationInfluencer;
	}
}
