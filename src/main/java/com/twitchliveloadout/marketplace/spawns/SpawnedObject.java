package com.twitchliveloadout.marketplace.spawns;

import com.twitchliveloadout.marketplace.*;
import com.twitchliveloadout.marketplace.products.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.awt.*;
import java.time.Instant;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

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
	private int randomEffectCounter = 0;

	@Getter
	private Instant lastRandomEffectAt;

	private double currentScale = -1;
	private double currentRotationDegrees = 0;
	private int currentAnimationId;
	private Instant lockAnimationUntil;

	public SpawnedObject(MarketplaceProduct product, Client client, ModelData modelData, SpawnPoint spawnPoint, EbsSpawn spawn, EbsModelSet modelSet)
	{
		this.spawnedAt = Instant.now();
		this.product = product;
		this.client = client;
		this.object = client.createRuneLiteObject();
		this.modelData = modelData;
		this.spawnPoint = spawnPoint;
		this.spawn = spawn;
		this.modelSet = modelSet;

		// initialize expiry if set via spawn properties
		initializeExpiry();

		// setup one time settings
		initializeModel();

		// reset the animations to it will immediately show the idle animation if available
		resetAnimation();

		// set to initial spawn-point
		updateLocation();
	}

	private void initializeExpiry()
	{
		EbsRandomRange durationMs = spawn.durationMs;
		if (durationMs != null)
		{
			int randomDurationMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(durationMs, 0,0);
			expiredAt = Instant.now().plusMillis(randomDurationMs);
		}
	}

	private void initializeModel()
	{
		object.setDrawFrontTilesFirst(true);
	}

	public void rotateTowards(LocalPoint targetPoint)
	{
		LocalPoint sourcePoint = spawnPoint.getLocalPoint(client);

		// guard: check if the source point is valid
		// if not that means it is not in the current scene
		if (sourcePoint == null)
		{
			return;
		}

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
		int roundedScale = (int) scale;

		// guard: check if the scale is valid and changed
		if (roundedScale < 0 || scale == currentScale)
		{
			return;
		}

		currentScale = scale;
		modelData.cloneVertices();
		modelData.scale(roundedScale, roundedScale, roundedScale);

		// only set the radius when it is set, because some radius values
		// can cause major frame drops, for this reason it needs to be tested
		// and hard-coded in the config and never automatically set!
		if (modelSet.scalePerOneTile != null)
		{
			double scalePerOneTile = modelSet.scalePerOneTile;
			double tileRadius = scale / scalePerOneTile;
			int radius = (int) (RUNELITE_OBJECT_RADIUS_PER_TILE * tileRadius);

			object.setRadius(radius);
		}

		render();
	}

	/**
	 * Recolor the model data through an EBS configured recolor.
	 * Note that cloning the colors and re-rendering the object is needed manually outside of this method!
	 */
	public void recolor(EbsRecolor recolor)
	{

		// guard: make sure the recolor is valid
		if (recolor == null)
		{
			return;
		}

		Integer sourceColorIndex = recolor.sourceColorIndex;
		Integer sourceColorHsl = recolor.sourceColorHsl;
		Integer targetColorHsl = recolor.targetColorHsl;
		Integer targetColorHex = recolor.targetColorHex;

		// check if a hex target color is passed which needs to be converted
		// NOTE: when also a HSL is padded the HSL takes priority
		if (targetColorHsl == null && targetColorHex != null)
		{
			targetColorHsl = getColorHsl(targetColorHex);
		}

		// guard: skip when target is not valid
		if (targetColorHsl == null)
		{
			return;
		}

		// determine whether an index, specific color or everything is requested to be changed
		if (sourceColorHsl != null) {
			recolorByColor(sourceColorHsl, targetColorHsl);
		} else if (isValidColorIndex(sourceColorIndex)) {
			recolorByIndex(sourceColorIndex, targetColorHsl);
		} else {

			// recolor the whole model
			for (short color : modelData.getFaceColors())
			{
				recolorByColor(color, targetColorHsl);
			}
		}
	}

	private void recolorByColor(int sourceColorHsl, int targetColorHsl)
	{
		modelData.recolor((short) sourceColorHsl, (short) targetColorHsl);
	}

	private void recolorByIndex(int sourceColorIndex, int targetColorHsl)
	{

		// guard: make sure the index and color are valid
		if (!isValidColorIndex(sourceColorIndex))
		{
			return;
		}

		short[] colors = modelData.getFaceColors();
		short sourceColorHsl = colors[sourceColorIndex];

		modelData.recolor(sourceColorHsl, (short) targetColorHsl);
	}

	private int getColorHsl(Integer colorHex)
	{
		Color color = getColor(colorHex);
		short colorHsl = JagexColor.rgbToHSL(color.getRGB(), 1.0d);

		return colorHsl;
	}

	private Color getColor(Integer colorHex)
	{
		int r = (colorHex & 0xFF0000) >> 16;
		int g = (colorHex & 0xFF00) >> 8;
		int b = (colorHex & 0xFF);

		return new Color(r, g, b);
	}

	private boolean isValidColorIndex(int colorIndex)
	{
		return colorIndex >= 0 && colorIndex < modelData.getFaceColors().length;
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

//		log.info("SET ANIMATION TRULY TO: "+ animationId);
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

	public EbsMovementFrame getMovementAnimations()
	{
		if (spawn.movementAnimations == null)
		{
			return new EbsMovementFrame();
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

	public boolean isInRegion()
	{
		return isInView(REGION_SIZE);
	}
	public boolean isInChunk()
	{
		return isInView(CHUNK_SIZE);
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
		object.setModel(modelData.light(64, 850, -30, -50, -30));
	}

	public void respawn()
	{

		// guard: location cannot be set to local point if not in region
		if (!isInRegion())
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

		// guard: check if the point is valid
		// if not that means it is not in the current scene
		if (localPoint == null)
		{
			return;
		}

		// move the object to the new relative local point as the scene offset might be changed
		object.setLocation(localPoint, plane);
	}

	public void updateLastRandomEffectAt(boolean isTriggered)
	{
		EbsInterval randomInterval = spawn.randomEffectsInterval;
		Integer afterTriggerDelayMs = 0;

		// add extra time requested to delay the effect after a successful trigger
		if (randomInterval != null && isTriggered)
		{
			afterTriggerDelayMs = randomInterval.afterTriggerDelayMs;
		}

		lastRandomEffectAt = Instant.now().plusMillis(afterTriggerDelayMs);
	}

	public void registerRandomEffect()
	{
		randomEffectCounter += 1;
		updateLastRandomEffectAt(true);
	}

	public boolean isExpired()
	{
		return expiredAt != null && Instant.now().isAfter(expiredAt);
	}
}
