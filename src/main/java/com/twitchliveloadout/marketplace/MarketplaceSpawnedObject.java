package com.twitchliveloadout.marketplace;

import lombok.Getter;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;

import java.time.Instant;

public class MarketplaceSpawnedObject {
	@Getter
	private final Instant spawnedAt;
	@Getter
	private final RuneLiteObject object;
	@Getter
	private final MarketplaceSpawnPoint spawnPoint;
	@Getter
	private final MarketplaceProduct product;

	public MarketplaceSpawnedObject(RuneLiteObject object, MarketplaceSpawnPoint spawnPoint, MarketplaceProduct product)
	{
		this.spawnedAt = Instant.now();
		this.object = object;
		this.spawnPoint = spawnPoint;
		this.product = product;
	}

	public boolean isExpired()
	{
		final Instant now = Instant.now();
		final int expiryTimeMs = product.getExpiryTimeMs();
		final Instant expiredAt = spawnedAt.plusMillis(expiryTimeMs);

		return now.isAfter(expiredAt);
	}

	public boolean isAtLocation(LocalPoint checkedLocalPoint, int checkedPlane)
	{
		int plane = spawnPoint.getPlane();
		LocalPoint localPoint = spawnPoint.getLocalPoint();
		System.out.println("CHECKED PLANE: "+ checkedPlane);
		System.out.println("OBJECT PLANE: "+ plane);
		System.out.println("CHECKED LOCAL POINT: "+ checkedLocalPoint.toString());
		System.out.println("OBJECT LOCAL POINT: "+ localPoint.toString());

		return plane == checkedPlane && localPoint.distanceTo(checkedLocalPoint) <= 0;
	}

	public void show()
	{
		object.setActive(true);
	}

	public void hide()
	{
		object.setActive(false);
	}
}
