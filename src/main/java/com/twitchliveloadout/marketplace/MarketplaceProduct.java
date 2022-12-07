package com.twitchliveloadout.marketplace;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
	private HashMap<MarketplaceSpawnedObject, Instant> lastRandomAnimations = new HashMap();

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

		handleSpawns();
		handleSpawnRandomAnimations();
//		handlePlayerAnimation();
//		handlePlayerEquipment();
//		handleInterfaceEffect();
	}

	public void onClientTick()
	{
		handleSpawnRotations();
		test();
	}

	public void test()
	{

//		for (MarketplaceSpawnedObject spawnedObject : spawnedObjects) {
//			LocalPoint targetPoint = manager.getClient().getLocalPlayer().getLocalLocation();
//			spawnedObject.rotateTowards(targetPoint);
//			spawnedObject.render();
//			int animationId = manager.getConfig().devObjectSpawnAnimationId();
//
//			if (animationId > 0)  {
//				spawnedObject.setAnimation(animationId, true);
//			}
//		}
	}

	private void handleSpawnRotations()
	{
		for (MarketplaceSpawnedObject spawnedObject : spawnedObjects)
		{
			EbsProduct.ModelSet modelSet = spawnedObject.getModelSet();
			String rotationType = modelSet.modelRotationType;
			Player player = manager.getClient().getLocalPlayer();

			// guard: make sure the rotation and player are valid
			if (rotationType == null || player == null)
			{
				continue;
			}

			if (rotationType.equals(PLAYER_ROTATION_TYPE))
			{
				LocalPoint targetPoint = player.getLocalLocation();
				spawnedObject.rotateTowards(targetPoint);
			}
			else if (rotationType.equals(INTERACTING_ROTATION_TYPE))
			{
				Actor interacting = player.getInteracting();

				if (interacting == null)
				{
					continue;
				}

				LocalPoint targetPoint = interacting.getLocalLocation();
				spawnedObject.rotateTowards(targetPoint);
			}
		}
	}

	private void handleSpawnRandomAnimations()
	{
		Instant now = Instant.now();

		for (MarketplaceSpawnedObject spawnedObject : spawnedObjects)
		{
			Instant lastRandomAnimationAt = lastRandomAnimations.get(spawnedObject);
			EbsProduct.Spawn spawn = spawnedObject.getSpawn();
			EbsProductInterval randomInterval = spawn.randomAnimationInterval;
			ArrayList<EbsProduct.Animation> randomAnimations = spawn.randomAnimations;

			// guard: make sure there is a valid interval and animation
			if (randomInterval == null || randomAnimations == null || randomAnimations.size() <= 0)
			{
				continue;
			}

			// guard: check if enough time has passed
			if (lastRandomAnimationAt != null && lastRandomAnimationAt.plusMillis(randomInterval.delayMs).isAfter(now))
			{
				continue;
			}

			// update the last time it was attempted too roll and execute a random animation
			lastRandomAnimations.put(spawnedObject, now);

			// guard: skip when this is the first time the interval is triggered!
			// this prevents the random animation to instantly be triggered on spawn
			if (lastRandomAnimationAt == null)
			{
				continue;
			}

			// guard: skip this animation when not rolled, while setting the timer before this roll
			if (!MarketplaceConfigGetters.rollChance(randomInterval.chance))
			{
				continue;
			}

			// select a random entry from all the candidates
			EbsProduct.Animation randomAnimation = MarketplaceConfigGetters.getRandomEntryFromList(randomAnimations);

			// trigger the animations on this single spawned object
			ArrayList<MarketplaceSpawnedObject> animatedSpawnedObjects = new ArrayList();
			animatedSpawnedObjects.add(spawnedObject);
			triggerAnimation(animatedSpawnedObjects, randomAnimation, 0);

			// TODO: increase the total amount it was triggered and check against max repeat amount.
		}
	}

	private void handleSpawns()
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

		Client client = manager.getClient();
		ArrayList<MarketplaceSpawnedObject> newSpawnedObjects = new ArrayList();
		ArrayList<ModelData> newSpawnedModels = new ArrayList();

		EbsModelPlacement placement = spawn.modelPlacement;

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
		EbsProduct.ModelSet modelSet = MarketplaceConfigGetters.getRandomEntryFromList(spawn.modelSets);

		// guard: make sure the selected model is valid
		if (modelSet == null || modelSet.modelIds == null)
		{
			log.error("Could not find valid model set when triggering spawn behaviour!");
			return;
		}

		// get properties from model set
		boolean shouldScaleModel = (modelSet.modelScale != null);
		boolean shouldRotateModel = (RANDOM_ROTATION_TYPE.equals(modelSet.modelRotationType));
		double modelScale = MarketplaceConfigGetters.getValidRandomNumberByRange(modelSet.modelScale, 1, 1);
		double modelRotationDegrees = MarketplaceConfigGetters.getValidRandomNumberByRange(modelSet.modelRotation, 0, 360);

		// loop all the individual model IDs making up this model
		for (int modelId : modelSet.modelIds)
		{
			RuneLiteObject runeLiteObject = client.createRuneLiteObject();
			ModelData modelData = client.loadModelData(modelId);
			MarketplaceSpawnedObject spawnedObject = new MarketplaceSpawnedObject(
			this,
				client,
				runeLiteObject,
				modelData,
				spawnPoint,
				spawn,
				modelSet
			);

			// TODO: do any recolours here

			if  (shouldScaleModel)
			{
				spawnedObject.scale(modelScale);
			}

			if (shouldRotateModel)
			{
				spawnedObject.rotate(modelRotationDegrees);
			}

			// set the object to the model
			spawnedObject.render();

			// reset the animations to it will immediately show the idle animation if available
			spawnedObject.resetAnimation();

			// keep track of all the models and spawned objects that were placed
			newSpawnedModels.add(modelData);
			newSpawnedObjects.add(spawnedObject);
		}

		// trigger any animations played on show
		triggerAnimation(newSpawnedObjects, spawn.showAnimation, spawnDelayMs);

		// schedule showing of the objects
		scheduleShowObjects(newSpawnedObjects, spawnDelayMs);

		// register the objects to the product and manager to make the spawn point unavailable
		spawnedObjects.addAll(newSpawnedObjects);
		manager.registerSpawnedObjectPlacements(newSpawnedObjects);
	}

	private void triggerAnimation(ArrayList<MarketplaceSpawnedObject> spawnedObjects, EbsProduct.Animation animation, int baseDelayMs)
	{

		// guard: make sure the animation is valid
		if (animation == null)
		{
			return;
		}

		// TODO: trigger player animation and player graphics here!
		triggerModelAnimations(spawnedObjects, animation.modelAnimation, baseDelayMs);
	}

	private void triggerModelAnimations(ArrayList<MarketplaceSpawnedObject> spawnedObjects, EbsProductAnimationFrame animation, int baseDelayMs)
	{

		// guard: make sure the animation is valid
		if (animation == null)
		{
			return;
		}

		EbsProductAnimationFrame modelAnimation = MarketplaceConfigGetters.getValidAnimationFrame(animation);

		// guard: make sure there is an animation ID
		if (modelAnimation.id == null)
		{
			return;
		}

		int delayMs = modelAnimation.delayMs;
		int durationMs = modelAnimation.durationMs;
		int startAfterMs = baseDelayMs + delayMs;
		int resetAfterMs = startAfterMs + durationMs;

		// schedule to start the animation
		scheduleSetAnimations(spawnedObjects, modelAnimation.id, startAfterMs);

		// only reset animations when max duration
		if (modelAnimation.durationMs != null) {
			scheduleResetAnimations(spawnedObjects, resetAfterMs);
		}
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

				// choose this option when the chance is not known or when the roll landed
				if (MarketplaceConfigGetters.rollChance(option.chance))
				{
					return option;
				}
			}
		}

		// get the first is no valid one is found
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
			}
		}, delayMs);
	}

	private void scheduleSetAnimations(ArrayList<MarketplaceSpawnedObject> spawnedObjects, int animationId, long delayMs)
	{
		manager.getPlugin().scheduleOnClientThread(() -> {
			for (MarketplaceSpawnedObject spawnedObject : spawnedObjects) {
				spawnedObject.setAnimation(animationId, true);
			}
		}, delayMs);
	}

	private void scheduleResetAnimations(ArrayList<MarketplaceSpawnedObject> spawnedObjects, long delayMs)
	{
		manager.getPlugin().scheduleOnClientThread(() -> {
			for (MarketplaceSpawnedObject spawnedObject : spawnedObjects) {
				spawnedObject.resetAnimation();
			}
		}, delayMs);
	}
}
