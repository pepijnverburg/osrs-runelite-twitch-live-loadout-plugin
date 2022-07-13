package com.twitchliveloadout;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.ClientThread;

import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MarketplaceManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;

	private final Client client;
	private final ClientThread clientThread;
	private final TwitchLiveLoadoutConfig config;
	private final ScheduledExecutorService executor;

	private final MarketplaceProduct spawnerProduct = MarketplaceProduct.GROUND_SPAWNING_PORTAL;

	private final int MAX_FIND_SPAWN_POINT_ATTEMPTS = 50;

	public MarketplaceManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client, ClientThread clientThread, TwitchLiveLoadoutConfig config, ScheduledExecutorService executor)
	{
		this.plugin = plugin;
		this.twitchState = twitchState;
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
		this.executor = executor;

		loadMarketplaceProductCache();
	}

	public void applyProducts()
	{
		// for testing
		clientThread.invokeLater(new Runnable() {
			@Override
			public void run() {
//				spawnTest();
				spawnProduct(config.devMarketplaceProductSpawn());
			}
		});
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
			spawnPoints.add(new MarketplaceSpawnPoint(
				client.getLocalPlayer().getLocalLocation(),
				client.getPlane()
			));
		} else {
			spawnPoints.addAll(getSpawnPoints.generate(this));
		}

		// first update the settings that need to be used
		if (hasCustomizeSettings)
		{
			customizeSettings.execute(product);
		}

		final MarketplaceModel[] marketplaceModels = product.getMarketplaceModels();
		final boolean useSpawners = product.isUseSpawners();
		final int spawnerDurationMs = product.getSpawnerDurationMs();
		final int randomSpawnDelayMs = product.getRandomSpawnDelayMs();

		// loop all the requested spawn points
		for (MarketplaceSpawnPoint spawnPoint : spawnPoints)
		{
			final ArrayList<RuneLiteObject> objects = new ArrayList();
			final ArrayList<ModelData> models = new ArrayList();
			final int spawnDelayMs = (int) (Math.random() * randomSpawnDelayMs);

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
			// NOTE: important to rotate them all the same!
			rotateModelsRandomly(models);

			// check if the model needs further customization (e.g. recolors)
			if (hasModelCustomizer)
			{
				for (int modelIndex = 0; modelIndex < models.size(); modelIndex++)
				{
					ModelData model = models.get(modelIndex);
					customizeModel.execute(model, modelIndex);
				}
			}

			// check if the spawners cutscene applies for this product
			if (!useSpawners) {
				scheduleSpawn(objects, spawnDelayMs);
			}
			else if (product != spawnerProduct)
			{
				ArrayList<RuneLiteObject> spawnerObjects = spawnProduct(spawnerProduct);
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
		scheduleOnClientThread(() -> {
			for (RuneLiteObject object : objects) {
				object.setActive(true);
			}
		}, delayMs);
	}

	private void scheduleDespawn(ArrayList<RuneLiteObject> objects, long delayMs)
	{
		scheduleOnClientThread(() -> {
			for (RuneLiteObject object : objects) {
				object.setActive(false);
			}
		}, delayMs);
	}

	private void scheduleAnimationReset(RuneLiteObject object, long delayMs)
	{
		scheduleOnClientThread(() -> {
			object.setShouldLoop(false);
			object.setAnimation(null);
		}, delayMs);
	}

	private void scheduleOnClientThread(ClientThreadAction action, long delayMs)
	{
		executor.schedule(new Runnable() {
			@Override
			public void run() {
				clientThread.invokeLater(new Runnable() {
					@Override
					public void run() {
						try {
							action.execute();
						} catch (Exception exception) {
							log.warn("Could not execute action on client thread: ", exception);
						}
					}
				});
			}
		}, delayMs, TimeUnit.MILLISECONDS);
	}

	private void spawnTest()
	{
		// https://github.com/Maurits825/tob-light-colors/blob/master/src/main/java/com/toblightcolors/TobLightColorsPlugin.java
		System.out.println("Spawn object!");
		int id = config.devObjectSpawnId();
		RuneLiteObject newObject = client.createRuneLiteObject();
		ModelData model = client.loadModelData(id)
				.cloneVertices()
				.cloneColors();
//				.scale(50, 50, 50);

		try {


		} catch (Exception e) {
			// empty
		}

		newObject.setModel(model.light());

		newObject.setLocation(client.getLocalPlayer().getLocalLocation(), client.getPlane());
		newObject.setActive(true);
	}

	private void loadMarketplaceProductCache()
	{

	}

	public MarketplaceSpawnPoint getAvailableSpawnPoint(int maxRadius)
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
			int deltaX = -1 * maxRadius + (int) (Math.random() * maxRadius * 2);
			int deltaY = -1 * maxRadius + (int) (Math.random() * maxRadius * 2);
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

	public interface ClientThreadAction {
		public void execute();
	}
}
