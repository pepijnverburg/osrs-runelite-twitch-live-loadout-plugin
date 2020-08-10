package net.runelite.client.plugins.twitchliveloadout;

public enum FightStatisticEntry {
	SHARED("shared"),
//	MELEE("melee"),
//	MAGIC("magic"),
//	RANGED("ranged"),

//	FREEZE("freeze"),
	SPLASH("splash"),
//	SPECIAL_ATTACK("specialAttack"),
	POISON("poison"),
//	VENGEANCE("vengeance"),
//	HEAL("heal"),

//	PRAYER("prayer"),
//	RANGED_PRAYER("rangedPrayer"),
//	MELEE_PRAYER("meleePrayer"),
//	MAGIC_PRAYER("magicPrayer"),
	SMITE("smite");

	private final String key;

	FightStatisticEntry(String key)
	{
		this.key = key;
	}

	public String getKey()
	{
		return key;
	}
}
