package net.runelite.client.plugins.twitchliveloadout;

public enum FightStatisticEntry {
	RANGED("ranged"),
	MELEE("melee"),
	MAGIC("magic"),

	FREEZE("freeze"),
	SPECIAL_ATTACK("specialAttack"),
	POISON("poison)"),
	VENGEANCE("vengeance"),
	HEAL("heal"),

	PRAYER("prayer"),
	SMITE("smite"),
	RANGED_PRAYER("rangedPrayer"),
	MELEE_PRAYER("meleePrayer"),
	MAGIC_PRAYER("magicPrayer");

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
