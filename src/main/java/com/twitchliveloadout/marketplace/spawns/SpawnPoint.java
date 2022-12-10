package com.twitchliveloadout.marketplace.spawns;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public class SpawnPoint {
	@Getter
	private final WorldPoint worldPoint;

	@Getter
	private final int plane;

	public SpawnPoint(WorldPoint worldPoint, int plane)
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
