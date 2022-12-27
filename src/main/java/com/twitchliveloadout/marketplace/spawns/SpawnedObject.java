package com.twitchliveloadout.marketplace.spawns;

import com.twitchliveloadout.marketplace.*;
import com.twitchliveloadout.marketplace.products.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.time.Instant;

@Slf4j
public class SpawnedObject {

	@Getter
	private final Instant spawnedAt;

	@Getter
	private final Client client;

	@Getter
	private final RuneLiteObject object;

	@Getter
	private final EbsSpawn spawn;

	@Getter
	private final EbsModelSet modelSet;

	@Getter
	private final ModelData modelData;

	@Getter
	@Setter
	private SpawnPoint spawnPoint;

	@Getter
	private final MarketplaceProduct product;

	@Getter
	@Setter
	private boolean respawnRequired = false;

	@Getter
	private Instant expiredAt;

	@Getter
	private int randomVisualEffectCounter = 0;

	@Getter
	private Instant lastRandomVisualEffectAt;

	private double currentScale = -1;
	private double currentRotationDegrees = 0;
	private int currentAnimationId;
	private Instant lockAnimationUntil;

	public SpawnedObject(MarketplaceProduct product, Client client, RuneLiteObject object, ModelData modelData, SpawnPoint spawnPoint, EbsSpawn spawn, EbsModelSet modelSet)
	{
		this.spawnedAt = Instant.now();
		this.product = product;
		this.client = client;
		this.object = object;
		this.modelData = modelData;
		this.spawnPoint = spawnPoint;
		this.spawn = spawn;
		this.modelSet = modelSet;

		// initialize expiry if set via spawn properties
		EbsRandomRange durationMs = spawn.durationMs;
		if (durationMs != null)
		{
			int randomDurationMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(durationMs, 0,0);
			expiredAt = Instant.now().plusMillis(randomDurationMs);
		}

		// reset the animations to it will immediately show the idle animation if available
		resetAnimation();

		// set to initial spawn-point
		updateLocation();
	}

	public void rotateTowards(LocalPoint targetPoint)
	{
		LocalPoint sourcePoint = spawnPoint.getLocalPoint(client);
		int deltaX = sourcePoint.getX() - targetPoint.getX();
		int deltaY = sourcePoint.getY() - targetPoint.getY();
		double angleRadians = Math.atan2(deltaX, deltaY);
		double angleDegrees = Math.toDegrees(angleRadians);

		rotate(angleDegrees);
	}

	public void rotate(double angleDegrees)
	{

		// guard: skip rotation if already rotated like this for performance
		if (currentRotationDegrees == angleDegrees)
		{
			return;
		}

		currentRotationDegrees = angleDegrees;

		// make sure rotation is not negative
		if (angleDegrees < 0) {
			angleDegrees = 360 + (angleDegrees % 360);
		}

		// make sure there are no multiple rotations
		if (angleDegrees > 360) {
			angleDegrees = angleDegrees % 360;
		}

		int orientation = (int) (angleDegrees * MarketplaceConstants.RUNELITE_OBJECT_FULL_ROTATION / 360d);
		object.setOrientation(orientation);
	}

	public void scale(double scale)
	{

		// guard: check if the scale is valid and changed
		if (scale < 0 || scale == currentScale)
		{
			return;
		}

		currentScale = scale;

		SpawnUtilities.scaleModel(modelData, scale);
		render();
	}

	public void setAnimation(int animationId, boolean shouldLoop)
	{
		Animation animation = null;

		// guard: skip when the current animation
		if  (animationId == currentAnimationId)
		{
			return;
		}

		if (animationId >= 0)
		{
			animation = client.loadAnimation(animationId);
		}

		object.setShouldLoop(shouldLoop);
		object.setAnimation(animation);
		currentAnimationId = animationId;
	}

	public boolean isAnimationLocked()
	{
		return (lockAnimationUntil != null && Instant.now().isBefore(lockAnimationUntil));
	}

	public void lockAnimationUntil(long durationMs)
	{
		lockAnimationUntil = Instant.now().plusMillis(durationMs);
	}

	public void resetAnimation()
	{
		int idleAnimationId = getMovementAnimations().idle;

		// guard: set to no animation when there is no idle animation
		if (idleAnimationId < 0)  {
			setAnimation(-1, false);
			return;
		}

		setAnimation(idleAnimationId, true);
	}

	public EbsMovementAnimations getMovementAnimations()
	{
		if (spawn.movementAnimations == null)
		{
			return new EbsMovementAnimations();
		}

		return spawn.movementAnimations;
	}

	public void show()
	{
		object.setActive(true);
		render();
	}

	public void hide()
	{
		object.setActive(false);
		object.setModel(null);
	}

	public boolean isInView()
	{
		return isInView(Constants.REGION_SIZE);
	}

	public boolean isInView(int radius)
	{
		final WorldPoint worldPoint = spawnPoint.getWorldPoint();
		final LocalPoint playerLocalPoint = client.getLocalPlayer().getLocalLocation();
		final WorldPoint playerWorldPoint = WorldPoint.fromLocal(client, playerLocalPoint);
		final int distanceToPlayer = worldPoint.distanceTo(playerWorldPoint);
		final boolean isInView = (distanceToPlayer <= radius);

		return isInView;
	}

	public void render()
	{
		object.setModel(modelData.light());
	}

	public void respawn()
	{

		// guard: location cannot be set to local point if not in scene
		if (!isInView())
		{
			return;
		}

		// the location might've changed, so update
		updateLocation();

		// de-activate and re-activate again to force re-render
		hide();
		show();
	}

	public void updateLocation()
	{
		final LocalPoint localPoint = spawnPoint.getLocalPoint(client);
		final int plane = spawnPoint.getPlane();

		// move the object to the new relative local point as the scene offset might be changed
		object.setLocation(localPoint, plane);
	}

	public void upateLastRandomVisualEffectAt()
	{
		lastRandomVisualEffectAt = Instant.now();
	}

	public void registerRandomVisualEffect()
	{
		randomVisualEffectCounter += 1;
		upateLastRandomVisualEffectAt();
	}

	public boolean isExpired()
	{
		return expiredAt != null && Instant.now().isAfter(expiredAt);
	}
}