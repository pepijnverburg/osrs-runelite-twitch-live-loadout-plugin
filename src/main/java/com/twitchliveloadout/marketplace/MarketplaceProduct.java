package com.twitchliveloadout.marketplace;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class MarketplaceProduct
{

	/**
	 * The Marketplace manager
	 */
	private final MarketplaceManager manager;

	/**
	 * The Twitch transaction attributed to this product
	 */
	@Getter
	private final ExtensionTransaction transaction;

	/**
	 * The Twitch transaction attributed to this product
	 */
	@Getter
	private final EbsProduct ebsProduct;

	/**
	 * The Twitch transaction attributed to this product
	 */
	@Getter
	private final StreamerProduct streamerProduct;

	/**
	 * Long-term interval trackers
	 */
	private Instant lastSpawnBehaviour;
	private int spawnBehaviourCounter = 0;
	private Instant lastInterfaceEffect;
	private int interfaceEffectCounter = 0;

	/**
	 * A list of all the spawned objects for this product
	 */
	@Getter
	private final CopyOnWriteArrayList<MarketplaceSpawnedObject> spawnedObjects = new CopyOnWriteArrayList();

	public MarketplaceProduct(MarketplaceManager manager, ExtensionTransaction transaction, EbsProduct ebsProduct, StreamerProduct streamerProduct)
	{
		this.manager = manager;
		this.transaction = transaction;
		this.ebsProduct = ebsProduct;
		this.streamerProduct = streamerProduct;
	}

	public void start()
	{

	}

	public void pause()
	{

	}

	public void stop()
	{

	}

	public void onGameTick()
	{

		// guard: make sure the EBS product is valid
		if (ebsProduct == null)
		{
			return;
		}

		handleSpawnBehaviour();
//		handlePlayerAnimation();
//		handlePlayerEquipment();
//		handleInterfaceEffect();
	}

	private void handleSpawnBehaviour()
	{
		Instant now = Instant.now();
		String productId = ebsProduct.id;
		EbsProduct.Behaviour behaviour = ebsProduct.behaviour;
		ArrayList<EbsProduct.SpawnOption> spawnOptions = behaviour.spawnOptions;
		EbsProductInterval spawnInterval = behaviour.spawnInterval;

		// guard: check if objects need to be spawned
		if (spawnOptions == null)
		{
			log.error("Could not find valid spawn behaviour options for product: "+ productId);
			return;
		}

		// make sure the behaviour interval is valid
		if (spawnInterval == null)
		{
			spawnInterval = MarketplaceConfigGetters.generateDefaultInterval();
		}

		Integer repeatAmount = spawnInterval.repeatAmount;
		int validatedRepeatAmount = (repeatAmount == null ? 1 : repeatAmount);

		// guard: check if the amount has passed
		if (spawnBehaviourCounter >= validatedRepeatAmount)
		{
			return;
		}

		// guard: check if the interval has not passed
		if (lastSpawnBehaviour != null && lastSpawnBehaviour.plusMillis(spawnInterval.delayMs).isAfter(now))
		{
			return;
		}

		// select a random option
		EbsProduct.SpawnOption spawnOption = getSpawnBehaviourByChance(spawnOptions);

		// guard: check if a valid option was selected
		if (spawnOption == null)
		{
			log.error("Could not find valid spawn behaviour option for product: "+ productId);
			return;
		}

		// an option is selected so we can change the timer and count
		log.info("Executing spawn behaviours for product, because they are valid: "+ productId);
		lastSpawnBehaviour = now;
		spawnBehaviourCounter += 1;

		// randomize the amount of spawns
		int spawnAmount = (int) MarketplaceConfigGetters.getValidRandomNumberByRange(spawnOption.spawnAmount, 1, 1);
		ArrayList<EbsProduct.Spawn> spawns = spawnOption.spawns;

		// guard: make sure the spawn behaviours are valid
		if (spawns == null)
		{
			log.error("Could not find valid spawn behaviours for product: "+ productId);
			return;
		}

		// execute the spawn for the requested amount of times along with all spawn behaviours
		for (int spawnIndex = 0; spawnIndex < spawnAmount; spawnIndex++)
		{
			for (EbsProduct.Spawn spawn : spawns)
			{
				int spawnDelayMs = (int) MarketplaceConfigGetters.getValidRandomNumberByRange(spawnOption.spawnDelayMs, 0, 0);
				triggerSpawn(spawn, spawnDelayMs);
			}
		}
	}

	private void triggerSpawn(EbsProduct.Spawn spawn, int spawnDelayMs)
	{

		// guard: make sure the spawn is valid
		if (spawn == null)
		{
			log.error("An invalid spawn object was passed when triggering spawn!");
			return;
		}

		EbsModelPlacement placement = spawn.modelPlacement;
		EbsProduct.Animation showAnimation = spawn.showAnimation;
		Client client = manager.getClient();
		ArrayList<MarketplaceSpawnedObject> spawnedObjects = new ArrayList();
		ArrayList<ModelData> spawnedModels = new ArrayList();

		// make sure there are valid placement parameters
		if (placement == null)
		{
			placement = MarketplaceConfigGetters.generateDefaultModelPlacement();
		}

		// find an available spawn point
		MarketplaceSpawnPoint spawnPoint;
		Integer radius = placement.radius;
		int validatedRadius = (radius == null) ? DEFAULT_RADIUS : radius;
		if (placement.radiusType == OUTWARD_RADIUS_TYPE) {
			spawnPoint = manager.getOutwardSpawnPoint(validatedRadius);
		} else {
			spawnPoint = manager.getSpawnPoint(validatedRadius);
		}

		// guard: make sure the spawn point is valid
		if (spawnPoint == null)
		{
			log.error("Could not find valid spawn point when triggering spawn behaviour!");
			return;
		}

		// roll a random set of model IDs
		EbsProduct.ModelSet modelSet = MarketplaceConfigGetters.getRandomModelSet(spawn.modelSets);

		// guard: make sure the selected model is valid
		if (modelSet == null || modelSet.modelIds == null)
		{
			log.error("Could not find valid model set when triggering spawn behaviour!");
			return;
		}

		// loop all the individual model IDs making up this model
		for (int modelId : modelSet.modelIds)
		{
			RuneLiteObject runeLiteObject = client.createRuneLiteObject();
			ModelData modelData = client.loadModelData(modelId)
				.cloneVertices()
				.cloneColors();

			MarketplaceSpawnedObject spawnedObject = new MarketplaceSpawnedObject(
				client,
				runeLiteObject,
				spawnPoint,
				this
			);

			// TODO: do any recolours here, this needs to be done before light!

			// set the object to the model
			runeLiteObject.setModel(modelData.light());

			// move to the spawn location
			runeLiteObject.setLocation(spawnPoint.getLocalPoint(), spawnPoint.getPlane());

			// keep track of all the models and spawned objects that were placed
			spawnedModels.add(modelData);
			spawnedObjects.add(spawnedObject);
		}

		// perform any other modifications to the models that need to be done for all
		// the spawned models in the same way (e.g. when there is a combination of models for one entity)
		MarketplaceModelUtilities.scaleModels(spawnedModels, modelSet);
		MarketplaceModelUtilities.rotateModels(spawnedModels, modelSet);

		// get all animations we might need to trigger
		EbsProductAnimationFrame modelAnimation = MarketplaceConfigGetters.getValidAnimationFrame(showAnimation.modelAnimation);

		// only set animations when found
		if (modelAnimation.id != null) {
			int delayMs = modelAnimation.delayMs;
			int durationMs = modelAnimation.durationMs;
			int startAfterMs = spawnDelayMs + delayMs;
			int resetAfterMs = startAfterMs + durationMs;

			// schedule to start the animation
			scheduleSetAnimations(spawnedObjects, modelAnimation.id, startAfterMs);

			// only reset animations when max duration
			if (modelAnimation.durationMs != null) {
				scheduleResetAnimations(spawnedObjects, resetAfterMs);
			}
		}

		// schedule showing of the objects
		scheduleShowObjects(spawnedObjects, spawnDelayMs);

		// register the objects to the manager to make the spawn point unavailable
		manager.registerSpawnedObjectPlacements(spawnedObjects);
	}

	private EbsProduct.SpawnOption getSpawnBehaviourByChance(ArrayList<EbsProduct.SpawnOption> spawnBehaviourOptions)
	{
		int attempts = 0;
		int maxAttempts = 50;

		// guard: make sure there are any options
		if (spawnBehaviourOptions == null || spawnBehaviourOptions.size() < 0)
		{
			return null;
		}

		// roll for x amount of times to select the option
		// TODO: see how this impacts the selection?
		while (attempts++ < maxAttempts)
		{
			for (EbsProduct.SpawnOption option : spawnBehaviourOptions)
			{
				Double roll = Math.random();
				Double chance = option.chance;

				// choose this option when the chance is not known or when the roll landed
				if (chance == null || roll <= chance)
				{
					return option;
				}
			}
		}

		return spawnBehaviourOptions.get(0);
	}

	private void scheduleShowObjects(ArrayList<MarketplaceSpawnedObject> spawnedObjects, long delayMs)
	{
		manager.getPlugin().scheduleOnClientThread(() -> {
			for (MarketplaceSpawnedObject spawnedObject : spawnedObjects) {
				spawnedObject.show();
			}
		}, delayMs);
	}

	private void scheduleHideObjects(ArrayList<MarketplaceSpawnedObject> spawnedObjects, long delayMs)
	{
		manager.getPlugin().scheduleOnClientThread(() -> {
			for (MarketplaceSpawnedObject spawnedObject : spawnedObjects) {
				spawnedObject.hide();
				spawnedObject.getObject().setModel(null);
			}
		}, delayMs);
	}

	private void scheduleSetAnimations(ArrayList<MarketplaceSpawnedObject> spawnedObjects, int animationId, long delayMs)
	{
		manager.getPlugin().scheduleOnClientThread(() -> {
			Animation objectAnimation = manager.getClient().loadAnimation(animationId);

			for (MarketplaceSpawnedObject spawnedObject : spawnedObjects) {
				RuneLiteObject object = spawnedObject.getObject();
				object.setShouldLoop(true);
				object.setAnimation(objectAnimation);
			}
		}, delayMs);
	}

	private void scheduleResetAnimations(ArrayList<MarketplaceSpawnedObject> spawnedObjects, long delayMs)
	{
		manager.getPlugin().scheduleOnClientThread(() -> {
			for (MarketplaceSpawnedObject spawnedObject : spawnedObjects) {
				RuneLiteObject object = spawnedObject.getObject();
				object.setShouldLoop(false);
				object.setAnimation(null);
			}
		}, delayMs);
	}
}
