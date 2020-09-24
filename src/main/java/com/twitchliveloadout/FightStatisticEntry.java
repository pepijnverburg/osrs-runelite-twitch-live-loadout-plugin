package net.runelite.client.plugins.twitchliveloadout;

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

	private final String key;
	private final boolean durationInfluencer;
	private final boolean updatedAtInfluencer;

	FightStatisticEntry(String key, boolean durationInfluencer, boolean updatedAtInfluencer)
	{
		this.key = key;
		this.durationInfluencer = durationInfluencer;
		this.updatedAtInfluencer = updatedAtInfluencer;
	}

	public String getKey()
	{
		return key;
	}

	public boolean isDurationInfluencer()
	{
		return durationInfluencer;
	}

	public boolean isUpdatedAtInfluencer()
	{
		return updatedAtInfluencer;
	}
}
