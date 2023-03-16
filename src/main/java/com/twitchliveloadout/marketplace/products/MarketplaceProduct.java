package com.twitchliveloadout.marketplace.products;

import com.twitchliveloadout.marketplace.LambdaIterator;
import com.twitchliveloadout.marketplace.interfaces.MenuManager;
import com.twitchliveloadout.marketplace.interfaces.WidgetManager;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.marketplace.MarketplaceRandomizers;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.animations.AnimationManager;
import com.twitchliveloadout.marketplace.spawns.SpawnPoint;
import com.twitchliveloadout.marketplace.spawns.SpawnedObject;
import com.twitchliveloadout.marketplace.spawns.SpawnManager;
import com.twitchliveloadout.marketplace.transmogs.TransmogManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
	@Getter
	private boolean isPaused = false;

	/**
	 * Long-term interval trackers
	 */
	private Instant lastSpawnBehaviourAt;
	private int spawnBehaviourCounter = 0;
	private Instant lastEffectBehaviourAt;
	private int effectBehaviourCounter = 0;

	/**
	 * Expiration trackers
	 */
	@Getter
	private final Instant startedAt;
	@Getter
	private Instant expiredAt;
	@Getter
	private final Instant loadedAt;
	@Getter
	private final Instant transactionAt;

	/**
	 * A list of all the spawned objects for this product
	 */
	@Getter
	private final CopyOnWriteArrayList<SpawnedObject> spawnedObjects = new CopyOnWriteArrayList<>();

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
		this.loadedAt = Instant.now();
		this.transactionAt = Instant.parse(transaction.timestamp);

		// check when the transaction was loaded in and if it was loaded too late
		// compared to when the transaction was made. With this mechanism we still allow
		// queued transactions to be handled while a streamer is logged out for 30 seconds while keeping RL open.
		// but we will not handle transactions that RL will load when booting up without any in the queue.
		Instant transactionExpiredAt = transactionAt.plusSeconds(duration);
		boolean loadedTooLate = transaction.loadedAt.isAfter(transactionExpiredAt);

		this.startedAt = (!loadedTooLate && manager.getConfig().marketplaceStartOnLoadedAt() ? loadedAt : transactionAt);
		this.expiredAt = startedAt.plusSeconds(duration).plusMillis(TRANSACTION_DELAY_CORRECTION_MS);
	}

	public void handleBehaviour()
	{

		// guard: make sure the EBS product is active and valid
		if (!isActive || ebsProduct == null)
		{
			return;
		}

		handleNewEffects();
		handleNewSpawns();
		handleSpawnLocations();
		handleSpawnRandomEffects();
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
		play();
		triggerEffectsOptions(ebsProduct.behaviour.startEffectsOptions);
	}

	public void play()
	{
		// guard: skip when already active
		if (isActive)
		{
			return;
		}

		isPaused = false;
		isActive = true;

		handleSpawnedObjects(spawnedObjects, 0, SpawnedObject::show);
	}

	public void pause()
	{

		// guard: skip when already inactive
		if (!isActive)
		{
			return;
		}

		isPaused = true;
		isActive = false;

		handleSpawnedObjects(spawnedObjects, 0, SpawnedObject::hide);
	}

	public void stop()
	{
		// guard: skip if already stopped
		if (!isActive && !isPaused)
		{
			return;
		}

		// NOTE: do this before toggling to off otherwise some effects are skipped
		triggerEffectsOptions(ebsProduct.behaviour.stopEffectsOptions);

		// start with disabling all behaviours
		isPaused = false;
		isActive = false;

		// force the expiry when not expired yet
		// this allows us to prematurely clean up this product
		if (!isExpired())
		{
			expiredAt = Instant.now();
		}

		// clean up all the spawned objects
		handleSpawnedObjects(spawnedObjects, 0, (spawnedObject) -> {
			hideSpawnedObject(spawnedObject, 0);
			manager.getSpawnManager().deregisterSpawnedObjectPlacement(spawnedObject);
		});
		spawnedObjects.clear();
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

	public boolean isExpired(int nowDeltaMs)
	{
		return expiredAt == null || Instant.now().plusMillis(nowDeltaMs).isAfter(expiredAt);
	}

	public boolean isExpired()
	{
		return isExpired(0);
	}

	public long getExpiresInMs()
	{
		return expiredAt.toEpochMilli() - Instant.now().toEpochMilli();
	}

	private void handleSpawnRotations()
	{
		Iterator<SpawnedObject> spawnedObjectIterator = spawnedObjects.iterator();

		while (spawnedObjectIterator.hasNext())
		{
			SpawnedObject spawnedObject = spawnedObjectIterator.next();
			EbsModelSet modelSet = spawnedObject.getModelSet();
			String rotationType = modelSet.rotationType;
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
		Iterator<SpawnedObject> spawnedObjectIterator = spawnedObjects.iterator();

		// lookup table to change the spawn points consistently across objects
		// this is needed, because we want objects that are on the same location
		// to move to the new same location
		HashMap<WorldPoint, SpawnPoint> newSpawnPoints = new HashMap<>();

		while (spawnedObjectIterator.hasNext())
		{
			SpawnedObject spawnedObject = spawnedObjectIterator.next();
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
			ArrayList<EbsCondition> followConditions = modelPlacement.followConditions;
			String validFollowType = (followType == null ? NONE_FOLLOW_TYPE : followType);
			EbsRandomRange radiusRange = modelPlacement.radiusRange;
			int maxRadius = radiusRange.max.intValue();

			// guard: check if we can execute the follow behaviour according to its conditions
			if (!verifyConditions(followConditions, spawnedObject))
			{
				continue;
			}

			// this follow type makes sure that the spawned object is always in view
			if (validFollowType.equals(IN_RADIUS_FOLLOW_TYPE)) {

				// guard: skip this behaviour if it is already in the scene
				if (spawnedObject.isInView(maxRadius))
				{
					continue;
				}

				// use lookup to get new spawn point candidate
				SpawnPoint newInSceneSpawnPoint = null;

				// the same new location should be used when moving form the same tile
				// for this reason we have a lookup so that objects placed on the same tile
				// move the the same new tile
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

	private void handleSpawnRandomEffects()
	{

		// guard: don't trigger effects when the product has gone active
		// this for example makes sure the hideEffect is not interrupted by the random ones
		if (!isActive)
		{
			return;
		}

		Instant now = Instant.now();
		Iterator<SpawnedObject> spawnedObjectIterator = spawnedObjects.iterator();

		while (spawnedObjectIterator.hasNext())
		{
			SpawnedObject spawnedObject = spawnedObjectIterator.next();
			Instant lastRandomEffectAt = spawnedObject.getLastRandomEffectAt();
			EbsSpawn spawn = spawnedObject.getSpawn();
			EbsInterval randomInterval = spawn.randomEffectsInterval;
			ArrayList<ArrayList<EbsEffect>> randomEffectsOptions = spawn.randomEffectsOptions;

			// guard: make sure there is a valid interval and animation
			if (randomInterval == null || randomEffectsOptions == null || randomEffectsOptions.size() <= 0)
			{
				continue;
			}

			Boolean triggerOnSpawn = randomInterval.triggerOnSpawn;

			// guard: check if the max repeat amount is exceeded
			// NOTE: -1 repeat amount if infinity!
			if (randomInterval.repeatAmount >= 0 && spawnedObject.getRandomEffectCounter() >= randomInterval.repeatAmount)
			{
				continue;
			}

			// guard: check if enough time has passed
			if (lastRandomEffectAt != null && lastRandomEffectAt.plusMillis(randomInterval.delayMs).isAfter(now))
			{
				continue;
			}

			// update the last time it was attempted too roll and execute a random effect
			spawnedObject.updateLastRandomEffectAt();

			// guard: skip when this is the first time the interval is triggered!
			// this prevents the random effect to instantly be triggered on spawn when that is not requested
			if (lastRandomEffectAt == null && !triggerOnSpawn)
			{
				continue;
			}

			// guard: skip this effect when not rolled, while setting the timer before this roll
			if (!MarketplaceRandomizers.rollChance(randomInterval.chance))
			{
				// we will allow effects that are to be triggered on spawn
				if (lastRandomEffectAt != null || !triggerOnSpawn)
				{
					continue;
				}
			}

			// guard: skip when this spawned object is not in the region,
			// because this can feel random when graphics/animations are triggered
			// without the spawned object in view
			if (!spawnedObject.isInRegion())
			{
				continue;
			}

			// select a random entry from all the candidates
			ArrayList<EbsEffect> randomEffects = MarketplaceRandomizers.getRandomEntryFromList(randomEffectsOptions);

			// trigger the animations on this single spawned object
			triggerEffects(
				randomEffects,
				0,
				spawnedObject,
				false,
				null
			);

			// increase the counter that will be used to check if the max repeat count is reached
			spawnedObject.registerRandomEffect();
		}
	}

	private void handleNewEffects()
	{
		Instant now = Instant.now();
		String transactionId = transaction.id;
		String productId = ebsProduct.id;
		EbsBehaviour behaviour = ebsProduct.behaviour;
		ArrayList<ArrayList<EbsEffect>> effectOptions = behaviour.effectsOptions;
		EbsInterval effectsInterval = behaviour.effectsInterval;

		// guard: check if there are any effect options
		if (effectOptions == null)
		{
			return;
		}

		// make sure the behaviour interval is valid
		if (effectsInterval == null)
		{
			effectsInterval = new EbsInterval();

			// when the spawn interval is not set it can only trigger once
			// this makes the most sense in the JSON configuration of the product
			// if no interval is set -> no repetition
			effectsInterval.repeatAmount = 1;
		}

		int repeatAmount = effectsInterval.repeatAmount;

		// guard: check if the amount has passed
		// NOTE: -1 repeat amount if infinity!
		if (repeatAmount >= 0 && effectBehaviourCounter >= repeatAmount)
		{
			return;
		}

		// guard: check if the interval has not passed
		if (lastEffectBehaviourAt != null && lastEffectBehaviourAt.plusMillis(effectsInterval.delayMs).isAfter(now))
		{
			return;
		}

		// select a random option
		ArrayList<EbsEffect> effectsOption = MarketplaceRandomizers.getRandomEntryFromList(effectOptions);

		// guard: check if a valid option was selected
		if (effectsOption == null)
		{
			log.error("Could not find valid effect behaviour option for product ("+ productId +")");
			return;
		}

		log.debug("Executing effect behaviours for product ("+ productId +") and transaction ("+ transactionId +")");
		lastEffectBehaviourAt = Instant.now();
		effectBehaviourCounter += 1;

		triggerEffects(
			effectsOption,
			0,
			null,
			false,
			null
		);
	}

	private void handleNewSpawns()
	{
		Instant now = Instant.now();
		String transactionId = transaction.id;
		String productId = ebsProduct.id;
		EbsBehaviour behaviour = ebsProduct.behaviour;
		ArrayList<EbsSpawnOption> startSpawnOptions = behaviour.startSpawnOptions;
		ArrayList<EbsSpawnOption> spawnOptions = behaviour.spawnOptions;
		EbsInterval spawnInterval = behaviour.spawnInterval;
		boolean hasSpawnedAtLeastOnce = (lastSpawnBehaviourAt != null);

		// override the spawn options with the initial ones when they are valid
		// and the first spawn still needs to happen
		if (!hasSpawnedAtLeastOnce && startSpawnOptions != null)
		{
			spawnOptions = startSpawnOptions;
		}

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
		if (hasSpawnedAtLeastOnce && lastSpawnBehaviourAt.plusMillis(spawnInterval.delayMs).isAfter(now))
		{
			return;
		}

		// select a random option
		EbsSpawnOption spawnOption = MarketplaceRandomizers.getSpawnBehaviourByChance(spawnOptions);

		// guard: check if a valid option was selected
		if (spawnOption == null)
		{
			log.error("Could not find valid spawn behaviour option for product ("+ productId +")");
			return;
		}

		// an option is selected so we can change the timer and count
		log.debug("Executing spawn behaviours for product ("+ productId +") and transaction ("+ transactionId +")");
		lastSpawnBehaviourAt = now;
		spawnBehaviourCounter += 1;

		// randomize the amount of spawns
		int spawnGroupAmount = (int) MarketplaceRandomizers.getValidRandomNumberByRange(spawnOption.spawnAmount, 1, 1);
		ArrayList<EbsSpawn> spawns = spawnOption.spawns;
		String spawnPointType = spawnOption.spawnPointType;

		// guard: check if the conditions are satisfied
		// NOTE: this should happen after the timer is being set!
		if (!verifyConditions(spawnOption.conditions, null))
		{
			return;
		}

		// guard: make sure the spawn behaviours are valid
		if (spawns == null)
		{
			log.error("Could not find valid spawn behaviours for product ("+ productId +")");
			return;
		}

		// make sure spawning is on client thread for e.g. using client instance
		manager.getPlugin().runOnClientThread(() -> {

			// execute the spawn for the requested amount of times along with all spawn behaviours
			for (int spawnGroupIndex = 0; spawnGroupIndex < spawnGroupAmount; spawnGroupIndex++)
			{

				// spawn points can be shared between spawns depending on the settings
				SpawnPoint spawnPoint = null;

				for (EbsSpawn spawn : spawns)
				{
					int spawnAmount = (int) MarketplaceRandomizers.getValidRandomNumberByRange(spawn.spawnAmount, 1, 1);

					for (int spawnIndex = 0; spawnIndex < spawnAmount; spawnIndex++)
					{
						int spawnDelayMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(spawnOption.spawnDelayMs, 0, 0);

						// determine whether we re-use the same spawn-point we already got
						// or if we should generate a new one
						if (spawnPoint == null || INDIVIDUAL_SPAWN_POINT_TYPE.equals(spawnPointType))
						{
							spawnPoint = getSpawnPoint(spawn);
						}

						triggerSpawn(spawn, spawnPoint, spawnDelayMs);
					};
				}
			}
		});
	}

	private void triggerSpawn(EbsSpawn spawn, SpawnPoint spawnPoint, int spawnDelayMs)
	{

		// guard: make sure the spawn is valid
		if (spawn == null)
		{
			log.error("An invalid spawn object was passed when triggering spawn!");
			return;
		}

		SpawnManager spawnManager = manager.getSpawnManager();
		Client client = manager.getClient();

		// guard: make sure the spawn point is valid
		if (spawnPoint == null)
		{
			log.debug("Could not find valid spawn point when triggering spawn behaviour!");
			return;
		}

		// roll a random set of model IDs
		EbsModelSet modelSet = MarketplaceRandomizers.getRandomEntryFromList(spawn.modelSetOptions);

		// guard: make sure the selected model is valid
		if (modelSet == null || modelSet.ids == null)
		{
			log.error("Could not find valid model set when triggering spawn behaviour!");
			return;
		}

		// get properties from model set
		boolean shouldScaleModel = (modelSet.scale != null);
		boolean shouldRotateModel = (RANDOM_ROTATION_TYPE.equals(modelSet.rotationType));
		double modelScale = MarketplaceRandomizers.getValidRandomNumberByRange(modelSet.scale, 1, 1);
		double modelRotationDegrees = MarketplaceRandomizers.getValidRandomNumberByRange(modelSet.rotation, 0, 360);
		ArrayList<EbsRecolor> recolors = modelSet.recolors;
		ArrayList<ModelData> modelDataChunks = new ArrayList<>();

		// load all the models
		modelSet.ids.forEach((modelId) -> {
			modelDataChunks.add(client.loadModelData(modelId));
		});

		// merge all models into one
		ModelData mergedModelData = client.mergeModels(modelDataChunks.toArray(new ModelData[modelDataChunks.size()]), modelDataChunks.size());

		SpawnedObject spawnedObject = new SpawnedObject(
			this,
			client,
			mergedModelData,
			spawnPoint,
			spawn,
			modelSet
		);

		// check for valid recolors
		if (recolors != null)
		{
			mergedModelData.cloneColors();

			// apply recolors
			LambdaIterator.handleAll(recolors, (recolor) -> {
				spawnedObject.recolor(recolor);
			});
		}

		// scale model
		if  (shouldScaleModel)
		{
			spawnedObject.scale(modelScale);
		}

		// rotate model
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
		int radius = (int) MarketplaceRandomizers.getValidRandomNumberByRange(radiusRange, DEFAULT_MIN_RADIUS, DEFAULT_MAX_RADIUS);
		String radiusType = placement.radiusType;
		String locationType = placement.locationType;
		Boolean inLineOfSight = placement.inLineOfSight;
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
			spawnPoint = spawnManager.getOutwardSpawnPoint(radius, inLineOfSight, referenceWorldPoint);
		} else {
			spawnPoint = spawnManager.getSpawnPoint(radius, inLineOfSight, referenceWorldPoint);
		}

		return spawnPoint;
	}

	public void triggerEffects(ArrayList<EbsEffect> effects, int baseDelayMs, SpawnedObject spawnedObject, boolean forceModelAnimation, ResetEffectHandler resetModelAnimationHandler)
	{

		// guard: make sure the animation is valid
		if (effects == null)
		{
			return;
		}

		int totalDelayMs = baseDelayMs;
		Iterator<EbsEffect> effectIterator = effects.iterator();

		while (effectIterator.hasNext())
		{
			EbsEffect effect = effectIterator.next();
			boolean isLast = !effectIterator.hasNext();
			int durationMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(effect.durationMs, 0, 0);
			int delayMs = 0; // potentially handy in the future to delay a full effect
			ArrayList<EbsCondition> conditions = effect.conditions;

			// schedule all the individual effects
//			log.info("SCHEDULE EFFECTS: "+ totalDelayMs +", at: "+ Instant.now().toEpochMilli());
			manager.getPlugin().scheduleOnClientThread(() -> {

				// guard: check if all the conditions for this effect are met
				if (!verifyConditions(conditions, spawnedObject))
				{
//					log.info("CANCELLED EFFECTS: "+ Instant.now().toEpochMilli());
					return;
				}

//				log.info("TRIGGERED EFFECTS: "+ Instant.now().toEpochMilli());
				triggerModelAnimation(
					spawnedObject,
					effect.modelAnimation,
					delayMs,
					forceModelAnimation,
					isLast ? resetModelAnimationHandler : null
				);
				triggerPlayerGraphic(effect.playerGraphic, delayMs);
				triggerPlayerAnimation(effect.playerAnimation, delayMs);
				triggerPlayerEquipment(effect.playerEquipment, delayMs);
				triggerPlayerMovement(effect.playerMovement, delayMs);
				triggerInterfaceWidgets(effect.interfaceWidgets, delayMs);
				triggerMenuOptions(effect.menuOptions, delayMs);
				triggerSoundEffect(effect.soundEffect, delayMs);
				triggerNotifications(effect.notifications, delayMs);
			}, totalDelayMs);

			totalDelayMs += durationMs;
		}
	}

	public void triggerEffectsOptions(ArrayList<ArrayList<EbsEffect>> effectsOptions)
	{
		// trigger the animations on this single spawned object
		ArrayList<EbsEffect> effects = MarketplaceRandomizers.getRandomEntryFromList(effectsOptions);
		triggerEffects(effects);
	}

	public void triggerEffects(ArrayList<EbsEffect> effects)
	{
		triggerEffects(
			effects,
			0,
			null,
			false,
			null
		);
	}

	private boolean verifyConditions(ArrayList<EbsCondition> conditions, SpawnedObject spawnedObject)
	{

		// guard: check if collection is valid
		if (conditions == null)
		{
			return true;
		}

		Iterator<EbsCondition> iterator = conditions.iterator();

		while (iterator.hasNext())
		{
			EbsCondition condition = iterator.next();
			boolean conditionVerified = verifyCondition(condition, spawnedObject);

			// guard: if one condition is not verified return false
			// this means the top-level conditions are using AND logic
			if (!conditionVerified)
			{
				return false;
			}
		}

		return true;
	}

	private boolean verifyCondition(EbsCondition condition, SpawnedObject spawnedObject)
	{

		// guard: check if collection is valid
		if (condition == null)
		{
			return true;
		}

		Integer varbitId = condition.varbitId;
		Integer varbitValue = condition.varbitValue;
		Integer minTimeMs = condition.minTimeMs;
		Integer maxTimeMs = condition.maxTimeMs;
		Double minTimePercentage = condition.minTimePercentage;
		Double maxTimePercentage = condition.maxTimePercentage;
		Integer maxSpawnsInView = condition.maxSpawnsInView;
		Integer maxSpawnsInViewRadius = condition.maxSpawnsInViewRadius;
		Integer minSpawnsInView = condition.minSpawnsInView;
		Integer minSpawnsInViewRadius = condition.minSpawnsInViewRadius;
		Integer spawnInViewRadius = condition.spawnInViewRadius;
		ArrayList<EbsCondition> orConditions = condition.or;
		ArrayList<EbsCondition> andConditions = condition.and;
		boolean orConditionsVerified = false;

		// guard: check if it is allowed within an absolute time-frame
		if (!verifyTimePassedMs(minTimeMs, maxTimeMs))
		{
			return false;
		}

		// guard: check if it is allowed withing a relative time-frame
		if (!verifyTimePassedPercentage(minTimePercentage, maxTimePercentage))
		{
			return false;
		}

		// guard: check if this condition should check a varbit
		if (varbitId >= 0 && manager.getClient().getVarbitValue(varbitId) != varbitValue)
		{
			return false;
		}

		// guard: check for max spawns in view
		if (maxSpawnsInView > 0 && countSpawnedObjectsInView(maxSpawnsInViewRadius) > maxSpawnsInView)
		{
			return false;
		}

		// guard: check for min spawns in view
		if (minSpawnsInView > 0 && countSpawnedObjectsInView(minSpawnsInViewRadius) < minSpawnsInView)
		{
			return false;
		}

		// guard: check for request to check the current spawn and if its in radius
		if (spawnedObject != null && spawnInViewRadius > 0 && !spawnedObject.isInView(spawnInViewRadius))
		{
			return false;
		}

		LambdaIterator.handleAll(andConditions, (andCondition) -> {

		});

		// check if one AND condition is not valid to set to false
		if (andConditions != null)
		{
			for (EbsCondition andCondition : andConditions)
			{
				if (!verifyCondition(andCondition, spawnedObject))
				{
					return false;
				}
			}
		}

		// check if one OR condition is valid to set the flag to true
		if (orConditions != null)
		{
			for (EbsCondition orCondition : orConditions)
			{
				if (verifyCondition(orCondition, spawnedObject))
				{
					orConditionsVerified = true;
					break;
				}
			}

			// guard: check if the or conditions are valid
			if (!orConditionsVerified)
			{
				return false;
			}
		}

		return true;
	}

	/**
	 * Check whether a certain time-frame absolute to the starting time is valid
	 */
	private boolean verifyTimePassedMs(int minMs, int maxMs)
	{

		// guard: make sure the time-frame is valid
		if (minMs < 0 || maxMs < 0)
		{
			return true;
		}

		// guard: skip any calculations when it is the full time-frame
		if (minMs == 0 && maxMs == Integer.MAX_VALUE)
		{
			return true;
		}

		long passedMs = getPassedMs();

		// guard: check whether the requested time-frame is outside of the current passed time
		if (passedMs < minMs || passedMs > maxMs)
		{
			return false;
		}

		return true;
	}

	/**
	 * Check whether a certain time-frame relative to the total duration with percentages is what we are now at
	 */
	private boolean verifyTimePassedPercentage(double minPercentage, double maxPercentage)
	{

		// guard: make sure the percentages are valid
		if (minPercentage < 0 || maxPercentage < 0 || maxPercentage > 1 || minPercentage > 1)
		{
			return true;
		}

		// guard: skip any calculations when it is the full time-frame
		if (minPercentage == 0 && maxPercentage == 1)
		{
			return true;
		}

		long durationMs = getDurationMs();

		// guard: make sure the duration is valid
		if (durationMs <= 0)
		{
			return false;
		}

		long passedMs = getPassedMs();
		double passedTimePercentage = (((double) passedMs) / ((double) durationMs));

		// guard: check whether the current elapsed time is outside of the requested range
		if (passedTimePercentage < minPercentage || passedTimePercentage > maxPercentage)
		{
			return false;
		}

		return true;
	}

	private void triggerModelAnimation(SpawnedObject spawnedObject, EbsAnimationFrame animation, int baseDelayMs, boolean force, ResetEffectHandler resetAnimationHandler)
	{
		// guard: make sure the spawned object and animation are valid
		if (spawnedObject == null || animation == null)
		{
			return;
		}

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
		handleEffectFrame(animation, baseDelayMs, (startDelayMs) -> {
			setAnimation(spawnedObject, animation.id, animation.shouldLoop, startDelayMs);
		}, resetAnimationHandler);
	}

	private void triggerPlayerGraphic(EbsGraphicFrame graphicFrame, int delayMs)
	{
		AnimationManager animationManager = manager.getAnimationManager();

		handleEffectFrame(graphicFrame, delayMs, (startDelayMs) -> {
			animationManager.setPlayerGraphic(graphicFrame.id, graphicFrame.height, startDelayMs, graphicFrame.durationMs);
		}, animationManager::resetPlayerGraphic);
	}

	private void triggerPlayerAnimation(EbsAnimationFrame animationFrame, int delayMs)
	{
		AnimationManager animationManager = manager.getAnimationManager();

		handleEffectFrame(animationFrame, delayMs, (startDelayMs) -> {
			animationManager.setPlayerAnimation(animationFrame.id, startDelayMs, animationFrame.durationMs);
		}, animationManager::resetPlayerAnimation);
	}

	private void triggerPlayerEquipment(EbsEquipmentFrame equipmentFrame, int baseDelayMs)
	{
		TransmogManager transmogManager = manager.getTransmogManager();

		// guard: make sure the frame is valid
		if (equipmentFrame == null)
		{
			return;
		}

		int delayMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(equipmentFrame.delayMs, 0, 0);

		manager.getPlugin().scheduleOnClientThread(() -> {
			transmogManager.addEffect(this, equipmentFrame);
		}, baseDelayMs + delayMs);
	}

	private void triggerPlayerMovement(EbsMovementFrame movementFrame, int baseDelayMs)
	{
		AnimationManager animationManager = manager.getAnimationManager();

		// guard: make sure the frame is valid
		if (movementFrame == null)
		{
			return;
		}

		int delayMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(movementFrame.delayMs, 0, 0);

		manager.getPlugin().scheduleOnClientThread(() -> {
			animationManager.addEffect(this, movementFrame);
		}, baseDelayMs + delayMs);
	}

	private void triggerInterfaceWidgets(ArrayList<EbsInterfaceWidgetFrame> interfaceWidgetFrames, int delayMs)
	{
		WidgetManager widgetManager = manager.getWidgetManager();

		// guard: make sure there are valid widget frames
		if (interfaceWidgetFrames == null)
		{
			return;
		}

		// TODO: allow delayMs of the effect frame to be included!
		manager.getPlugin().scheduleOnClientThread(() -> {
			Iterator<EbsInterfaceWidgetFrame> interfaceWidgetFrameIterator = interfaceWidgetFrames.iterator();

			while (interfaceWidgetFrameIterator.hasNext())
			{
				EbsInterfaceWidgetFrame interfaceWidgetFrame = interfaceWidgetFrameIterator.next();
				widgetManager.addEffect(this, interfaceWidgetFrame);
			}
		}, delayMs);
	}

	private void triggerMenuOptions(ArrayList<EbsMenuOptionFrame> menuOptionFrames, int delayMs)
	{
		MenuManager menuManager = manager.getMenuManager();

		// guard: make sure there are valid frames
		if (menuOptionFrames == null)
		{
			return;
		}

		// TODO: allow delayMs of the effect frame to be included!
		manager.getPlugin().scheduleOnClientThread(() -> {
			Iterator<EbsMenuOptionFrame> menuOptionFrameIterator = menuOptionFrames.iterator();

			while (menuOptionFrameIterator.hasNext())
			{
				EbsMenuOptionFrame menuOptionFrame = menuOptionFrameIterator.next();
				menuManager.addEffect(this, menuOptionFrame);
			}
		}, delayMs);
	}

	private void triggerSoundEffect(EbsSoundEffectFrame soundEffect, int baseDelayMs)
	{

		// guard: make sure the sound is valid
		if (soundEffect == null)
		{
			return;
		}

		Integer soundEffectId = soundEffect.id;
		int delayMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(soundEffect.delayMs, 0, 0);

		if (soundEffectId <= 0)
		{
			return;
		}

		manager.getPlugin().scheduleOnClientThread(() -> {
			manager.getSoundManager().playSound(soundEffectId);
		}, baseDelayMs + delayMs);
	}

	private void triggerNotifications(ArrayList<EbsNotification> notifications, int delayMs)
	{
		// guard: make sure there are any notifications
		if (notifications == null || notifications.size() <= 0)
		{
			return;
		}

		// queue at the start of the effect
		manager.getPlugin().scheduleOnClientThread(() -> {
			boolean isExpired = ((!isActive && !isExpired()) || isExpired(-1 * END_NOTIFICATION_GRACE_PERIOD_MS));

			// guard: make sure the product is active
			if (isExpired)
			{
				log.debug("Skipping notifications because product is expired by time: "+ getExpiresInMs());
				return;
			}

			// now queue them in the manager, so we also safely remove this product anywhere else
			// while making sure the notifications ARE going to be triggered
			manager.getNotificationManager().handleEbsNotifications(this, notifications);
		}, delayMs);
	}

	private void handleEffectFrame(EbsEffectFrame effect, int baseDelayMs, StartEffectHandler startHandler, ResetEffectHandler resetHandler)
	{

		// guard: make sure the animation is valid
		if (effect == null)
		{
			return;
		}

		int effectId = effect.id;

		// guard: make sure there is an effect ID
		if (effectId < 0)
		{
			return;
		}

		EbsRandomRange delayMsRange = effect.delayMs;
		int delayMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(delayMsRange, 0, 0);
		int durationMs = effect.durationMs;
		int startDelayMs = baseDelayMs + delayMs;

		// schedule to start the effect
		startHandler.execute(startDelayMs);

		// only reset effects when there is a max duration
		if (durationMs >= 0) {
			int resetDelayMs = startDelayMs + durationMs;
			resetHandler.execute(resetDelayMs);
		}
	}

	private interface StartEffectHandler {
		public void execute(int delayMs);
	}

	private interface ResetEffectHandler {
		public void execute(int delayMs);
	}

	/**
	 * Schedule showing of a spawned object where it will trigger the show effects if available
	 */
	private void showSpawnedObject(SpawnedObject spawnedObject, long delayMs)
	{
		handleSpawnedObject(spawnedObject, delayMs, () -> {
			ArrayList<EbsEffect> showEffects = spawnedObject.getSpawn().showEffects;

			// trigger effects and graphics on show
			triggerEffects(
				showEffects,
				0,
				spawnedObject,
				true,
				null
			);

			spawnedObject.show();
		});
	}

	/**
	 * Schedule hiding of a spawned object where it will trigger the hide effects if available
	 */
	private void hideSpawnedObject(SpawnedObject spawnedObject, long delayMs)
	{
		handleSpawnedObject(spawnedObject, delayMs, () -> {
			ArrayList<EbsEffect> hideEffects = spawnedObject.getSpawn().hideEffects;

			// guard: check if the hide animation is set
			if (hideEffects == null)
			{
				spawnedObject.hide();
				return;
			}

			// trigger the effects and at the end hide the object
			triggerEffects(
				hideEffects,
				0,
				spawnedObject,
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
	private void setAnimation(SpawnedObject spawnedObject, int animationId, boolean shouldLoop, long delayMs)
	{
		handleSpawnedObject(spawnedObject, delayMs, () -> {
			spawnedObject.setAnimation(animationId, shouldLoop);
		});
	}

	/**
	 * Schedule a reset of animation for a spawned object
	 */
	private void resetAnimation(SpawnedObject spawnedObject, long delayMs)
	{
		handleSpawnedObject(spawnedObject, delayMs, () -> {

			// guard: check if this product has been disabled in the mean time after this was scheduled
			// this means we will not trigger the reset animation, because it can interrupt the hide effects.
			// it will not do this when paused, because with paused objects we still want to reset the animation in the background.
			if (!isActive && !isPaused)
			{
				return;
			}

			spawnedObject.resetAnimation();
		});
	}

	/**
	 * Count the amount of spawned objects that are in view of the player
	 */
	private int countSpawnedObjectsInView(int radius)
	{
		AtomicInteger inViewAmount = new AtomicInteger();

		LambdaIterator.handleAll(spawnedObjects, (spawnedObject) -> {
			if (spawnedObject.isInView(radius))
			{
				inViewAmount.addAndGet(1);
			}
		});

		return inViewAmount.get();
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
		Iterator<SpawnedObject> iterator = spawnedObjects.iterator();

		manager.getPlugin().scheduleOnClientThread(() -> {
			while(iterator.hasNext())
			{
				SpawnedObject spawnedObject = iterator.next();
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

	/**
	 * Calculate how long in milliseconds this product is going to be active
	 */
	public long getDurationMs()
	{
		if (expiredAt == null || startedAt == null)
		{
			return 0;
		}

		return Duration.between(startedAt, expiredAt).toMillis();
	}

	/**
	 * Calculate the amount of time in milliseconds since this product was started
	 */
	public long getPassedMs()
	{
		Instant now = Instant.now();

		return Duration.between(startedAt, now).toMillis();
	}
}
