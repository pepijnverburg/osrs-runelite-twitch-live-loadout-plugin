package com.twitchliveloadout.marketplace.products;

import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.marketplace.MarketplaceConfigGetters;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.animations.AnimationManager;
import com.twitchliveloadout.marketplace.spawns.SpawnPoint;
import com.twitchliveloadout.marketplace.spawns.SpawnedObject;
import com.twitchliveloadout.marketplace.spawns.SpawnManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

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
	private final TwitchTransaction transaction;

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
	 * Current status
	 */
	@Getter
	private boolean isActive = false;

	/**
	 * Long-term interval trackers
	 */
	private Instant lastSpawnBehaviour;
	private int spawnBehaviourCounter = 0;
	private Instant lastInterfaceEffect;
	private int interfaceEffectCounter = 0;
	private HashMap<SpawnedObject, Instant> lastRandomAnimations = new HashMap();

	/**
	 * A list of all the spawned objects for this product
	 */
	@Getter
	private final CopyOnWriteArrayList<SpawnedObject> spawnedObjects = new CopyOnWriteArrayList();

	public MarketplaceProduct(MarketplaceManager manager, TwitchTransaction transaction, EbsProduct ebsProduct, StreamerProduct streamerProduct)
	{
		this.manager = manager;
		this.transaction = transaction;
		this.ebsProduct = ebsProduct;
		this.streamerProduct = streamerProduct;

		// start immediately
		start();
	}

	public void handleBehaviour()
	{

		// guard: make sure the EBS product is active and valid
		if (!isActive || ebsProduct == null)
		{
			return;
		}

		handleSpawns();
		handleSpawnRandomAnimations();
		handleMovementAnimations();
//		handlePlayerEquipment();
//		handleInterfaceEffect();
	}

	public void onClientTick()
	{

		//guard: make sure the product is active
		if (!isActive)
		{
			return;
		}

		handleSpawnRotations();
		test();
	}

	public void start()
	{
		isActive = true;
	}

	public void pause()
	{
		isActive = false;
	}

	public void stop()
	{
		isActive = false;
	}

	public boolean hasMovementAnimations()
	{
		return ebsProduct.behaviour.playerAnimations != null;
	}

	public void handleMovementAnimations()
	{
		AnimationManager animationManager = manager.getAnimationManager();

		// guard: skip when no movement animations
		if (!hasMovementAnimations())
		{
			return;
		}

		EbsMovementAnimations movementAnimations = ebsProduct.behaviour.playerAnimations;

		// update the animation manager
		animationManager.setCurrentMovementAnimations(movementAnimations);
		animationManager.updateEffectAnimations();
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
		for (SpawnedObject spawnedObject : spawnedObjects)
		{
			EbsModelSet modelSet = spawnedObject.getModelSet();
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

		for (SpawnedObject spawnedObject : spawnedObjects)
		{
			Instant lastRandomAnimationAt = lastRandomAnimations.get(spawnedObject);
			EbsSpawn spawn = spawnedObject.getSpawn();
			EbsInterval randomInterval = spawn.randomAnimationInterval;
			ArrayList<EbsAnimation> randomAnimations = spawn.randomAnimations;

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
			EbsAnimation randomAnimation = MarketplaceConfigGetters.getRandomEntryFromList(randomAnimations);

			// trigger the animations on this single spawned object
			ArrayList<SpawnedObject> animatedSpawnedObjects = new ArrayList();
			animatedSpawnedObjects.add(spawnedObject);
			triggerAnimation(animatedSpawnedObjects, randomAnimation, 0);

			// TODO: increase the total amount it was triggered and check against max repeat amount.
		}
	}

	private void handleSpawns()
	{
		Instant now = Instant.now();
		String productId = ebsProduct.id;
		EbsBehaviour behaviour = ebsProduct.behaviour;
		ArrayList<EbsSpawnOption> spawnOptions = behaviour.spawnOptions;
		EbsInterval spawnInterval = behaviour.spawnInterval;

		// guard: check if objects need to be spawned
		if (spawnOptions == null)
		{
			return;
		}

		// make sure the behaviour interval is valid
		if (spawnInterval == null)
		{
			spawnInterval = new EbsInterval();
		}

		int repeatAmount = spawnInterval.repeatAmount;

		// guard: check if the amount has passed
		// NOTE: -1 repeat amount if infinity!
		if (repeatAmount >= 0 && spawnBehaviourCounter >= repeatAmount)
		{
			return;
		}

		// guard: check if the interval has not passed
		if (lastSpawnBehaviour != null && lastSpawnBehaviour.plusMillis(spawnInterval.delayMs).isAfter(now))
		{
			return;
		}

		// select a random option
		EbsSpawnOption spawnOption = getSpawnBehaviourByChance(spawnOptions);

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
		ArrayList<EbsSpawn> spawns = spawnOption.spawns;

		// guard: make sure the spawn behaviours are valid
		if (spawns == null)
		{
			log.error("Could not find valid spawn behaviours for product: "+ productId);
			return;
		}

		// execute the spawn for the requested amount of times along with all spawn behaviours
		for (int spawnIndex = 0; spawnIndex < spawnAmount; spawnIndex++)
		{
			for (EbsSpawn spawn : spawns)
			{
				int spawnDelayMs = (int) MarketplaceConfigGetters.getValidRandomNumberByRange(spawnOption.spawnDelayMs, 0, 0);
				triggerSpawn(spawn, spawnDelayMs);
			}
		}
	}

	private void triggerSpawn(EbsSpawn spawn, int spawnDelayMs)
	{

		// guard: make sure the spawn is valid
		if (spawn == null)
		{
			log.error("An invalid spawn object was passed when triggering spawn!");
			return;
		}

		Client client = manager.getClient();
		SpawnManager spawnManager = manager.getSpawnManager();
		ArrayList<SpawnedObject> newSpawnedObjects = new ArrayList();
		ArrayList<ModelData> newSpawnedModels = new ArrayList();

		EbsModelPlacement placement = spawn.modelPlacement;

		// make sure there are valid placement parameters
		if (placement == null)
		{
			placement = new EbsModelPlacement();
		}

		// find an available spawn point
		SpawnPoint spawnPoint = null;
		Integer radius = placement.radius;
		String radiusType = placement.radiusType;
		String locationType = placement.locationType;
		int validatedRadius = (radius == null) ? DEFAULT_RADIUS : radius;
		String validatedRadiusType = (radiusType == null) ? DEFAULT_RADIUS_TYPE : radiusType;
		String validatedLocationType = (locationType == null) ? CURRENT_TILE_LOCATION_TYPE : locationType;
		WorldPoint referenceWorldPoint = client.getLocalPlayer().getWorldLocation();

		// check if we should change the reference to the previous tile
		if (PREVIOUS_TILE_LOCATION_TYPE.equals(validatedLocationType))
		{
			referenceWorldPoint = spawnManager.getPreviousPlayerLocation();
			log.info("WORLD POOINT FOUND: "+ referenceWorldPoint);
			if (referenceWorldPoint == null)
			{
				return;
			}
		}

		if (OUTWARD_RADIUS_TYPE.equals(validatedRadiusType)) {
			spawnPoint = spawnManager.getOutwardSpawnPoint(validatedRadius, referenceWorldPoint);
		} else {
			spawnPoint = spawnManager.getSpawnPoint(validatedRadius, referenceWorldPoint);
		}

		// guard: make sure the spawn point is valid
		if (spawnPoint == null)
		{
			log.error("Could not find valid spawn point when triggering spawn behaviour!");
			return;
		}

		// roll a random set of model IDs
		EbsModelSet modelSet = MarketplaceConfigGetters.getRandomEntryFromList(spawn.modelSets);

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
			SpawnedObject spawnedObject = new SpawnedObject(
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
		spawnManager.registerSpawnedObjectPlacements(newSpawnedObjects);
	}

	private void triggerAnimation(ArrayList<SpawnedObject> spawnedObjects, EbsAnimation animation, int baseDelayMs)
	{

		// guard: make sure the animation is valid
		if (animation == null)
		{
			return;
		}

		triggerModelAnimations(spawnedObjects, animation.modelAnimation, baseDelayMs);
		triggerPlayerGraphic(animation.playerGraphic, baseDelayMs);
		triggerPlayerAnimation(animation.playerAnimation, baseDelayMs);
	}

	private void triggerModelAnimations(ArrayList<SpawnedObject> spawnedObjects, EbsAnimationFrame animation, int baseDelayMs)
	{
		handleAnimationFrame(animation, baseDelayMs, (animationId, startDelayMs) -> {
			scheduleSetAnimations(spawnedObjects, animationId, startDelayMs);
		}, (resetDelayMs) -> {
			scheduleResetAnimations(spawnedObjects, resetDelayMs);
		});
	}

	private void triggerPlayerGraphic(EbsAnimationFrame animation, int baseDelayMs)
	{
		handleAnimationFrame(animation, baseDelayMs, (graphicId, startDelayMs) -> {
			schedulePlayerGraphic(graphicId, startDelayMs);
		}, (resetDelayMs) -> {
			// empty, no need to reset one-time graphic
		});
	}

	private void triggerPlayerAnimation(EbsAnimationFrame animation, int baseDelayMs)
	{
		handleAnimationFrame(animation, baseDelayMs, (animationId, startDelayMs) -> {
			schedulePlayerAnimation(animationId, startDelayMs);
		}, (resetDelayMs) -> {
			// empty, no need to reset one-time animation
		});
	}

	private void handleAnimationFrame(EbsAnimationFrame animation, int baseDelayMs, StartAnimationHandler startHandler, ResetAnimationHandler resetHandler)
	{

		// guard: make sure the animation is valid
		if (animation == null)
		{
			return;
		}

		// guard: make sure there is an animation ID
		if (animation.id < 0)
		{
			return;
		}

		int animationId = animation.id;
		int delayMs = animation.delayMs;
		int durationMs = animation.durationMs;
		int startDelayMs = baseDelayMs + delayMs;
		int resetDelayMs = startDelayMs + durationMs;

		// schedule to start the animation
		startHandler.execute(animationId, startDelayMs);

		// only reset animations when max duration
		if (animation.durationMs != null) {
			resetHandler.execute(resetDelayMs);
		}
	}

	private interface StartAnimationHandler {
		public void execute(int animationId, int delayMs);
	}

	private interface ResetAnimationHandler {
		public void execute(int delayMs);
	}

	private EbsSpawnOption getSpawnBehaviourByChance(ArrayList<EbsSpawnOption> spawnBehaviourOptions)
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
			for (EbsSpawnOption option : spawnBehaviourOptions)
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

	private void schedulePlayerGraphic(int graphicId, long delayMs)
	{
		Client client = manager.getClient();
		Player player = client.getLocalPlayer();

		// guard: make sure the player is valid
		if (player == null)
		{
			return;
		}

		manager.getPlugin().scheduleOnClientThread(() -> {
			player.setGraphic(graphicId);
			player.setSpotAnimFrame(0);
		}, delayMs);
	}

	private void schedulePlayerAnimation(int animationId, long delayMs)
	{
		Client client = manager.getClient();
		Player player = client.getLocalPlayer();

		// guard: make sure the player is valid
		if (player == null)
		{
			return;
		}

		manager.getPlugin().scheduleOnClientThread(() -> {
			player.setAnimation(animationId);
			player.setAnimationFrame(0);
		}, delayMs);
	}

	private void scheduleShowObjects(ArrayList<SpawnedObject> spawnedObjects, long delayMs)
	{
		manager.getPlugin().scheduleOnClientThread(() -> {
			for (SpawnedObject spawnedObject : spawnedObjects) {
				spawnedObject.show();
			}
		}, delayMs);
	}

	private void scheduleHideObjects(ArrayList<SpawnedObject> spawnedObjects, long delayMs)
	{
		manager.getPlugin().scheduleOnClientThread(() -> {
			for (SpawnedObject spawnedObject : spawnedObjects) {
				spawnedObject.hide();
			}
		}, delayMs);
	}

	private void scheduleSetAnimations(ArrayList<SpawnedObject> spawnedObjects, int animationId, long delayMs)
	{
		manager.getPlugin().scheduleOnClientThread(() -> {
			for (SpawnedObject spawnedObject : spawnedObjects) {
				spawnedObject.setAnimation(animationId, true);
			}
		}, delayMs);
	}

	private void scheduleResetAnimations(ArrayList<SpawnedObject> spawnedObjects, long delayMs)
	{
		manager.getPlugin().scheduleOnClientThread(() -> {
			for (SpawnedObject spawnedObject : spawnedObjects) {
				spawnedObject.resetAnimation();
			}
		}, delayMs);
	}
}
