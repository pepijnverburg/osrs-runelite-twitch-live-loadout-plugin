package com.twitchliveloadout.marketplace.spawns;

import com.twitchliveloadout.marketplace.*;
import com.twitchliveloadout.marketplace.products.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.geometry.SimplePolygon;
import net.runelite.api.model.Jarvis;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;
import static net.runelite.api.Perspective.COSINE;
import static net.runelite.api.Perspective.SINE;

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
	private EbsModelSet modelSet;

	@Getter
	private ModelData modelData;

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
	private int currentTranslateX = 0;
	private int currentTranslateY = 0;
	private int currentTranslateZ = 0;
	private int currentAnimationId;
	private Instant lockAnimationUntil;

	private final ConcurrentHashMap<String, String> stateFrameValues = new ConcurrentHashMap<>();

	public SpawnedObject(MarketplaceProduct product, Client client, SpawnPoint spawnPoint, EbsSpawn spawn, EbsModelSet modelSet, Instant expiredAt)
	{
		this.spawnedAt = Instant.now();
		this.product = product;
		this.client = client;
		this.object = client.createRuneLiteObject();
		this.spawnPoint = spawnPoint;
		this.spawn = spawn;
		this.modelSet = modelSet;
		this.expiredAt = expiredAt;

		// first initialize the model set so we have proper model data
		// for the next initialisation methods
		updateModelSet(false);

		// setup one time settings
		initializeObject();

		// reset the animations to it will immediately show the idle animation if available
		resetAnimation();

		// set to initial spawn-point
		updateLocation();
	}

	public void setModelSet(EbsModelSet modelSet)
	{

		// guard: make sure the new one is valid
		if (modelSet == null)
		{
			return;
		}

		this.modelSet = modelSet;
	}

	public void updateModelSet(boolean preserveTransforms)
	{

		// guard: make sure the selected model set is valid
		if (modelSet == null)
		{
			return;
		}

		// get properties from model set
		ArrayList<Integer> modelIds = modelSet.ids;
		EbsModelPlacement modelPlacement = spawn.modelPlacement;
		EbsTranslation modelSetTranslation = modelSet.translation;

		if (modelPlacement == null)
		{
			modelPlacement = new EbsModelPlacement();
		}

		EbsTranslation modelPlacementTranslation = modelPlacement.translation;
		int translateX = mergeTranslations(TRANSLATE_X_AXIS, modelSetTranslation, modelPlacementTranslation);
		int translateY = mergeTranslations(TRANSLATE_Y_AXIS, modelSetTranslation, modelPlacementTranslation);
		int translateZ = mergeTranslations(TRANSLATE_Z_AXIS, modelSetTranslation, modelPlacementTranslation);
		double modelScale = MarketplaceRandomizers.getValidRandomNumberByRange(modelSet.scale, 1, 1, 0, MAX_MODEL_SCALE);
		double modelRotationDegrees = MarketplaceRandomizers.getValidRandomNumberByRange(modelPlacement.rotation, 0, 360, 0, 360);
		ArrayList<EbsRecolor> recolors = modelSet.recolors;
		ArrayList<ModelData> modelDataChunks = new ArrayList<>();
		boolean shouldScaleModel = (modelSet.scale != null);
		boolean shouldRotateModel = !NO_ROTATION_TYPE.equals(modelPlacement.rotationType);
		boolean shouldTranslateModel = (translateX != 0 || translateY != 0 || translateZ != 0);

		// load all the models if set
		if (modelIds != null)
		{
			modelIds.forEach((modelId) -> {
				modelDataChunks.add(client.loadModelData(modelId));
			});

			// merge all models into one
			modelData = client.mergeModels(modelDataChunks.toArray(new ModelData[modelDataChunks.size()]), modelDataChunks.size());
		}

		// guard: make sure the model data is valid before doing anything else
		if (modelData == null)
		{
			return;
		}

		// check for valid recolors
		if (recolors != null)
		{
			modelData.cloneColors();

			// apply recolors
			LambdaIterator.handleAll(recolors, (recolor) -> {
				recolor(recolor);
			});
		}

		// scale model
		// NOTE: don't include with preserve transform, because different model IDs might require a different scale
		if (shouldScaleModel) {
			scale(modelScale, false);
		}

		// rotate model
		if (preserveTransforms) {
			rotate(currentRotationDegrees, true);
		} else if (shouldRotateModel) {
			rotate(modelRotationDegrees, false);
		}

		// set the position
		// NOTE: don't include with preserve transform, because different model IDs might require a different scale
		if (shouldTranslateModel) {
			translate(translateX, translateY, translateZ, false);
		}

		// re-render after changes
		render();
	}

	private int mergeTranslations(String translateAxis, EbsTranslation translationOne, EbsTranslation translationTwo)
	{
		EbsRandomRange rangeOne = getTranslationRandomRange(translateAxis, translationOne);
		EbsRandomRange rangeTwo = getTranslationRandomRange(translateAxis, translationTwo);
		int amountOne = (int) MarketplaceRandomizers.getValidRandomNumberByRange(rangeOne, 0, 0, MIN_MODEL_TRANSLATE, MAX_MODEL_TRANSLATE);
		int amountTwo = (int) MarketplaceRandomizers.getValidRandomNumberByRange(rangeTwo, 0, 0, MIN_MODEL_TRANSLATE, MAX_MODEL_TRANSLATE);

		return amountOne + amountTwo;
	}

	private EbsRandomRange getTranslationRandomRange(String translateAxis, EbsTranslation translation)
	{

		// guard: make sure the passed params are valid
		if (translateAxis == null || translation == null)
		{
			return null;
		}

		switch (translateAxis)
		{
			case TRANSLATE_X_AXIS:
				return translation.x;
			case TRANSLATE_Y_AXIS:
				return translation.y;
			case TRANSLATE_Z_AXIS:
				return translation.z;
		}

		return null;
	}

	private void initializeObject()
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

		rotate(angleDegrees, false);
	}

	public void translate(int x, int y, int z, boolean forceUpdate)
	{

		// guard: skip when there are no changes
		if (currentTranslateX == x && currentTranslateY == y && currentTranslateZ == z && !forceUpdate)
		{
			return;
		}

		currentTranslateX = x;
		currentTranslateY = y;
		currentTranslateZ = z;

		modelData.cloneVertices();
		modelData.translate(x, y, z);
	}

	public void rotate(double angleDegrees, boolean forceUpdate)
	{

		// guard: skip rotation if already rotated like this for performance
		if (currentRotationDegrees == angleDegrees && !forceUpdate)
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
		setOrientation(orientation);
	}

	public void setOrientation(int orientation)
	{
		object.setOrientation(orientation);
	}

	public void scale(double scale, boolean forceUpdate)
	{
		int roundedScale = (int) scale;

		// guard: check if the scale is valid and changed
		if (roundedScale < 0 || (scale == currentScale && !forceUpdate))
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
	}

	/**
	 * Recolor the model data through an EBS configured recolor.
	 * Note that cloning the colors and re-rendering the object is needed manually outside of this method!
	 */
	private void recolor(EbsRecolor recolor)
	{

		// guard: make sure the model and recolor is valid
		if (recolor == null || modelData == null)
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
			targetColorHsl = MarketplaceColors.getColorHsl(targetColorHex);
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

		// guard: make sure the model is valid
		if (modelData == null)
		{
			return;
		}

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

	private boolean isValidColorIndex(int colorIndex)
	{
		// guard: make sure the model is valid
		if (modelData == null)
		{
			return false;
		}

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

	public String getStateFrameValue(String stateKey)
	{
		if (stateKey == null)
		{
			return null;
		}

		return stateFrameValues.get(stateKey);
	}

	public void setStateFrameValue(String stateKey, String stateValue)
	{
		if (stateKey == null)
		{
			return;
		}

		stateFrameValues.put(stateKey, stateValue);
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

		// guard: make sure the model is valid
		if (modelData == null)
		{
			return;
		}

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

	public void expireNow()
	{
		expiredAt = Instant.now();
	}

	public boolean isExpired()
	{
		return expiredAt != null && Instant.now().isAfter(expiredAt);
	}

	public SimplePolygon calculatePolygon()
	{
		Model model = object.getModel();
		int jauOrient = object.getOrientation();
		LocalPoint localPoint = spawnPoint.getLocalPoint(client);
		int x = localPoint.getX();
		int y = localPoint.getY();
		int z = client.getPlane();
		int zOff = Perspective.getTileHeight(client, localPoint, z);
		AABB aabb = model.getAABB(jauOrient);

		int x1 = aabb.getCenterX();
		int y1 = aabb.getCenterZ();
		int z1 = aabb.getCenterY() + zOff;

		int ex = aabb.getExtremeX();
		int ey = aabb.getExtremeZ();
		int ez = aabb.getExtremeY();

		int x2 = x1 + ex;
		int y2 = y1 + ey;
		int z2 = z1 + ez;

		x1 -= ex;
		y1 -= ey;
		z1 -= ez;

		int[] xa = new int[]{
				x1, x2, x1, x2,
				x1, x2, x1, x2
		};
		int[] ya = new int[]{
				y1, y1, y2, y2,
				y1, y1, y2, y2
		};
		int[] za = new int[]{
				z1, z1, z1, z1,
				z2, z2, z2, z2
		};

		int[] x2d = new int[8];
		int[] y2d = new int[8];

		modelToCanvasCpu(client, 8, x, y, z, 0, xa, ya, za, x2d, y2d);

		return Jarvis.convexHull(x2d, y2d);
	}

	private static void modelToCanvasCpu(Client client, int end, int x3dCenter, int y3dCenter, int z3dCenter, int rotate, int[] x3d, int[] y3d, int[] z3d, int[] x2d, int[] y2d)
	{
		final int
				cameraPitch = client.getCameraPitch(),
				cameraYaw = client.getCameraYaw(),

				pitchSin = SINE[cameraPitch],
				pitchCos = COSINE[cameraPitch],
				yawSin = SINE[cameraYaw],
				yawCos = COSINE[cameraYaw],
				rotateSin = SINE[rotate],
				rotateCos = COSINE[rotate],

				cx = x3dCenter - client.getCameraX(),
				cy = y3dCenter - client.getCameraY(),
				cz = z3dCenter - client.getCameraZ(),

				viewportXMiddle = client.getViewportWidth() / 2,
				viewportYMiddle = client.getViewportHeight() / 2,
				viewportXOffset = client.getViewportXOffset(),
				viewportYOffset = client.getViewportYOffset(),

				zoom3d = client.getScale();

		for (int i = 0; i < end; i++)
		{
			int x = x3d[i];
			int y = y3d[i];
			int z = z3d[i];

			if (rotate != 0)
			{
				int x0 = x;
				x = x0 * rotateCos + y * rotateSin >> 16;
				y = y * rotateCos - x0 * rotateSin >> 16;
			}

			x += cx;
			y += cy;
			z += cz;

			final int
					x1 = x * yawCos + y * yawSin >> 16,
					y1 = y * yawCos - x * yawSin >> 16,
					y2 = z * pitchCos - y1 * pitchSin >> 16,
					z1 = y1 * pitchCos + z * pitchSin >> 16;

			int viewX, viewY;

			if (z1 < 50)
			{
				viewX = Integer.MIN_VALUE;
				viewY = Integer.MIN_VALUE;
			}
			else
			{
				viewX = (viewportXMiddle + x1 * zoom3d / z1) + viewportXOffset;
				viewY = (viewportYMiddle + y2 * zoom3d / z1) + viewportYOffset;
			}

			x2d[i] = viewX;
			y2d[i] = viewY;
		}
	}
}
