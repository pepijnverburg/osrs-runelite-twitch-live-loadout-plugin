package com.twitchliveloadout.marketplace;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public class MarketplaceSpawnPoint {
	@Getter
	private final WorldPoint worldPoint;

	@Getter
	private final int plane;

	public MarketplaceSpawnPoint(WorldPoint worldPoint, int plane)
	{
		this.worldPoint = worldPoint;
		this.plane = plane;
	}

	public LocalPoint getLocalPoint(Client client)
	{
		final LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);

		return localPoint;
	}
}
