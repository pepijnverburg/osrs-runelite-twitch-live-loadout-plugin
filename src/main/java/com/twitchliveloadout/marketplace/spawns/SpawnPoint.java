package com.twitchliveloadout.marketplace.spawns;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

public class SpawnPoint {
	@Getter
	private WorldPoint worldPoint;

	public SpawnPoint(WorldPoint worldPoint)
	{
		this.worldPoint = worldPoint;
	}

	public LocalPoint getLocalPoint(Client client)
	{
		final LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);

		return localPoint;
	}

	public int getPlane()
	{
		return worldPoint.getPlane();
	}
}
