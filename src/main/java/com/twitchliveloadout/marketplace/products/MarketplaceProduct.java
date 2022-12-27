package com.twitchliveloadout.marketplace.products;

import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.marketplace.MarketplaceRandomizers;
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
import java.util.*;
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
	 * The Twitch product attributed to this product
	 */
	@Getter
	private final TwitchProduct twitchProduct;

	/**
	 * Current status
	 */
	@Getter
	private boolean isActive = false;

	/**
	 * Long-term interval trackers
	 */
	private Instant lastSpawnBehaviourAt;
	private int spawnBehaviourCounter = 0;
	private Instant lastInterfaceEffectAt;
	private int interfaceEffectCounter = 0;

	/**
	 * Expiration trackers
	 */
	@Getter
	private final Instant startedAt;
	@Getter
	private final Instant expiredAt;

	/**
	 * A list of all the spawned objects for this product
	 */
	@Getter
	private final CopyOnWriteArrayList<SpawnedObject> spawnedObjects = new CopyOnWriteArrayList();

	public MarketplaceProduct(MarketplaceManager manager, TwitchTransaction transaction, EbsProduct ebsProduct, StreamerProduct streamerProduct, TwitchProduct twitchProduct)
	{
		this.manager = manager;
		this.transaction = transaction;
		this.ebsProduct = ebsProduct;
		this.streamerProduct = streamerProduct;
		this.twitchProduct = twitchProduct;

		// determine when this product should expire which is
		// based on the moment the transaction is executed with a correction
		// added along with the actual duration. A correction is added because
		// it takes a few seconds before the transaction is added.
		int duration = streamerProduct.duration;
		this.startedAt = Instant.parse(transaction.timestamp);
		this.expiredAt = startedAt.plusSeconds(duration).plusMillis(TRANSACTION_DELAY_CORRECTION_MS);

		// start immediately
		start();

		// only queue the start notifications on instancing
		queueNotificationsByTimingType(START_NOTIFICATION_TIMING_TYPE);
	}

	public void handleBehaviour()
	{

		// guard: make sure the EBS product is active and valid
		if (!isActive || ebsProduct == null)
		{
			return;
		}

		handleNewSpawns();
		handleSpawnLocations();
		handleSpawnRandomVisualEffects();
		handleMovementAnimations();
//		handlePlayerEquipment();
//		handleInterfaceEffect();
	}

	public void onClientTick()
	{

		// guard: make sure the product is active
		if (!isActive)
		{
			return;
		}

		handleSpawnRotations();
	}

	public void start()
	{

		// guard: skip when already active
		if (isActive)
		{
			return;
		}

		handleSpawnedObjects(spawnedObjects, 0, (spawnedObject) -> {
			spawnedObject.show();
		});
		isActive = true;
	}

	public void pause()
	{

		// guard: skip when already inactive
		if (!isActive)
		{
			return;
		}

		isActive = false;
	}

	public void stop()
	{
		AnimationManager animationManager = manager.getAnimationManager();

		// start with disabling all behaviours
		isActive = false;

		// clean up all the spawned objects
		handleSpawnedObjects(spawnedObjects, 0, (spawnedObject) -> {
			hideSpawnedObject(spawnedObject, 0);
			manager.getSpawnManager().deregisterSpawnedObjectPlacement(spawnedObject);
		});
		spawnedObjects.clear();

		// revert to the original player animations if these are the movement
		// animations that are currently active, because other products could've taken over
		if (animationManager.isCurrentMovementAnimations(ebsProduct.behaviour.playerAnimations))
		{
			animationManager.revertAnimations();
		}

		// finally queue any notifications that should be triggered at the end
		queueNotificationsByTimingType(END_NOTIFICATION_TIMING_TYPE);
	}

	private void queueNotificationsByTimingType(String timingType)
	{
		ArrayList<EbsNotification> notifications = ebsProduct.behaviour.notifications;
		ArrayList<EbsNotification> filteredNotifications = new ArrayList();

		// guard: make sure there are any notifications
		if (notifications == null)
		{
			return;
		}

		for (EbsNotification notification : notifications)
		{
			// make sure the type matches
			if (timingType.equals(notification.timingType))
			{
				filteredNotifications.add(notification);
			}
		}

		// now queue them in the manager, so we also safely remove this product anywhere else
		// while making sure the notifications ARE going to be triggered
		manager.getNotificationManager().queueEbsNotifications(this, filteredNotifications);
	}

	/**
	 * Spawns of this product can expire by itself as well, without the whole product
	 * being expired. This handled removing those expired spawns while the product is active
	 */
	public void cleanExpiredSpawnedObjects()
	{
		handleSpawnedObjects(spawnedObjects, 0, (spawnedObject) -> {

			// guard: check if the spawned object is not expired yet
			if (!spawnedObject.isExpired())
			{
				return;
			}

			// hide and free up the spawn point for future spawns
			hideSpawnedObject(spawnedObject, 0);
			manager.getSpawnManager().deregisterSpawnedObjectPlacement(spawnedObject);
			spawnedObjects.remove(spawnedObject);
		});
	}

	public boolean isExpired()
	{
		return expiredAt == null || Instant.now().isAfter(expiredAt);
	}

	public long getExpiresInMs()
	{
		return expiredAt.toEpochMilli() - Instant.now().toEpochMilli();
	}

	private boolean hasMovementAnimations()
	{
		return ebsProduct.behaviour.playerAnimations != null;
	}

	private void handleMovementAnimations()
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

	private void handleSpawnRotations()
	{
		Iterator spawnedObjectIterator = spawnedObjects.iterator();

		while (spawnedObjectIterator.hasNext())
		{
			SpawnedObject spawnedObject = (SpawnedObject) spawnedObjectIterator.next();
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

	private void handleSpawnLocations()
	{
		SpawnManager spawnManager = manager.getSpawnManager();
		Iterator spawnedObjectIterator = spawnedObjects.iterator();

		// lookup table to change the spawn points consistently across objects
		// this is needed, because we want objects that are on the same location
		// to move to the new same location
		HashMap<WorldPoint, SpawnPoint> newSpawnPoints = new HashMap();

		while (spawnedObjectIterator.hasNext())
		{
			SpawnedObject spawnedObject = (SpawnedObject) spawnedObjectIterator.next();
			EbsSpawn spawn = spawnedObject.getSpawn();
			WorldPoint worldPoint = spawnedObject.getSpawnPoint().getWorldPoint();

			if (spawn == null)
			{
				continue;
			}

			EbsModelPlacement modelPlacement = spawn.modelPlacement;

			if (modelPlacement  == null)
			{
				modelPlacement = new EbsModelPlacement();
			}

			String followType = modelPlacement.followType;
			String validFollowType = (followType == null ? NONE_FOLLOW_TYPE : followType);
			EbsRandomRange radiusRange = modelPlacement.radiusRange;
			int maxRadius = radiusRange.max.intValue();

			// this follow type makes sure that the spawned object is always in view
			if (validFollowType.equals(IN_RADIUS_FOLLOW_TYPE)) {

				// guard: skip this behaviour if it is already in the scene
				if (spawnedObject.isInView(maxRadius))
				{
					continue;
				}

				// use lookup to get new spawn point candidate
				SpawnPoint newInSceneSpawnPoint = null;

				if (newSpawnPoints.containsKey(worldPoint)) {
					newInSceneSpawnPoint = newSpawnPoints.get(worldPoint);
				} else {
					newInSceneSpawnPoint = getSpawnPoint(spawn);
					newSpawnPoints.put(worldPoint, newInSceneSpawnPoint);
				}

				// guard: skip if no new spawn point could be found
				if (newInSceneSpawnPoint == null)
				{
					continue;
				}

				// move to the the in scene location and make sure the tile registration is updated
				spawnManager.moveSpawnedObject(spawnedObject, newInSceneSpawnPoint);
			}

			// this follow type moves the spawned object to be on the previous location of the player
			if (validFollowType.equals(PREVIOUS_TILE_FOLLOW_TYPE))
			{
				WorldPoint previousPlayerWorldPoint = spawnManager.getPreviousPlayerLocation();

				// guard: skip when already on the previous player location
				if (worldPoint.equals(previousPlayerWorldPoint))
				{
					continue;
				}

				// create a new spawn point without checking the already taken locations
				// TODO: check if this is what we really want as behaviour
				SpawnPoint previousPlayerSpawnPoint = new SpawnPoint(previousPlayerWorldPoint);

				// move to the previous player tile to follow and make sure the tile registration is updated
				spawnManager.moveSpawnedObject(spawnedObject, previousPlayerSpawnPoint);
			}
		}
	}

	private void handleSpawnRandomVisualEffects()
	{
		Instant now = Instant.now();
		Iterator spawnedObjectIterator = spawnedObjects.iterator();

		while (spawnedObjectIterator.hasNext())
		{
			SpawnedObject spawnedObject = (SpawnedObject) spawnedObjectIterator.next();
			Instant lastRandomVisualEffectAt = spawnedObject.getLastRandomVisualEffectAt();
			EbsSpawn spawn = spawnedObject.getSpawn();
			EbsInterval randomInterval = spawn.randomVisualEffectsInterval;
			ArrayList<EbsVisualEffects> randomVisualEffectsOptions = spawn.randomVisualEffectsOptions;

			// guard: make sure there is a valid interval and animation
			if (randomInterval == null || randomVisualEffectsOptions == null || randomVisualEffectsOptions.size() <= 0)
			{
				continue;
			}

			// guard: check if the max repeat amount is exceeded
			// NOTE: -1 repeat amount if infinity!
			if (randomInterval.repeatAmount >= 0 && spawnedObject.getRandomVisualEffectCounter() >= randomInterval.repeatAmount)
			{
				continue;
			}

			// guard: check if enough time has passed
			if (lastRandomVisualEffectAt != null && lastRandomVisualEffectAt.plusMillis(randomInterval.delayMs).isAfter(now))
			{
				continue;
			}

			// update the last time it was attempted too roll and execute a random visual effect
			spawnedObject.upateLastRandomVisualEffectAt();

			// guard: skip when this is the first time the interval is triggered!
			// this prevents the random visual effect to instantly be triggered on spawn
			if (lastRandomVisualEffectAt == null)
			{
				continue;
			}

			// guard: skip this visual effect when not rolled, while setting the timer before this roll
			if (!MarketplaceRandomizers.rollChance(randomInterval.chance))
			{
				continue;
			}

			// guard: skip when this spawned object is not in the scene,
			// because this can feel random when graphics/animations are triggered
			// without the spawned object in view
			if (!spawnedObject.isInView())
			{
				continue;
			}

			// select a random entry from all the candidates
			EbsVisualEffects randomVisualEffects = MarketplaceRandomizers.getRandomEntryFromList(randomVisualEffectsOptions);

			// trigger the animations on this single spawned object
			triggerVisualEffects(
				spawnedObject,
					randomVisualEffects,
				0,
				false,
				null
			);

			// increase the counter that will be used to check if the max repeat count is reached
			spawnedObject.registerRandomVisualEffect();
		}
	}

	private void handleNewSpawns()
	{
		Instant now = Instant.now();
		String transactionId = transaction.id;
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

			// when the spawn interval is not set it can only trigger once
			// this makes the most sense in the JSON configuration of the product
			// if no interval is set -> no repetition
			spawnInterval.repeatAmount = 1;
		}

		int repeatAmount = spawnInterval.repeatAmount;

		// guard: check if the amount has passed
		// NOTE: -1 repeat amount if infinity!
		if (repeatAmount >= 0 && spawnBehaviourCounter >= repeatAmount)
		{
			return;
		}

		// guard: check if the interval has not passed
		if (lastSpawnBehaviourAt != null && lastSpawnBehaviourAt.plusMillis(spawnInterval.delayMs).isAfter(now))
		{
			return;
		}

		// select a random option
		EbsSpawnOption spawnOption = getSpawnBehaviourByChance(spawnOptions);

		// guard: check if a valid option was selected
		if (spawnOption == null)
		{
			log.error("Could not find valid spawn behaviour option for product ("+ productId +")");
			return;
		}

		// an option is selected so we can change the timer and count
		log.info("Executing spawn behaviours for product ("+ productId +") and transaction ("+ transactionId +")");
		lastSpawnBehaviourAt = now;
		spawnBehaviourCounter += 1;

		// randomize the amount of spawns
		int spawnAmount = (int) MarketplaceRandomizers.getValidRandomNumberByRange(spawnOption.spawnAmount, 1, 1);
		ArrayList<EbsSpawn> spawns = spawnOption.spawns;

		// guard: make sure the spawn behaviours are valid
		if (spawns == null)
		{
			log.error("Could not find valid spawn behaviours for product ("+ productId +")");
			return;
		}

		// execute the spawn for the requested amount of times along with all spawn behaviours
		for (int spawnIndex = 0; spawnIndex < spawnAmount; spawnIndex++)
		{
			for (EbsSpawn spawn : spawns)
			{
				int spawnDelayMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(spawnOption.spawnDelayMs, 0, 0);

				// make sure spawning is on client thread for e.g. using client instance
				manager.getPlugin().runOnClientThread(() -> {
					triggerSpawn(spawn, spawnDelayMs);
				});
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
		SpawnPoint spawnPoint = getSpawnPoint(spawn);

		// guard: make sure the spawn point is valid
		if (spawnPoint == null)
		{
			log.error("Could not find valid spawn point when triggering spawn behaviour!");
			return;
		}

		// roll a random set of model IDs
		EbsModelSet modelSet = MarketplaceRandomizers.getRandomEntryFromList(spawn.modelSetOptions);

		// guard: make sure the selected model is valid
		if (modelSet == null || modelSet.modelIds == null)
		{
			log.error("Could not find valid model set when triggering spawn behaviour!");
			return;
		}

		// get properties from model set
		boolean shouldScaleModel = (modelSet.modelScale != null);
		boolean shouldRotateModel = (RANDOM_ROTATION_TYPE.equals(modelSet.modelRotationType));
		double modelScale = MarketplaceRandomizers.getValidRandomNumberByRange(modelSet.modelScale, 1, 1);
		double modelRotationDegrees = MarketplaceRandomizers.getValidRandomNumberByRange(modelSet.modelRotation, 0, 360);
		ArrayList<ModelData> modelDataChunks = new ArrayList();

		// load all the models
		modelSet.modelIds.forEach((modelId) -> {
			modelDataChunks.add(client.loadModelData(modelId));
		});

		// merge all models into one
		ModelData mergedModelData = client.mergeModels(modelDataChunks.toArray(new ModelData[modelDataChunks.size()]), modelDataChunks.size());

		RuneLiteObject runeLiteObject = client.createRuneLiteObject();
		SpawnedObject spawnedObject = new SpawnedObject(
		this,
			client,
			runeLiteObject,
			mergedModelData,
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

		// re-render after changes
		spawnedObject.render();

		// schedule showing of the object as it is initially hidden
		showSpawnedObject(spawnedObject, spawnDelayMs);

		// register the objects to the product and manager to make the spawn point unavailable
		spawnedObjects.add(spawnedObject);
		spawnManager.registerSpawnedObjectPlacement(spawnedObject);
	}

	private SpawnPoint getSpawnPoint(EbsSpawn spawn)
	{
		Client client = manager.getClient();
		SpawnManager spawnManager = manager.getSpawnManager();
		EbsModelPlacement placement = spawn.modelPlacement;

		// make sure there are valid placement parameters
		if (placement == null)
		{
			placement = new EbsModelPlacement();
		}

		SpawnPoint spawnPoint = null;
		EbsRandomRange radiusRange = placement.radiusRange;
		int radius = (int) MarketplaceRandomizers.getValidRandomNumberByRange(radiusRange, 0d, DEFAULT_RADIUS);
		String radiusType = placement.radiusType;
		String locationType = placement.locationType;
		String validatedRadiusType = (radiusType == null) ? DEFAULT_RADIUS_TYPE : radiusType;
		String validatedLocationType = (locationType == null) ? CURRENT_TILE_LOCATION_TYPE : locationType;
		WorldPoint referenceWorldPoint = client.getLocalPlayer().getWorldLocation();

		// check if we should change the reference to the previous tile
		if (PREVIOUS_TILE_LOCATION_TYPE.equals(validatedLocationType))
		{
			referenceWorldPoint = spawnManager.getPreviousPlayerLocation();

			if (referenceWorldPoint == null)
			{
				return null;
			}
		}

		if (OUTWARD_RADIUS_TYPE.equals(validatedRadiusType)) {
			spawnPoint = spawnManager.getOutwardSpawnPoint(radius, referenceWorldPoint);
		} else {
			spawnPoint = spawnManager.getSpawnPoint(radius, referenceWorldPoint);
		}

		return spawnPoint;
	}

	private void triggerVisualEffects(SpawnedObject spawnedObject, EbsVisualEffects visualEffects, int baseDelayMs, boolean forceModelAnimation, ResetVisualEffectHandler resetModelAnimationHandler)
	{

		// guard: make sure the animation is valid
		if (visualEffects == null)
		{
			return;
		}

		triggerModelAnimation(spawnedObject, visualEffects.modelAnimation, baseDelayMs, forceModelAnimation, resetModelAnimationHandler);
		triggerPlayerGraphic(visualEffects.playerGraphic, baseDelayMs);
		triggerPlayerAnimation(visualEffects.playerAnimation, baseDelayMs);
	}

	private void triggerModelAnimation(SpawnedObject spawnedObject, EbsAnimationFrame animation, int baseDelayMs, boolean force, ResetVisualEffectHandler resetAnimationHandler)
	{

		// add default reset handler
		if (resetAnimationHandler == null)
		{
			resetAnimationHandler = (resetDelayMs) -> {
				resetAnimation(spawnedObject, resetDelayMs);
			};
		}

		// guard: skip when the animation is locked
		// NOTE: some animations we want to force, such as hide animations
		if (!force && spawnedObject.isAnimationLocked())
		{
			return;
		}

		// add an animation lock to prevent animations to override each other
		spawnedObject.lockAnimationUntil(animation.durationMs);

		// perform the actual animation along with a possible reset after it is done
		handleVisualEffectFrame(animation, baseDelayMs, (startDelayMs) -> {
			setAnimation(spawnedObject, animation.id, startDelayMs);
		}, resetAnimationHandler);
	}

	private void triggerPlayerGraphic(EbsGraphicFrame graphic, int baseDelayMs)
	{
		handleVisualEffectFrame(graphic, baseDelayMs, (startDelayMs) -> {
			manager.getAnimationManager().setPlayerGraphic(graphic.id, graphic.height, startDelayMs, graphic.durationMs);
		}, (resetDelayMs) -> {
			// empty, no need to reset one-time graphic
		});
	}

	private void triggerPlayerAnimation(EbsAnimationFrame animation, int baseDelayMs)
	{
		handleVisualEffectFrame(animation, baseDelayMs, (startDelayMs) -> {
			manager.getAnimationManager().setPlayerAnimation(animation.id, startDelayMs, animation.durationMs);
		}, (resetDelayMs) -> {
			// empty, no need to reset one-time animation
		});
	}

	private void handleVisualEffectFrame(EbsVisualEffectFrame visualEffect, int baseDelayMs, StartVisualEffectHandler startHandler, ResetVisualEffectHandler resetHandler)
	{

		// guard: make sure the animation is valid
		if (visualEffect == null)
		{
			return;
		}

		int visualEffectId = visualEffect.id;

		// guard: make sure there is an visual effect ID
		if (visualEffectId < 0)
		{
			return;
		}

		int delayMs = visualEffect.delayMs;
		int durationMs = visualEffect.durationMs;
		int startDelayMs = baseDelayMs + delayMs;

		// schedule to start the visual effect
		startHandler.execute(startDelayMs);

		// only reset visual effects when there is a max duration
		if (durationMs >= 0) {
			int resetDelayMs = startDelayMs + durationMs;
			resetHandler.execute(resetDelayMs);
		}
	}

	private interface StartVisualEffectHandler {
		public void execute(int delayMs);
	}

	private interface ResetVisualEffectHandler {
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
				if (MarketplaceRandomizers.rollChance(option.chance))
				{
					return option;
				}
			}
		}

		// get the first is no valid one is found
		return spawnBehaviourOptions.get(0);
	}

	/**
	 * Schedule showing of a spawned object where it will trigger the show visual effects if available
	 */
	private void showSpawnedObject(SpawnedObject spawnedObject, long delayMs)
	{
		handleSpawnedObject(spawnedObject, delayMs, () -> {
			EbsVisualEffects showVisualEffects = spawnedObject.getSpawn().showVisualEffects;

			// trigger visual effects and graphics on show
			triggerVisualEffects(
				spawnedObject,
				showVisualEffects,
				0,
				true,
				null
			);

			spawnedObject.show();
		});
	}

	/**
	 * Schedule hiding of a spawned object where it will trigger the hide visual effects if available
	 */
	private void hideSpawnedObject(SpawnedObject spawnedObject, long delayMs)
	{
		handleSpawnedObject(spawnedObject, delayMs, () -> {
			EbsVisualEffects hideVisualEffects = spawnedObject.getSpawn().hideVisualEffects;

			// guard: check if the hide animation is set
			if (hideVisualEffects == null)
			{
				spawnedObject.hide();
				return;
			}

			// trigger the visual effects and at the end hide the object
			triggerVisualEffects(
				spawnedObject,
				hideVisualEffects,
				0,
				true,
				(resetDelayMs) -> {
					handleSpawnedObject(spawnedObject, resetDelayMs, () -> {
						spawnedObject.hide();
					});
				}
			);
		});
	}

	/**
	 * Schedule a set animation for a spawned object
	 */
	private void setAnimation(SpawnedObject spawnedObject, int animationId, long delayMs)
	{
		handleSpawnedObject(spawnedObject, delayMs, () -> {
			spawnedObject.setAnimation(animationId, true);
		});
	}

	/**
	 * Schedule a reset of animation for a spawned object
	 */
	private void resetAnimation(SpawnedObject spawnedObject, long delayMs)
	{
		handleSpawnedObject(spawnedObject, delayMs, () -> {
			spawnedObject.resetAnimation();
		});
	}

	/**
	 * Shortcut to loop all the spawned objects and handle thhem with a delay on the client thread.
	 */
	private void handleSpawnedObjects(CopyOnWriteArrayList<SpawnedObject> spawnedObjects, long delayMs, MarketplaceManager.SpawnedObjectHandler handler)
	{
		// guard: check if the collection is valid
		if (spawnedObjects == null || spawnedObjects.size() <= 0)
		{
			return;
		}

		// make the iterator before the delay to make sure any modifications
		// after calling this handlers are ignored, because a snapshot is made
		Iterator iterator = spawnedObjects.iterator();

		manager.getPlugin().scheduleOnClientThread(() -> {

			while(iterator.hasNext())
			{
				SpawnedObject spawnedObject = (SpawnedObject) iterator.next();
				handler.execute(spawnedObject);
			}
		}, delayMs);
	}

	/**
	 * Shortcut to handle a spawned object on the client thread with a delay
	 */
	private void handleSpawnedObject(SpawnedObject spawnedObject, long delayMs, MarketplaceManager.EmptyHandler handler)
	{
		// guard: check if the collection is valid
		if (spawnedObject == null)
		{
			return;
		}

		manager.getPlugin().scheduleOnClientThread(() -> {
			handler.execute();
		}, delayMs);
	}
}