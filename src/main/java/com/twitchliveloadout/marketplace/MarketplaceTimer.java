package com.twitchliveloadout.marketplace;

import lombok.Getter;

@Getter
public enum MarketplaceTimer {
	// NOTE: timers are distributed evenly across a game tick during the client ticks
	// if a timer is needed to be > 600ms in terms of timer then use a game tick
	PRODUCT_SPAWN_ROTATIONS("product-spawn-rotations", 75),
	WIDGETS("widgets", 100),
	DRAWS("draws", 200),
	RECORD_LOCATION("record-location", 200),
	RESPAWNS("respawns", 200),
	PRODUCT_BEHAVIOURS("product-behaviours", 300),
	PRODUCT_EXPIRED_SPAWNS("product-expired-spawns", 500),
	;

	private final String name;
	private final int delayMs;

	MarketplaceTimer(String name, int delayMs)
	{
		this.name = name;
		this.delayMs = delayMs;
	}
}

