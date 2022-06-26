package com.twitchliveloadout;

import com.google.inject.Inject;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MarketplaceManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;
	private final Client client;
	private final ClientThread clientThread;
	private final TwitchLiveLoadoutConfig config;
	private final ScheduledExecutorService executor;

	private final MarketplaceProduct spawnerProduct = MarketplaceProduct.GROUND_SPAWNING_PORTAL;
	private final int SPAWNER_DELAY_MS = 1000;

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

	private RuneLiteObject spawnProduct(MarketplaceProduct product)
	{
		int objectModelId = product.getObjectModelId();
		int objectAnimationId = product.getObjectAnimationId();
		MarketplaceProduct.CustomizeAction customize = product.getCustomizeAction();
		boolean hasObjectAnimation = objectAnimationId > 0;
		boolean hasCustomizer = customize != null;
		RuneLiteObject object = client.createRuneLiteObject();
		ModelData model = client.loadModelData(objectModelId)
			.cloneVertices()
			.cloneColors();

		object.setModel(model.light());

		// play object animations
		if (hasObjectAnimation) {
			Animation objectAnimation = client.loadAnimation(objectAnimationId);
			object.setAnimation(objectAnimation);
		}

		// random rotation
		rotateModelRandomly(model);

		if (hasCustomizer) {
			customize.execute(model, object);
		}

		// position under player
		object.setLocation(client.getLocalPlayer().getLocalLocation(), client.getPlane());

		// we start by spawning the spawn object to create a 'spawning' cutscene
		if (product != spawnerProduct)
		{
			RuneLiteObject spawnerObject = spawnProduct(spawnerProduct);
			scheduleSpawn(spawnerObject, 0);
			scheduleSpawn(object, SPAWNER_DELAY_MS);
			scheduleDespawn(spawnerObject, SPAWNER_DELAY_MS);
		}

		return object;
	}

	private void rotateModelRandomly(ModelData model)
	{
		double random = Math.random();

		if (random < 0.25) {
			model.rotateY90Ccw();
		} else if (random < 0.5) {
			model.rotateY180Ccw();
		} else if (random < 0.75) {
			model.rotateY270Ccw();
		} else {
			// nothing!
		}
	}

	private void scheduleSpawn(RuneLiteObject object, long delayMs)
	{
		scheduleOnClientThread(() -> {
			object.setActive(true);
		}, delayMs);
	}

	private void scheduleDespawn(RuneLiteObject object, long delayMs)
	{
		scheduleOnClientThread(() -> {
			object.setActive(false);
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
						action.execute();
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
			int rgb = 255;
			rgb = (rgb << 8) + 0;
			rgb = (rgb << 8) + 0;
			model.recolor(model.getFaceColors()[0], JagexColor.rgbToHSL(rgb, 1.0d));

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

	public interface ClientThreadAction {
		public void execute();
	}
}
