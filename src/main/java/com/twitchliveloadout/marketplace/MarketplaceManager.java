package com.twitchliveloadout.marketplace;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class MarketplaceManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;

	@Getter
	private final Client client;
	private final TwitchLiveLoadoutConfig config;
	private final boolean ENABLE_TEST_PRODUCTS = false;

	/**
	 * List to keep track of all the queued products
	 */
	private final CopyOnWriteArrayList<MarketplaceProduct> queuedProducts = new CopyOnWriteArrayList();

	/**
	 * List to keep track of all the active products
	 */
	private final CopyOnWriteArrayList<MarketplaceProduct> activeProducts = new CopyOnWriteArrayList();

	public MarketplaceManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client, TwitchLiveLoadoutConfig config)
	{
		this.plugin = plugin;
		this.twitchState = twitchState;
		this.client = client;
		this.config = config;
	}

	/**
	 * Handle game state changes to respawn all objects, because they are cleared
	 * when a new scene is being loaded.
	 */
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState newGameState = event.getGameState();

		// guard: only respawn on the loading event
		if (newGameState != GameState.LOADING)
		{
			return;
		}

		// set all objects to require a respawn, because after a loading of
		// a new scene all custom objects are cleared

		handleAllSpawnedObjects((spawnedObject) -> {

			spawnedObject.setRespawnRequired(true);
		});
	}

	/**
	 * Check for new products that should be spawned
	 */
	public void queueNewProducts()
	{

		// guard: only apply the products when the player is logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		if (ENABLE_TEST_PRODUCTS)
		{
			spawnTestProducts();
		}
	}

	public void spawnTestProducts()
	{
		int graphicId = config.devPlayerGraphicId();

		// skipping
//		client.getLocalPlayer().setRunAnimation(1836);
//		client.getLocalPlayer().setWalkAnimation(1836);

		// 3039 - walking drunk
		// 3040 - standing drunk
//		client.getLocalPlayer().setWalkAnimation(3039);
//		client.getLocalPlayer().setRunAnimation(3039);
//		client.getLocalPlayer().setIdlePoseAnimation(3040);

		// skating
//		client.getLocalPlayer().setWalkAnimation(755);
//		client.getLocalPlayer().setRunAnimation(755);
//		client.getLocalPlayer().setIdlePoseAnimation(1767);

		// superman
//		client.getLocalPlayer().setWalkAnimation(1851);
//		client.getLocalPlayer().setRunAnimation(1851);
//		// jig
//		client.getLocalPlayer().setIdlePoseAnimation(2106);

		// drinking
		// LOOK AT PLUGIN!
//		client.getLocalPlayer().setIdlePoseAnimation(1327); // 1327

		plugin.runOnClientThread(() -> {
			if (graphicId > 0) {
				Player player = client.getLocalPlayer();
				player.setGraphic(config.devPlayerGraphicId());
				player.setSpotAnimFrame(0);
			}

			applyProduct(config.devMarketplaceProductSpawn());
			spawnTestObject(config.devObjectSpawnModelId(), config.devObjectSpawnAnimationId());
		});
	}

	public void syncActiveProductsToScene()
	{

		// guard: only apply the products when the player is logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		ArrayList<MarketplaceSpawnedObject> respawnQueue = new ArrayList();
		LocalPoint playerLocalPoint = client.getLocalPlayer().getLocalLocation();
		WorldPoint playerWorldPoint = WorldPoint.fromLocal(client, playerLocalPoint);

		// loop all spawned objects and check whether they should respawn
		handleAllSpawnedObjects((spawnedObject) -> {
			WorldPoint worldPoint = spawnedObject.getSpawnPoint().getWorldPoint();
			int distanceToPlayer = worldPoint.distanceTo(playerWorldPoint);
			boolean isInView = distanceToPlayer < Constants.SCENE_SIZE;
			boolean isRespawnRequired = spawnedObject.isRespawnRequired();

			// only respawn if in viewport and a respawn is required
			// to prevent animations to be reset all the time
			if (isInView && isRespawnRequired) {
				respawnQueue.add(spawnedObject);
				spawnedObject.setRespawnRequired(false);
			}
		});

		// run all respawns at the same time
		plugin.runOnClientThread(() -> {
			for (MarketplaceSpawnedObject spawnedObject : respawnQueue)
			{
				spawnedObject.respawn();
			}
		});
	}

	public void cleanSpawnedObjects()
	{
		ArrayList<MarketplaceSpawnedObject> hideQueue = new ArrayList();

		handleAllSpawnedObjects((spawnedObject) -> {

			// guard: don't clean when not expired yet
			if (!spawnedObject.isExpired())
			{
				return;
			}

			removeSpawnedObject(spawnedObject, spawnedObjects);
		});

		// run all hides at the same time
		plugin.runOnClientThread(() -> {
			for (MarketplaceSpawnedObject spawnedObject : hideQueue)
			{
				spawnedObject.hide();
			}
		});
	}

	private void applyProduct(MarketplaceProduct product)
	{
		triggerProductPlayerGraphic(product);
		triggerProductPlayerAnimation(product);
		spawnProductObjects(product);
	}

	private void triggerProductPlayerGraphic(MarketplaceProduct product)
	{
		int graphicId = product.getPlayerGraphicId();
		Player player = client.getLocalPlayer();

		// guard: make sure the graphic is valid
		if (graphicId < 0)
		{
			return;
		}

		player.setGraphic(graphicId);
		player.setSpotAnimFrame(0);
	}

	private void triggerProductPlayerAnimation(MarketplaceProduct product)
	{
		int animationId = product.getPlayerAnimationId();
		Player player = client.getLocalPlayer();

		// guard: make sure the animation is valid
		if (animationId < 0)
		{
			return;
		}

		player.setAnimation(animationId);
	}

	private ArrayList<MarketplaceSpawnedObject> spawnProductObjects(MarketplaceProduct product)
	{
		final ArrayList<MarketplaceSpawnedObject> allObjects = new ArrayList();
		final ArrayList<MarketplaceSpawnPoint> spawnPoints = new ArrayList();

		// if there is no spawn point customizer we will spawn one at the player location
		if (!hasSpawnPoints)
		{
			final MarketplaceSpawnPoint defaultSpawnPoint = getOutwardSpawnPoint(1, 2, 10, null);

			if (defaultSpawnPoint == null)
			{
				return allObjects;
			}

			spawnPoints.add(defaultSpawnPoint);
		} else {
			spawnPoints.addAll(getSpawnPoints.generate(this));
		}

		// first update the settings that need to be used
		if (hasCustomizeSettings)
		{
			customizeSettings.execute(product);
		}

		final MarketplaceModel[][] candidateMarketplaceModels = product.getMarketplaceModels();
		final boolean useSpawners = product.isUseSpawners();
		final int spawnerDurationMs = product.getSpawnerDurationMs();
		final int randomSpawnDelayMs = product.getRandomSpawnDelayMs();

		// guard: check if there are any candidates
		// this prevents the below candidate randomizer to trigger errors
		if (candidateMarketplaceModels.length <= 0)
		{
			return allObjects;
		}

		// loop all the requested spawn points
		for (MarketplaceSpawnPoint spawnPoint : spawnPoints)
		{
			final ArrayList<MarketplaceSpawnedObject> objects = new ArrayList();
			final ArrayList<ModelData> models = new ArrayList();
			final int spawnDelayMs = (int) (Math.random() * randomSpawnDelayMs);
			final Random marketplaceModelsSelector = new Random();
			final int marketplaceModelsIndex = marketplaceModelsSelector.nextInt(candidateMarketplaceModels.length);
			final MarketplaceModel[] marketplaceModels = candidateMarketplaceModels[marketplaceModelsIndex];

			// guard: make sure the spawn point is valid, it can happen no valid tile
			// could be found resulting in a `null` spawn point
			if (spawnPoint == null)
			{
				continue;
			}

			// loop all the models that need to be placed
			for (MarketplaceModel marketplaceModel : marketplaceModels)
			{
				final int modelId = marketplaceModel.getModelId();
				final int animationId = marketplaceModel.getAnimationId();
				final int animationDurationMs = marketplaceModel.getAnimationDurationMs();
				final boolean hasAnimation = animationId > 0;
				final boolean shouldResetAnimation = animationDurationMs >= 0;
				final int resetAnimationDelayMs = spawnDelayMs + (useSpawners ? spawnerDurationMs : 0) + animationDurationMs;

				RuneLiteObject object = client.createRuneLiteObject();
				ModelData model = client.loadModelData(modelId)
					.cloneVertices()
					.cloneColors();
				MarketplaceSpawnedObject spawnedObject = new MarketplaceSpawnedObject(
					client,
					object,
					marketplaceModel,
					spawnPoint,
					product
				);

				// check if the model needs further customization (e.g. recolors)
				// this needs to be done before applying the light to the model
				if (hasModelCustomizer)
				{
					customizeModel.execute(model, modelId);
				}

				// set the object to the model
				object.setModel(model.light());

				// move to the spawn location
				object.setLocation(spawnPoint.getLocalPoint(), spawnPoint.getPlane());

				// play object animations if they are set
				if (hasAnimation) {
					Animation objectAnimation = client.loadAnimation(animationId);
					object.setAnimation(objectAnimation);
					object.setShouldLoop(true);

					if (shouldResetAnimation)
					{
						scheduleAnimationReset(object, resetAnimationDelayMs);
					}
				}

				// add each object and model
				objects.add(spawnedObject);
				models.add(model);
			}

			// random rotation for all models
			// NOTE: important to rotate them all the same if you have multiple models making up one object
			rotateModelsRandomly(models);

			// check if the spawners cutscene applies for this product
			if (!useSpawners) {
				scheduleShowObjects(objects, spawnDelayMs);
			}
			else if (product != spawnerProduct)
			{
				ArrayList<MarketplaceSpawnedObject> spawnerObjects = spawnProductObjects(spawnerProduct);

				// move them all to the location of the final object
				for (MarketplaceSpawnedObject spawnerObject : spawnerObjects)
				{
					spawnerObject.getObject().setLocation(spawnPoint.getLocalPoint(), spawnPoint.getPlane());
				}

				scheduleShowObjects(spawnerObjects, spawnDelayMs);
				scheduleShowObjects(objects, spawnDelayMs + spawnerDurationMs);
				scheduleResetObjects(spawnerObjects, spawnDelayMs + spawnerDurationMs);

				// register the spawners as well
				objects.addAll(spawnerObjects);
			}

			// add all the objects for this spawn point
			allObjects.addAll(objects);
		}

		registerSpawnedObjects(allObjects);
		return allObjects;
	}

	private void registerSpawnedObjects(ArrayList<MarketplaceSpawnedObject> objects)
	{
		Iterator iterator = objects.iterator();

		while (iterator.hasNext())
		{
			MarketplaceSpawnedObject spawnedObject = (MarketplaceSpawnedObject) iterator.next();
			LocalPoint localPoint = spawnedObject.getSpawnPoint().getLocalPoint();
			WorldPoint worldPoint = WorldPoint.fromLocal(client, localPoint);

			// check whether this world point already has a spawned object to add to
			if (registeredSpawnedObjects.containsKey(worldPoint)) {
				CopyOnWriteArrayList<MarketplaceSpawnedObject> safeObjects = registeredSpawnedObjects.get(worldPoint);
				safeObjects.add(spawnedObject);
			} else {
				CopyOnWriteArrayList<MarketplaceSpawnedObject> safeObjects = new CopyOnWriteArrayList();
				safeObjects.add(spawnedObject);
				registeredSpawnedObjects.put(worldPoint, safeObjects);
			}
		}
	}

	private void rotateModelsRandomly(ArrayList<ModelData> models)
	{
		final double random = Math.random();

		if (random < 0.25) {
			for (ModelData model : models) {
				model.rotateY90Ccw();
			}
		} else if (random < 0.5) {
			for (ModelData model : models) {
				model.rotateY180Ccw();
			}
		} else if (random < 0.75) {
			for (ModelData model : models) {
				model.rotateY270Ccw();
			}
		} else {
			// no rotation
		}
	}

	private void scheduleShowObjects(ArrayList<MarketplaceSpawnedObject> spawnedObjects, long delayMs)
	{
		plugin.scheduleOnClientThread(() -> {
			for (MarketplaceSpawnedObject spawnedObject : spawnedObjects) {
				spawnedObject.show();
			}
		}, delayMs);
	}

	private void scheduleResetObjects(ArrayList<MarketplaceSpawnedObject> spawnedObjects, long delayMs)
	{
		plugin.scheduleOnClientThread(() -> {
			for (MarketplaceSpawnedObject spawnedObject : spawnedObjects) {
				spawnedObject.hide();
				spawnedObject.getObject().setModel(null);
			}
		}, delayMs);
	}

	private void scheduleAnimationReset(RuneLiteObject object, long delayMs)
	{
		plugin.scheduleOnClientThread(() -> {
			object.setShouldLoop(false);
			object.setAnimation(null);
		}, delayMs);
	}

	public MarketplaceSpawnPoint getOutwardSpawnPoint(int startRadius, int radiusStepSize, int maxRadius, HashMap<WorldPoint, MarketplaceSpawnPoint> blacklistedSpawnPoints)
	{
		for (int radius = startRadius; radius < maxRadius; radius += radiusStepSize)
		{
			int randomizedRadius = radius + ((int) (Math.random() * radiusStepSize));
			int usedRadius = Math.min(randomizedRadius, maxRadius);
			MarketplaceSpawnPoint candidateSpawnPoint = getSpawnPoint(usedRadius, blacklistedSpawnPoints);

			if (candidateSpawnPoint != null) {
				return candidateSpawnPoint;
			}
		}

		return null;
	}

	public MarketplaceSpawnPoint getSpawnPoint(int radius)
	{
		return getSpawnPoint(radius);
	}

	public MarketplaceSpawnPoint getSpawnPoint(int radius, HashMap<WorldPoint, MarketplaceSpawnPoint> blacklistedSpawnPoints)
	{
		final ArrayList<MarketplaceSpawnPoint> candidateSpawnPoints = new ArrayList();
		final LocalPoint playerLocalPoint = client.getLocalPlayer().getLocalLocation();
		final int playerPlane = client.getPlane();
		final int[][] collisionFlags = getSceneCollisionFlags();
		final int sceneX = playerLocalPoint.getSceneX();
		final int sceneY = playerLocalPoint.getSceneY();

		// loop all the possible tiles for the requested radius and look for
		// the candidate tiles to spawn the object on
		for (int deltaX = -1 * radius; deltaX <= radius; deltaX++) {
			for (int deltaY = -1 * radius; deltaY <= radius; deltaY++) {
				int sceneAttemptX = sceneX + deltaX;
				int sceneAttemptY = sceneY + deltaY;

				// guard: make sure the flag can be found
				if (
					sceneAttemptX < 0
					|| sceneAttemptX >= collisionFlags.length
					|| sceneAttemptY < 0
					|| sceneAttemptY >= collisionFlags[sceneAttemptX].length
				) {
					continue;
				}

				int flagData = collisionFlags[sceneAttemptX][sceneAttemptY];
				int blockedFlags = CollisionDataFlag.BLOCK_MOVEMENT_FULL;

				// guard: check if this tile is not walkable
				if ((flagData & blockedFlags) != 0)
				{
					continue;
				}

				LocalPoint localPoint = LocalPoint.fromScene(sceneAttemptX, sceneAttemptY);
				WorldPoint worldPoint = WorldPoint.fromLocal(client, localPoint);

				// guard: check if this world point is already taken by another spawned object
				if (registeredSpawnedObjects.containsKey(worldPoint))
				{
					continue;
				}

				// guard: check if blacklisted manually
				if (blacklistedSpawnPoints != null && blacklistedSpawnPoints.containsKey(worldPoint))
				{
					continue;
				}

				// we have found a walkable tile to spawn the object on
				candidateSpawnPoints.add(new MarketplaceSpawnPoint(localPoint, worldPoint, playerPlane));
			}
		}

		// guard: make sure there are candidate spawn points to prevent the
		// random selection of one to trigger errors
		if (candidateSpawnPoints.size() <= 0)
		{
			return null;
		}

		final Random spawnPointSelector = new Random();
		final int spawnPointIndex = spawnPointSelector.nextInt(candidateSpawnPoints.size());
		final MarketplaceSpawnPoint spawnPoint = candidateSpawnPoints.get(spawnPointIndex);

		return spawnPoint;
	}

	private int[][] getSceneCollisionFlags() {
		final CollisionData[] collisionMaps = client.getCollisionMaps();
		int[][] collisionFlags = new int[Constants.SCENE_SIZE][Constants.SCENE_SIZE];

		// if we have map collision flags we populate the starting point with them
		if (collisionMaps != null) {
			collisionFlags = collisionMaps[client.getPlane()].getFlags();
		}

		return collisionFlags;
	}

	public void spawnTestObject(int modelId, int animationId)
	{
		if (modelId <= 0) {
			return;
		}

		boolean hasAnimation = animationId > 0;
		MarketplaceSpawnPoint spawnPoint = getSpawnPoint(5);
		RuneLiteObject object = client.createRuneLiteObject();
		ModelData model = client.loadModelData(modelId)
				.cloneVertices()
				.cloneColors();

		if (spawnPoint == null)
		{
			return;
		}

		object.setModel(model.light());
		object.setLocation(spawnPoint.getLocalPoint(), spawnPoint.getPlane());
		if (hasAnimation) {
			Animation objectAnimation = client.loadAnimation(animationId);
			object.setAnimation(objectAnimation);
			object.setShouldLoop(true);
		}

		object.setActive(true);
	}
}
