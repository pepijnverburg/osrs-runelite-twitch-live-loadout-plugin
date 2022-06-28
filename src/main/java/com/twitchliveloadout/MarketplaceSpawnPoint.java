package com.twitchliveloadout;

import lombok.Getter;
import net.runelite.api.coords.LocalPoint;

public class MarketplaceSpawnPoint {
	@Getter
	private final LocalPoint localPoint;
	@Getter
	private final int plane;

	public MarketplaceSpawnPoint(LocalPoint localPoint, int plane)
	{
		this.localPoint = localPoint;
		this.plane = plane;
	}
}
