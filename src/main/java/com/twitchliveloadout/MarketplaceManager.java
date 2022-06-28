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
				spawnProduct(config.devMarketplaceProductSpawn());
			}
		});
	}

	private ArrayList<RuneLiteObject> spawnProduct(MarketplaceProduct product)
	{
		final ArrayList<RuneLiteObject> objects = new ArrayList();
		final ArrayList<ModelData> models = new ArrayList();
		final MarketplaceModel[] marketplaceModels = product.getMarketplaceModels();
		final MarketplaceProduct.CustomizeModel customizeModel = product.getCustomizeModel();
		final MarketplaceProduct.GetSpawnPoints getSpawnPoints = product.getGetSpawnPoints();
		final MarketplaceProduct.CustomizeSettings customizeSettings = product.getCustomizeSettings();
		final boolean hasModelCustomizer = customizeModel != null;
		final boolean hasSpawnPoints = getSpawnPoints != null;
		final boolean hasCustomizeSettings = customizeSettings != null;
		final boolean useSpawners = product.isUseSpawners();
		final int spawnerDurationMs = product.getSpawnerDurationMs();
		final ArrayList<MarketplaceSpawnPoint> spawnPoints = new ArrayList();

		// if there is no spawn point customizer we will spawn one at the player location
		if (!hasSpawnPoints)
		{
			spawnPoints.add(new MarketplaceSpawnPoint(client.getLocalPlayer().getLocalLocation(), client.getPlane()));
		} else {
			spawnPoints.addAll(getSpawnPoints.generate(this));
		}

		// first update the settings that need to be used
		if (hasCustomizeSettings)
		{
			customizeSettings.execute(product);
		}

		// loop all the models that need to be placed
		for (MarketplaceModel marketplaceModel : marketplaceModels)
		{

			// loop all the requested spawn points
			for (MarketplaceSpawnPoint spawnPoint : spawnPoints)
			{
				final int modelId = marketplaceModel.getModelId();
				final int animationId = marketplaceModel.getAnimationId();
				final int animationDurationMs = marketplaceModel.getAnimationDurationMs();
				final boolean hasAnimation = animationId > 0;
				final boolean shouldResetAnimation = animationDurationMs >= 0;
				final int resetAnimationDelayMs = (useSpawners ? spawnerDurationMs : 0) + animationDurationMs;

				RuneLiteObject object = client.createRuneLiteObject();
				ModelData model = client.loadModelData(modelId)
					.cloneVertices()
					.cloneColors();

				// set the object to the model
				object.setModel(model.light());

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
			scheduleSpawn(objects, 0);
		}
		else if (product != spawnerProduct)
		{
			ArrayList<RuneLiteObject> spawnerObjects = spawnProduct(spawnerProduct);
			scheduleSpawn(spawnerObjects, 0);
			scheduleSpawn(objects, spawnerDurationMs);
			scheduleDespawn(spawnerObjects, spawnerDurationMs);

			// register the spawners as well
			objects.addAll(spawnerObjects);
		}

		return objects;
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

		if (id == 21262) {
			newObject.setAnimation(client.loadAnimation(5603));
		}

		newObject.setLocation(client.getLocalPlayer().getLocalLocation(), client.getPlane());
		newObject.setActive(true);
	}

	private void loadMarketplaceProductCache()
	{

	}

	public MarketplaceSpawnPoint getAvailableSpawnPoint()
	{
		return new MarketplaceSpawnPoint(client.getLocalPlayer().getLocalLocation(), client.getPlane());
	}

	public interface ClientThreadAction {
		public void execute();
	}
}
