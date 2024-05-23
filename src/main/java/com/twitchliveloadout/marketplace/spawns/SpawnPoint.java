package com.twitchliveloadout.marketplace.spawns;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

@Getter
public class SpawnPoint {
	private final WorldPoint worldPoint;

	public SpawnPoint(WorldPoint worldPoint)
	{
		this.worldPoint = worldPoint;
	}

	public LocalPoint getLocalPoint(Client client)
	{
		final WorldView worldView = client.getTopLevelWorldView();
		final LocalPoint localPoint = LocalPoint.fromWorld(worldView, worldPoint);

		return localPoint;
	}

	public int getPlane()
	{
		return worldPoint.getPlane();
	}
}
