package com.twitchliveloadout.marketplace;

import lombok.Getter;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public class MarketplaceSpawnPoint {
	@Getter
	private final LocalPoint localPoint;
	@Getter
	private final WorldPoint worldPoint;
	@Getter
	private final int plane;

	public MarketplaceSpawnPoint(LocalPoint localPoint, WorldPoint worldPoint, int plane)
	{
		this.localPoint = localPoint;
		this.worldPoint = worldPoint;
		this.plane = plane;
	}
}
