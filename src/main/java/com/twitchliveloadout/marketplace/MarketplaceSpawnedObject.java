package com.twitchliveloadout.marketplace;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.time.Instant;

public class MarketplaceSpawnedObject {

	@Getter
	private final Instant spawnedAt;

	@Getter
	private final Client client;

	@Getter
	private final RuneLiteObject object;

	@Getter
	private final MarketplaceSpawnPoint spawnPoint;

	@Getter
	private final MarketplaceProduct product;

	@Getter
	@Setter
	private boolean respawnRequired = false;

	public MarketplaceSpawnedObject(Client client, RuneLiteObject object, MarketplaceSpawnPoint spawnPoint, MarketplaceProduct product)
	{
		this.spawnedAt = Instant.now();
		this.client = client;
		this.object = object;
		this.spawnPoint = spawnPoint;
		this.product = product;
	}

	public boolean isExpired()
	{
//		final Instant now = Instant.now();
//		final int expiryTimeMs = product.getExpiryTimeMs();
//		final Instant expiredAt = spawnedAt.plusMillis(expiryTimeMs);
//
//		return now.isAfter(expiredAt);
		return false;
	}

	public void show()
	{
		object.setActive(true);
	}

	public void hide()
	{
		object.setActive(false);
	}

	public void respawn()
	{
		final int plane = spawnPoint.getPlane();
		final WorldPoint worldPoint = spawnPoint.getWorldPoint();
		final LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
		final boolean isInScene = worldPoint.isInScene(client);

		// guard: location cannot be set to local point if not in scene
		if (!isInScene)
		{
			return;
		}

		// move the object to the new relative local point as the scene offset might be changed
		object.setLocation(localPoint, plane);

		// de-activate and re-activate again to force re-render
		hide();
		show();
	}
}
