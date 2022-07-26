package com.twitchliveloadout.marketplace;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;

import java.util.ArrayList;
import java.util.Random;

@Slf4j
public class MarketplaceManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;

	@Getter
	private final Client client;
	private final TwitchLiveLoadoutConfig config;

	private final MarketplaceProduct spawnerProduct = MarketplaceProduct.GROUND_SPAWNING_PORTAL;

	private final int MAX_FIND_SPAWN_POINT_ATTEMPTS = 50;

	public MarketplaceManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client, TwitchLiveLoadoutConfig config)
	{
		this.plugin = plugin;
		this.twitchState = twitchState;
		this.client = client;
		this.config = config;

		loadMarketplaceProductCache();
	}

	public void applyProducts()
	{

		// guard: only apply the products when the player is logged in
		// we don't distinquish different accounts, all runelite clients
		// will spawn the same objects
		if (!plugin.isLoggedIn())
		{
			return;
		}

		// TMP: for testing
		plugin.runOnClientThread(() -> {
			applyProduct(config.devMarketplaceProductSpawn());
			spawnTestObject(config.devObjectSpawnModelId(), config.devObjectSpawnAnimationId());
		});
	}

	private void applyProduct(MarketplaceProduct product)
	{
		applyProductPlayerGraphic(product);
		applyProductPlayerAnimation(product);
		spawnProduct(product);
	}

	private void applyProductPlayerGraphic(MarketplaceProduct product)
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

	private void applyProductPlayerAnimation(MarketplaceProduct product)
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

	private ArrayList<RuneLiteObject> spawnProduct(MarketplaceProduct product)
	{
		final ArrayList<RuneLiteObject> allObjects = new ArrayList();
		final MarketplaceProduct.CustomizeModel customizeModel = product.getCustomizeModel();
		final MarketplaceProduct.GetSpawnPoints getSpawnPoints = product.getGetSpawnPoints();
		final MarketplaceProduct.CustomizeSettings customizeSettings = product.getCustomizeSettings();
		final boolean hasModelCustomizer = customizeModel != null;
		final boolean hasSpawnPoints = getSpawnPoints != null;
		final boolean hasCustomizeSettings = customizeSettings != null;
		final ArrayList<MarketplaceSpawnPoint> spawnPoints = new ArrayList();

		// if there is no spawn point customizer we will spawn one at the player location
		if (!hasSpawnPoints)
		{
			spawnPoints.add(getOutwardSpawnPoint(1, 2, 10));
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
			final ArrayList<RuneLiteObject> objects = new ArrayList();
			final ArrayList<ModelData> models = new ArrayList();
			final int spawnDelayMs = (int) (Math.random() * randomSpawnDelayMs);
			final Random marketplaceModelsSelector = new Random();
			final int marketplaceModelsIndex = marketplaceModelsSelector.nextInt(candidateMarketplaceModels.length);
			final MarketplaceModel[] marketplaceModels = candidateMarketplaceModels[marketplaceModelsIndex];

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
				objects.add(object);
				models.add(model);
			}

			// random rotation for all models
			// NOTE: important to rotate them all the same if you have multiple models making up one object
			rotateModelsRandomly(models);

			// check if the spawners cutscene applies for this product
			if (!useSpawners) {
				scheduleSpawn(objects, spawnDelayMs);
			}
			else if (product != spawnerProduct)
			{
				ArrayList<RuneLiteObject> spawnerObjects = spawnProduct(spawnerProduct);

				// move them all to the location of the final object
				for (RuneLiteObject spawnerObject : spawnerObjects)
				{
					spawnerObject.setLocation(spawnPoint.getLocalPoint(), spawnPoint.getPlane());
				}

				scheduleSpawn(spawnerObjects, spawnDelayMs);
				scheduleSpawn(objects, spawnDelayMs + spawnerDurationMs);
				scheduleDespawn(spawnerObjects, spawnDelayMs + spawnerDurationMs);

				// register the spawners as well
				objects.addAll(spawnerObjects);
			}

			// add all the objects for this spawn point
			allObjects.addAll(objects);
		}

		return allObjects;
	}

	private void rotateModelsRandomly(ArrayList<ModelData> models)
	{
		double random = Math.random();

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

	private void scheduleSpawn(ArrayList<RuneLiteObject> objects, long delayMs)
	{
		plugin.scheduleOnClientThread(() -> {
			for (RuneLiteObject object : objects) {
				object.setActive(true);
			}
		}, delayMs);
	}

	private void scheduleDespawn(ArrayList<RuneLiteObject> objects, long delayMs)
	{
		plugin.scheduleOnClientThread(() -> {
			for (RuneLiteObject object : objects) {
				object.setActive(false);
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

	private void loadMarketplaceProductCache()
	{
		// TODO: later for objects that are persistent for longer periods of time across login sessions.
	}

	public MarketplaceSpawnPoint getOutwardSpawnPoint(int startRadius, int radiusStepSize, int maxRadius)
	{
		for (int radius = startRadius; radius < maxRadius; radius += radiusStepSize)
		{
			MarketplaceSpawnPoint candidateSpawnPoint = getSpawnPoint(radius);

			if (candidateSpawnPoint != null) {
				return candidateSpawnPoint;
			}
		}

		return null;
	}

	public MarketplaceSpawnPoint getSpawnPoint(int radius)
	{
		CollisionData[] collisionMaps = client.getCollisionMaps();
		LocalPoint playerLocation = client.getLocalPlayer().getLocalLocation();
		int playerPlane = client.getPlane();
		MarketplaceSpawnPoint defaultSpawnPoint = new MarketplaceSpawnPoint(playerLocation, playerPlane);

		// guard: make sure there is a collision map available
		if (collisionMaps == null) {
			return defaultSpawnPoint;
		}

		int[][] flags = collisionMaps[client.getPlane()].getFlags();

		// attempt an X amount of times before giving up finding a random spawn point
		for (int attemptIndex = 0; attemptIndex < MAX_FIND_SPAWN_POINT_ATTEMPTS; attemptIndex++) {
			int deltaX = -1 * radius + (int) (Math.random() * radius * 2);
			int deltaY = -1 * radius + (int) (Math.random() * radius * 2);
			int sceneAttemptX = playerLocation.getSceneX() + deltaX;
			int sceneAttemptY = playerLocation.getSceneY() + deltaY;
			int flagData = flags[sceneAttemptX][sceneAttemptY];
			int blockedFlags = CollisionDataFlag.BLOCK_MOVEMENT_FULL;

			// guard: check if this tile is not walkable
			if ((flagData & blockedFlags) != 0) {
				continue;
			}

			// we have found a walkable tile to spawn the object on
			return new MarketplaceSpawnPoint(LocalPoint.fromScene(sceneAttemptX, sceneAttemptY), playerPlane);
		}

		return defaultSpawnPoint;
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
