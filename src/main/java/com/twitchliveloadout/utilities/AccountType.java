package com.twitchliveloadout.utilities;

import lombok.Getter;

public enum AccountType
{
	/**
	 * Normal account type.
	 */
	NORMAL(0, "NORMAL"),
	/**
	 * Ironman account type.
	 */
	IRONMAN(1, "IRONMAN"),
	/**
	 * Ultimate ironman account type.
	 */
	ULTIMATE_IRONMAN(2, "ULTIMATE_IRONMAN"),
	/**
	 * Hardcore ironman account type.
	 */
	HARDCORE_IRONMAN(3, "HARDCORE_IRONMAN"),
	/**
	 * Group ironman account type
	 */
	GROUP_IRONMAN(4, "GROUP_IRONMAN"),
	/**
	 * Hardcore group ironman account type
	 */
	HARDCORE_GROUP_IRONMAN(5, "HARDCORE_GROUP_IRONMAN"),

	/**
	 * Unranked group ironman account type
	 */
	UNRANKED_GROUP_IRONMAN(6, "UNRANKED_GROUP_IRONMAN");

	@Getter
	private final int id;

	@Getter
	private final String key;

	AccountType(int id, String key)
	{
		this.id = id;
		this.key = key;
	}
}
