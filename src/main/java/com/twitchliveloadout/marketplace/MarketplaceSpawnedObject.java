package com.twitchliveloadout.marketplace;

import lombok.Getter;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.coords.LocalPoint;

import java.time.Instant;

public class MarketplaceSpawnedObject {
	private final Client client;

	@Getter
	private final Instant spawnedAt;
	@Getter
	private final RuneLiteObject object;
	@Getter
	private final MarketplaceModel model;
	@Getter
	private final MarketplaceSpawnPoint spawnPoint;
	@Getter
	private final MarketplaceProduct product;

	public MarketplaceSpawnedObject(Client client, RuneLiteObject object, MarketplaceModel model, MarketplaceSpawnPoint spawnPoint, MarketplaceProduct product)
	{
		this.client = client;
		this.spawnedAt = Instant.now();
		this.object = object;
		this.model = model;
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
		final int plane = spawnPoint.getPlane();
		final LocalPoint localPoint = spawnPoint.getLocalPoint();

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

	public void respawn()
	{
//		final MarketplaceProduct.CustomizeModel customizeModel = product.getCustomizeModel();
//		final boolean hasModelCustomizer = customizeModel != null;
//		final int modelId = model.getModelId();
//		final int animationId = model.getAnimationId();
//		final boolean hasAnimation = animationId > 0;
//		final int animationDurationMs = model.getAnimationDurationMs();
//		final boolean shouldResetAnimation = animationDurationMs >= 0;
//		final ModelData modelData = client.loadModelData(modelId)
//			.cloneVertices()
//			.cloneColors();
//
//		// customize the model if needed to the same customizer callback
//		if (hasModelCustomizer)
//		{
//			customizeModel.execute(modelData, modelId);
//		}
//
//		// only set a looping animation when there is an animation and
//		// the animation was NOT a one time thing, if it was one time the respawn
//		// should not trigger the animation again
//		if (hasAnimation && !shouldResetAnimation)
//		{
//			Animation objectAnimation = client.loadAnimation(animationId);
//			object.setAnimation(objectAnimation);
//			object.setShouldLoop(true);
//		}
//
//		object.setModel(modelData.light());
		object.setActive(false);
		object.setActive(true);
	}
}
