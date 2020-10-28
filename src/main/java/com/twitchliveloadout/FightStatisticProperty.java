package com.twitchliveloadout;

public enum FightStatisticProperty
{
	HIT_COUNTERS("hc"),
	MISS_COUNTERS("mc"),
	HIT_DAMAGES("hd"),
	MISS_DAMAGES("md"),
	DURATION_SECONDS("ds");

	private final String key;

	FightStatisticProperty(String key)
	{
		this.key = key;
	}

	public String getKey()
	{
		return key;
	}
}
