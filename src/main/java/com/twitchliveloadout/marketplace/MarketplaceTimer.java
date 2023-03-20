package com.twitchliveloadout.marketplace;

import lombok.Getter;

public enum MarketplaceTimer {
	// NOTE: timers are distributed evenly across a game tick during the client ticks
	// if a timer is needed to be > 600ms in terms of timer then use a game tick
	PRODUCT_SPAWN_ROTATIONS("product-spawn-rotations", 75),
	RECORD_LOCATION("record-location", 200),
	RESPAWNS("respawns", 200),
	WIDGETS("widgets", 200),
	PRODUCT_BEHAVIOURS("product-behaviours", 300),
	PRODUCT_EXPIRED_SPAWNS("product-expired-spawns", 500),
	;

	@Getter
	private final String name;
	@Getter
	private final int delayMs;

	MarketplaceTimer(String name, int delayMs)
	{
		this.name = name;
		this.delayMs = delayMs;
	}
}

