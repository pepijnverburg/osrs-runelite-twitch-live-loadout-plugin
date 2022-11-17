package com.twitchliveloadout.marketplace;


import net.runelite.api.coords.WorldPoint;

import java.util.HashMap;

public class MarketplaceSpawnPointFactory {

	public static MarketplaceProduct.GetSpawnPoints createDefaultOutwardSpawner(int spawnAmount)
	{
		return (manager) -> {
			final HashMap<WorldPoint, MarketplaceSpawnPoint> spawnPoints = new HashMap();

			for (int spawnIndex = 0; spawnIndex < spawnAmount; spawnIndex++) {
				MarketplaceSpawnPoint spawnPoint = manager.getOutwardSpawnPoint(2, 2, 12, spawnPoints);

				// guard: make sure the spawnpoint is valid
				if (spawnPoint == null)
				{
					continue;
				}

				WorldPoint worldPoint = spawnPoint.getWorldPoint();
				spawnPoints.put(worldPoint, spawnPoint);
			}

			return spawnPoints.values();
		};
	}
}
