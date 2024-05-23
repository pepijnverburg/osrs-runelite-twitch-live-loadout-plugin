package com.twitchliveloadout.marketplace.products;

import com.twitchliveloadout.marketplace.LambdaIterator;
import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.interfaces.MenuManager;
import com.twitchliveloadout.marketplace.interfaces.WidgetManager;
import com.twitchliveloadout.marketplace.spawns.SpawnOverheadManager;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.marketplace.MarketplaceRandomizers;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.animations.AnimationManager;
import com.twitchliveloadout.marketplace.spawns.SpawnPoint;
import com.twitchliveloadout.marketplace.spawns.SpawnedObject;
import com.twitchliveloadout.marketplace.spawns.SpawnManager;
import com.twitchliveloadout.marketplace.transmogs.TransmogManager;
import com.twitchliveloadout.twitch.TwitchState;
import com.twitchliveloadout.twitch.TwitchStateEntry;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
	@Getter
	private int spawnAmount = 0;

	/**
	 * Lookup table with the current key value pairs of the state frames to assign state values to this product
	 * which are to be used in conditions to check whether certain effects are allowed to be executed.
	 * This very simple mechanism makes the MarketplaceProduct and SpawnedObject stateful where logic can be based on.
	 */
	private final ConcurrentHashMap<String, String> stateFrameValues = new ConcurrentHashMap<>();

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
		int transactionExpiryToleranceS = 30;
		Instant transactionExpiredAt = transactionAt.plusSeconds(duration + transactionExpiryToleranceS);
		Instant transactionLoadedAt = Instant.parse(transaction.loaded_at);
		boolean loadedTooLate = transactionLoadedAt.isAfter(transactionExpiredAt);

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

	public void start()
	{
		play();
		triggerEffectsOptions(
			ebsProduct.behaviour.startEffectsOptions,
			null,
			0
		);
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

	public void stop(boolean force)
	{
		// guard: skip if already stopped
		if (!isActive && !isPaused && !force)
		{
			return;
		}

		// do this before setting active to FALSE, otherwise some effects are skipped
		if (!force)
		{
			triggerEffectsOptions(
				ebsProduct.behaviour.stopEffectsOptions,
				null,
				0
			);
		}

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

			// when forced hide instantly, otherwise trigger the hide effects
			if (force) {
				spawnedObject.hide();
			} else {
				hideSpawnedObject(spawnedObject, 0);
			}

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

	public void handleSpawnRotations()
	{

		// guard: make sure the product is active
		if (!isActive)
		{
			return;
		}

		Iterator<SpawnedObject> spawnedObjectIterator = spawnedObjects.iterator();

		while (spawnedObjectIterator.hasNext())
		{
			SpawnedObject spawnedObject = spawnedObjectIterator.next();
			EbsModelPlacement modelPlacement = spawnedObject.getSpawn().modelPlacement;
			String rotationType = modelPlacement.rotationType;
			Player player = manager.getClient().getLocalPlayer();

			// guard: make sure the rotation and player are valid
			if (rotationType == null || player == null)
			{
				continue;
			}

			Actor interacting = player.getInteracting();

			switch (rotationType)
			{
				case PLAYER_ROTATION_TYPE:
					LocalPoint playerLocalLocation = player.getLocalLocation();
					spawnedObject.rotateTowards(playerLocalLocation);
					break;
				case MIRROR_PLAYER_ROTATION_TYPE:
					spawnedObject.setOrientation(player.getCurrentOrientation());
					break;
				case INTERACTING_ROTATION_TYPE:
					if (interacting == null)
					{
						continue;
					}

					LocalPoint interactingLocalLocation = interacting.getLocalLocation();
					spawnedObject.rotateTowards(interactingLocalLocation);
					break;
				case MIRROR_INTERACTING_ROTATION_TYPE:
					if (interacting == null)
					{
						continue;
					}

					spawnedObject.setOrientation(interacting.getCurrentOrientation());
					break;
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

			EbsModelPlacement modelRespawnPlacement = spawn.modelRespawnPlacement;
			EbsModelPlacement modelPlacement = spawn.modelPlacement;

			// override the model placement with a respawn model placement when set
			// this allows us to have different behaviours for the initial spawn placement
			// and the placement and follow behaviour afterwards
			if (modelRespawnPlacement != null)
			{
				modelPlacement = modelRespawnPlacement;
			}

			// make sure the selected model placement is always valid
			if (modelPlacement == null)
			{
				modelPlacement = new EbsModelPlacement();
			}

			String followType = modelPlacement.followType;
			ArrayList<EbsCondition> followConditions = modelPlacement.followConditions;
			String validFollowType = (followType == null ? NONE_FOLLOW_TYPE : followType);
			EbsRandomRange radiusRange = modelPlacement.radiusRange;
			int followRadius = DEFAULT_MAX_RADIUS;

			// override the follow radius with a set oe or the max in the radius range
			if (modelPlacement.followRadius != null) {
				followRadius = modelPlacement.followRadius;
			} else if (radiusRange != null && radiusRange.max != null) {
				followRadius = radiusRange.max.intValue();
			}

			// guard: skip when no follow type
			if (validFollowType.equals(NONE_FOLLOW_TYPE))
			{
				continue;
			}

			// guard: check if we can execute the follow behaviour according to its conditions
			if (!verifyConditions(followConditions, spawnedObject))
			{
				continue;
			}

			// this follow type makes sure that the spawned object is always in view
			if (validFollowType.equals(IN_RADIUS_FOLLOW_TYPE)) {

				// guard: skip this behaviour if it is already in the scene
				if (spawnedObject.isInView(followRadius))
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
					WorldPoint modelWorldPoint = spawnedObject.getSpawnPoint().getWorldPoint();
					newInSceneSpawnPoint = spawnManager.getSpawnPoint(modelPlacement, modelWorldPoint);
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
			Instant spawnedAt = spawnedObject.getSpawnedAt();
			Instant lastRandomEffectAt = spawnedObject.getLastRandomEffectAt();
			int randomEffectCounter = spawnedObject.getRandomEffectCounter();
			EbsSpawn spawn = spawnedObject.getSpawn();
			EbsInterval randomInterval = spawn.randomEffectsInterval;
			ArrayList<ArrayList<EbsEffect>> randomEffectsOptions = spawn.randomEffectsOptions;

			// guard: make sure there is a valid interval and animation
			if (randomInterval == null || randomEffectsOptions == null || randomEffectsOptions.size() <= 0)
			{
				continue;
			}

			Boolean triggerOnStart = randomInterval.triggerOnStart;
			Double chance = randomInterval.chance;
			ArrayList<EbsCondition> conditions = randomInterval.conditions;

			// guard: check whether the interval is allowed
			if (!verifyIntervalDelay(randomInterval, lastRandomEffectAt, spawnedAt, randomEffectCounter)) {
				continue;
			}

			// guard: skip when this spawned object is not in the region,
			// because this can feel random when graphics/animations are triggered
			// without the spawned object in view
			if (!spawnedObject.isInRegion())
			{
				continue;
			}

			// guard: make sure the conditions are met
			if (!verifyConditions(conditions, spawnedObject))
			{
				continue;
			}

			// guard: skip this effect when not rolled, while setting the timer before this roll
			if (!MarketplaceRandomizers.rollChance(chance))
			{
				// we will allow effects that are to be triggered on spawn
				if (lastRandomEffectAt != null || !triggerOnStart)
				{
					continue;
				}
			}

			// select a random entry from all the candidates
			ArrayList<EbsEffect> randomEffects = MarketplaceRandomizers.getRandomEntryFromList(randomEffectsOptions);

			// trigger the animations on this single spawned object
			triggerEffects(
				randomEffects,
				0,
				spawnedObject,
				null,
				false,
				null
			);

			// increase the counter that will be used to check if the max repeat count is reached
			spawnedObject.registerRandomEffect();
		}
	}

	private void handleNewEffects()
	{
		EbsBehaviour behaviour = ebsProduct.behaviour;
		ArrayList<ArrayList<EbsEffect>> effectOptions = behaviour.effectsOptions;

		// guard: check if there are any effect options
		if (effectOptions == null)
		{
			return;
		}

		String transactionId = transaction.id;
		String productId = ebsProduct.id;
		EbsInterval effectsInterval = behaviour.effectsInterval;

		// make sure the behaviour interval is valid
		if (effectsInterval == null)
		{
			effectsInterval = new EbsInterval();

			// when the spawn interval is not set it can only trigger once
			// this makes the most sense in the JSON configuration of the product
			// if no interval is set -> no repetition
			effectsInterval.repeatAmount = 1;
		}

		ArrayList<EbsCondition> conditions = effectsInterval.conditions;
		int afterTriggerDelayMs = effectsInterval.afterTriggerDelayMs;

		// guard: check whether the interval is allowed
		if (!verifyIntervalDelay(effectsInterval, lastEffectBehaviourAt, startedAt, effectBehaviourCounter)) {
			return;
		}

		// guard: check if the interval is allowed to be triggered based on the conditions
		if (!verifyConditions(conditions))
		{

			// when the conditions are not verified we will set the last behaviour at to prevent the effects
			// to be check too many times in a row, this gives some throttling in some possible heavy checks
			lastEffectBehaviourAt = Instant.now();
			return;
		}

		// select a random option
		ArrayList<EbsEffect> effectsOption = MarketplaceRandomizers.getRandomEntryFromList(effectOptions);

		// guard: check if a valid option was selected
		if (effectsOption == null)
		{
			log.warn("Could not find valid effect behaviour option for product ("+ productId +")");
			return;
		}

//		log.warn("Executing effect behaviours for product ("+ productId +") and transaction ("+ transactionId +")");
		lastEffectBehaviourAt = Instant.now().plusMillis(afterTriggerDelayMs);
		effectBehaviourCounter += 1;

		triggerEffects(
			effectsOption,
			0,
			null,
			null,
			false,
			null
		);
	}

	private void handleNewSpawns()
	{
		Instant now = Instant.now();
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

		int afterTriggerDelayMs = spawnInterval.afterTriggerDelayMs;
		ArrayList<EbsCondition> conditions = spawnInterval.conditions;

		// guard: check whether the interval is allowed
		if (!verifyIntervalDelay(spawnInterval, lastSpawnBehaviourAt, startedAt, spawnBehaviourCounter)) {
			return;
		}

		// guard: check if the conditions are met
		if (!verifyConditions(conditions))
		{
			return;
		}

		// update timer and count and add extra time requested to delay the effect after a successful trigger
		lastSpawnBehaviourAt = now.plusMillis(afterTriggerDelayMs);
		spawnBehaviourCounter += 1;

		triggerSpawnOptions(null, spawnOptions);
	}

	private void triggerSpawnOptions(SpawnedObject spawnedObject, ArrayList<EbsSpawnOption> spawnOptions)
	{
		WorldPoint worldPoint = null;

		if (spawnedObject !=  null) {
			worldPoint = spawnedObject.getSpawnPoint().getWorldPoint();
		}

		triggerSpawnOptionsAtWorldPoint(worldPoint, spawnOptions);
	}

	private void triggerSpawnOptionsAtWorldPoint(WorldPoint modelWorldPoint, ArrayList<EbsSpawnOption> spawnOptions)
	{

		// guard: make sure the spawn options are valid
		if (spawnOptions == null || spawnOptions.size() <= 0)
		{
			return;
		}

		SpawnManager spawnManager = manager.getSpawnManager();
		String transactionId = transaction.id;
		String productId = ebsProduct.id;
		EbsSpawnOption spawnOption = MarketplaceRandomizers.getSpawnBehaviourByChance(spawnOptions);

		// guard: check if a valid option was selected
		if (spawnOption == null)
		{
			log.warn("Could not find valid spawn behaviour option for product ("+ productId +")");
			return;
		}

		// randomize the amount of spawns
		int spawnGroupAmount = (int) MarketplaceRandomizers.getValidRandomNumberByRange(spawnOption.spawnAmount, 1, 1, 0, MAX_SPAWN_AMOUNT);
		ArrayList<EbsSpawn> spawns = spawnOption.spawns;
		String spawnPointType = spawnOption.spawnPointType;

		// multiply the spawn amount when chaos mode is activate
		if (manager.isChaosModeActive())
		{
			spawnGroupAmount *= manager.getConfig().chaosModeSpawnMultiplier();
		}

		// guard: make sure the spawn behaviours are valid
		if (spawns == null)
		{
			log.warn("Could not find valid spawn behaviours for product ("+ productId +")");
			return;
		}

		// valid option is found
		log.debug("Executing spawn behaviours for product ("+ productId +") and transaction ("+ transactionId +")");

		// guard: check if the conditions are satisfied
		// NOTE: this should happen after the timer is being set!
		if (!verifyConditions(spawnOption.conditions, null))
		{
			return;
		}

		// make sure spawning is on client thread for e.g. using client instance
		int finalSpawnGroupAmount = spawnGroupAmount;
		manager.getPlugin().runOnClientThread(() -> {

			// execute the spawn for the requested amount of times along with all spawn behaviours
			for (int spawnGroupIndex = 0; spawnGroupIndex < finalSpawnGroupAmount; spawnGroupIndex++)
			{

				// spawn points can be shared between spawns depending on the settings
				SpawnPoint spawnPoint = null;

				for (EbsSpawn spawn : spawns)
				{
					int spawnAmount = (int) MarketplaceRandomizers.getValidRandomNumberByRange(spawn.spawnAmount, 1, 1, 0, MAX_SPAWN_AMOUNT);

					for (int spawnIndex = 0; spawnIndex < spawnAmount; spawnIndex++)
					{
						int spawnDelayMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(spawnOption.spawnDelayMs, 0, 0);
						EbsModelPlacement placement = spawn.modelPlacement;

						// determine whether we re-use the same spawn-point we already got
						// or if we should generate a new one
						if (spawnPoint == null || INDIVIDUAL_SPAWN_POINT_TYPE.equals(spawnPointType))
						{
							spawnPoint = spawnManager.getSpawnPoint(placement, modelWorldPoint);
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
		EbsRandomRange durationMs = spawn.durationMs;
		int randomDurationMs = -1;
		Instant spawnedObjectExpiredAt = null;

		// guard: make sure the selected model is valid
		if (modelSet == null || modelSet.ids == null)
		{
			log.warn("Could not find valid model set when triggering spawn behaviour!");
			return;
		}

		if (durationMs != null)
		{
			randomDurationMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(durationMs, 0,0);
			spawnedObjectExpiredAt = Instant.now().plusMillis(randomDurationMs);
		}

		SpawnedObject spawnedObject = new SpawnedObject(
			this,
			client,
			spawnPoint,
			spawn,
			modelSet,
			spawnedObjectExpiredAt
		);

		// schedule showing of the object as it is initially hidden
		showSpawnedObject(spawnedObject, spawnDelayMs);

		// set timeout to hide object at the exact time of duration
		// the cleanup will remove it later, but this runs on game ticks
		if (randomDurationMs >= 0)
		{
			hideSpawnedObject(spawnedObject, spawnDelayMs + randomDurationMs);
		}

		// register the objects to the product and manager to make the spawn point unavailable
		spawnedObjects.add(spawnedObject);
		spawnAmount += 1;
		spawnManager.registerSpawnedObjectPlacement(spawnedObject);
	}

	public void triggerEffects(ArrayList<EbsEffect> effects, int startDelayMs, SpawnedObject spawnedObject, MarketplaceEffect marketplaceEffect, boolean forceModelAnimation, ResetEffectHandler resetModelAnimationHandler)
	{

		// guard: make sure the effect is valid
		if (effects == null)
		{
			return;
		}

		Iterator<EbsEffect> effectIterator = effects.iterator();

		// only trigger the first effect, because we want certain effect frames to be blocking for the future ones
		// depending on what the outcome of the conditions are, for this reason we cannot queue all frames at once
		// because certain conditions need to be evaluated when executing the frame and not beforehand.
		triggerEffect(effectIterator, startDelayMs, spawnedObject, marketplaceEffect, forceModelAnimation, resetModelAnimationHandler);
	}

	public void triggerEffect(Iterator<EbsEffect> effectIterator, int frameDelayMs, SpawnedObject spawnedObject, MarketplaceEffect marketplaceEffect, boolean forceModelAnimation, ResetEffectHandler resetModelAnimationHandler)
	{

		// guard: check if the iterator is valid
		if (effectIterator == null || !effectIterator.hasNext())
		{
			return;
		}

		EbsEffect effect = effectIterator.next();
		boolean isLast = !effectIterator.hasNext();
		int durationMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(effect.durationMs, 0, 0);
		ArrayList<EbsCondition> conditions = effect.conditions;
		boolean blockingConditions = effect.blockingConditions; // TMP: for backwards compatibility, remove after full update
		boolean breakOnInvalidConditions = effect.breakOnInvalidConditions || blockingConditions;
		boolean breakOnValidConditions = effect.breakOnValidConditions;

		// schedule all the individual effects
		manager.getPlugin().scheduleOnClientThread(() -> {
			int nextFrameDelayMs = durationMs;
			int innerDelayMs = 0; // potentially handy in the future to delay a full effect
			boolean conditionsVerified = verifyConditions(conditions, spawnedObject);
			boolean satisfiedInvalidBreak = conditionsVerified || !breakOnInvalidConditions;
			boolean satisfiedValidBreak = !conditionsVerified || !breakOnValidConditions;

			// check if we should schedule the next frame
			// this is allowed when there are no blocking conditions or when the conditions of this frame are okay
			if (satisfiedInvalidBreak && satisfiedValidBreak)
			{
				triggerEffect(effectIterator, nextFrameDelayMs, spawnedObject, marketplaceEffect, forceModelAnimation, resetModelAnimationHandler);
			}

			// guard: check if all the conditions for this effect are met
			if (!conditionsVerified)
			{
				// when this is the last one make sure we still reset the model animations or hide them
				if (isLast && resetModelAnimationHandler != null) {
					resetModelAnimationHandler.execute(innerDelayMs);
				}
				return;
			}

			triggerSpawnOptions(spawnedObject, effect.spawnOptions);
			triggerModelExpired(spawnedObject, effect.modelExpired);
			triggerProductExpired(effect.productExpired);
			triggerModelAnimation(
				spawnedObject,
				effect.modelAnimation,
				// when there is no model animation add the frame duration to the delay for the reset model animation handler
				// to be executed after the frame has been done
				(effect.modelAnimation == null ? nextFrameDelayMs + innerDelayMs : innerDelayMs),
				forceModelAnimation,
				isLast ? resetModelAnimationHandler : null
			);
			triggerModelOverhead(spawnedObject, effect.modelOverhead, innerDelayMs);
			triggerModelSetUpdate(
				spawnedObject,
				effect.modelSet
			);
			triggerPlayerGraphic(effect.playerGraphic, innerDelayMs);
			triggerPlayerAnimation(effect.playerAnimation, innerDelayMs);
			triggerPlayerEquipment(effect.playerEquipment, innerDelayMs);
			triggerPlayerMovement(effect.playerMovement, innerDelayMs);
			triggerInterfaceWidgets(effect.interfaceWidgets, innerDelayMs);
			triggerMenuOptions(effect.menuOptions, innerDelayMs);
			triggerSoundEffect(effect.soundEffect, innerDelayMs);
			triggerStateChange(spawnedObject, effect.stateChange, innerDelayMs);
			triggerNotifications(marketplaceEffect, effect.notifications, innerDelayMs);
			triggerProjectiles(spawnedObject, effect.projectiles, innerDelayMs);
			triggerEffectsOptions(effect.effectsOptions, spawnedObject, innerDelayMs);
		}, frameDelayMs);
	}

	public void triggerEffectsOptions(ArrayList<ArrayList<EbsEffect>> effectsOptions, SpawnedObject spawnedObject, int startDelayMs)
	{
		// trigger the animations on this single spawned object
		ArrayList<EbsEffect> effects = MarketplaceRandomizers.getRandomEntryFromList(effectsOptions);
		triggerEffects(effects, spawnedObject, startDelayMs);
	}

	public void triggerEffects(ArrayList<EbsEffect> effects, SpawnedObject spawnedObject, int startDelayMs)
	{
		triggerEffects(
			effects,
			startDelayMs,
			spawnedObject,
			null,
			false,
			null
		);
	}

	private boolean verifyIntervalDelay(EbsInterval interval, Instant lastTriggeredAt, Instant startedAt, int triggeredAmount)
	{
		Instant now = Instant.now();
		boolean triggerOnStart = interval.triggerOnStart;
		int repeatAmount = interval.repeatAmount;
		int startDelayMs = interval.startDelayMs;
		int delayMs = interval.delayMs;
		Instant delayReferenceTime = (lastTriggeredAt == null ? startedAt : lastTriggeredAt);
		boolean hasTriggeredAtLeastOnce = (lastTriggeredAt != null);

		// guard: check if the amount has passed
		// NOTE: -1 repeat amount for infinity!
		if (repeatAmount >= 0 && triggeredAmount >= repeatAmount)
		{
			return false;
		}

		// guard: check if the interval has not passed
		// this interval can be skipped the initial time when requested
		if (delayReferenceTime != null && delayReferenceTime.plusMillis(delayMs).isAfter(now))
		{
			if (hasTriggeredAtLeastOnce || !triggerOnStart)
			{
				return false;
			}
		}

		// guard: check if the minimum required time after the creation time has not passed
		if (startDelayMs > 0 && startedAt.plusMillis(startDelayMs).isAfter(now))
		{
			return false;
		}

		return true;
	}

	private boolean verifyConditions(ArrayList<EbsCondition> conditions)
	{
		return verifyConditions(conditions, null);
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
		String stateType = condition.stateType;
		String stateFormat = condition.stateFormat;
		String stateComparator = condition.stateComparator;
		String stateKey = condition.stateKey;
		String stateValue = condition.stateValue;
		Double chance = condition.chance;
		String combatStyle = condition.combatStyle;
		Integer regionId = condition.regionId;
		ArrayList<EbsCondition> orConditions = condition.or;
		ArrayList<EbsCondition> andConditions = condition.and;
		ArrayList<EbsCondition> notConditions = condition.not;
		boolean orConditionsVerified = false;

		// guard: check if the chance is passed
		if (!MarketplaceRandomizers.rollChance(chance))
		{
			return false;
		}

		// guard: check if the required state is valid
		if (!verifyStateValue(stateType, stateFormat, stateComparator, stateKey, stateValue, spawnedObject))
		{
			return false;
		}

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

		// guard: check whether a specific combat style is requested
		if (combatStyle != null && !manager.getFightStateManager().isCurrentCombatStyle(combatStyle))
		{
			return false;
		}

		// guard: check whether a specific region is requested
		if (regionId != null && regionId != manager.getCurrentRegionId())
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
		if (spawnedObject != null && spawnInViewRadius >= 0 && !spawnedObject.isInView(spawnInViewRadius))
		{
			return false;
		}

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

		// check if one NOT condition is not valid to set to false
		// the collection is handled as an AND statement on the root-level of the NOT conditions
		if (notConditions != null)
		{
			for (EbsCondition notCondition : notConditions)
			{
				// NOTE: when verified we will set to false
				if (verifyCondition(notCondition, spawnedObject))
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

	private boolean verifyStateValue(String stateType, String stateFormat, String stateComparator, String stateKey, String comparedStateValue, SpawnedObject spawnedObject)
	{

		// guard: make sure the state check is valid
		if (stateType == null || stateKey == null)
		{
			return true;
		}

		// default state is NULL
		String currentStateValue = null;

		if (PRODUCT_STATE_TYPE.equals(stateType)) {
			currentStateValue = stateFrameValues.get(stateKey);
		} else if (OBJECT_STATE_TYPE.equals(stateType) && spawnedObject != null) {
			currentStateValue = spawnedObject.getStateFrameValue(stateKey);
		}

		// guard: compare with a simple if when the current state is NULL
		// it might be possible a NULL state is requested, so it can still be verified
		if (currentStateValue == null)
		{
			if (comparedStateValue == null) {
				return true;
			} else {
				return false;
			}
		}

		// guard: check for other formats
		try {
			if (INTEGER_STATE_FORMAT.equals(stateFormat)) {
				int currentValue = (currentStateValue == null ? 0 : Integer.parseInt(currentStateValue));
				int comparedValue = (comparedStateValue == null ? 0 : Integer.parseInt(comparedStateValue));

				switch (stateComparator) {
					case EQUAL_STATE_COMPARISON: return currentValue == comparedValue;
					case LARGER_EQUAL_THAN_STATE_COMPARISON: return currentValue >= comparedValue;
					case LARGER_THAN_STATE_COMPARISON: return currentValue > comparedValue;
					case SMALLER_EQUAL_THAN_STATE_COMPARISON: return currentValue <= comparedValue;
					case SMALLER_THAN_STATE_COMPARISON: return currentValue < comparedValue;
				}
			}
		} catch (Exception exception) {
			return false;
		}

		// make the null-safe comparison
		return currentStateValue.equals(comparedStateValue);
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

		// guard: skip any passed time calculations when it is the full time-frame
		if (minMs == 0 && maxMs == Integer.MAX_VALUE)
		{
			return true;
		}

		long passedMs = getDurationPassed().toMillis();

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

		long durationMs = getDuration().toMillis();

		// guard: make sure the duration is valid
		if (durationMs <= 0)
		{
			return false;
		}

		long passedMs = getDurationPassed().toMillis();
		double passedTimePercentage = (((double) passedMs) / ((double) durationMs));

		// guard: check whether the current elapsed time is outside of the requested range
		if (passedTimePercentage < minPercentage || passedTimePercentage > maxPercentage)
		{
			return false;
		}

		return true;
	}

	private void triggerModelSetUpdate(SpawnedObject spawnedObject, EbsModelSet modelSet)
	{
		// guard: make sure the spawned object and model set are valid
		if (spawnedObject == null || modelSet == null)
		{
			return;
		}

		spawnedObject.setModelSet(modelSet);
		spawnedObject.updateModelSet(true);
	}

	private void triggerModelExpired(SpawnedObject spawnedObject, Boolean modelExpired)
	{

		// guard: check if everything is valid and if a model expiry should be triggered
		if (spawnedObject == null || modelExpired == null || !modelExpired)
		{
			return;
		}

		spawnedObject.expireNow();
	}

	private void triggerProductExpired(Boolean productExpired)
	{

		// guard: check if everything is valid and if a product expiry should be triggered
		if (productExpired == null || !productExpired)
		{
			return;
		}

		stop(false);
	}

	private void triggerModelAnimation(SpawnedObject spawnedObject, EbsAnimationFrame animation, int baseDelayMs, boolean force, ResetEffectHandler resetAnimationHandler)
	{
		// guard: make sure the spawned object and animation are valid
		if (spawnedObject == null || animation == null)
		{

			// always execute the reset animation handler when passed to for example hide the object
			if (resetAnimationHandler != null)
			{
				resetAnimationHandler.execute(baseDelayMs);
			}
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

		// create a unique key for this specific product and graphic frame
		// this feels unique enough to support multiple spot anims at once
		// overflowing of this integer is NOT a problem, so we can just add everything
		int graphicKey = hashCode() + manager.getClient().getGameCycle();

		handleEffectFrame(graphicFrame, delayMs, (startDelayMs) -> {
			animationManager.setPlayerGraphic(graphicKey, graphicFrame.id, graphicFrame.height, startDelayMs, graphicFrame.durationMs);
		}, (resetDelayMs) -> {
			animationManager.resetPlayerGraphic(graphicKey, resetDelayMs);
		});
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
			transmogManager.addEffect(this, equipmentFrame,  null);
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
			animationManager.addEffect(this, movementFrame, null);
		}, baseDelayMs + delayMs);
	}

	private void triggerModelOverhead(SpawnedObject spawnedObject, EbsModelOverheadFrame overheadFrame, int baseDelayMs)
	{
		SpawnOverheadManager spawnOverheadManager = manager.getSpawnOverheadManager();

		// guard: make sure the frame is valid
		if (overheadFrame == null)
		{
			return;
		}

		int delayMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(overheadFrame.delayMs, 0, 0);

		manager.getPlugin().scheduleOnClientThread(() -> {
			spawnOverheadManager.addEffect(this, overheadFrame, spawnedObject);
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
				widgetManager.addEffect(this, interfaceWidgetFrame, null);
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
				menuManager.addEffect(this, menuOptionFrame, null);
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

	private void triggerStateChange(SpawnedObject spawnedObject, EbsStateFrame stateFrame, int baseDelayMs)
	{

		// guard: make sure the state change is valid
		if (stateFrame == null)
		{
			return;
		}

		String stateType = stateFrame.type;
		String stateKey = stateFrame.key;
		Integer delayMs = stateFrame.delayMs;

		// guard: make sure the properties are valid
		if (stateType == null || stateKey == null || delayMs == null)
		{
			return;
		}

		manager.getPlugin().scheduleOnClientThread(() -> {
			if (PRODUCT_STATE_TYPE.equals(stateType)) {
				String currentStateValue = stateFrameValues.get(stateKey);
				String newStateValue = calculateNewStateValue(currentStateValue, stateFrame);
				stateFrameValues.put(stateKey, newStateValue);
			} else if (OBJECT_STATE_TYPE.equals(stateType) && spawnedObject != null) {
				String currentStateValue = spawnedObject.getStateFrameValue(stateKey);
				String newStateValue = calculateNewStateValue(currentStateValue, stateFrame);
				spawnedObject.setStateFrameValue(stateKey, newStateValue);
			}
		}, baseDelayMs + delayMs);
	}

	private String calculateNewStateValue(String currentStateValue, EbsStateFrame stateFrame)
	{

		// guard: make sure the state change is valid
		if (stateFrame == null)
		{
			return null;
		}

		String stateFormat = stateFrame.format;
		String stateOperation = stateFrame.operation;
		String stateDeltaValue = stateFrame.value;
		String newStateValue = stateDeltaValue;

		// due to some operations that can be done here make sure we fallback to the current value
		// when something goes wrong here, because things like division by 0 can happen
		try {
			// for integers use different handler
			if (INTEGER_STATE_FORMAT.equals(stateFormat)) {
				int currentValue = (currentStateValue == null ? 0 : Integer.parseInt(currentStateValue));
				int deltaValue = (stateDeltaValue == null ? 0 : Integer.parseInt(stateDeltaValue));
				int newValue = deltaValue;

				switch (stateOperation) {
					case SET_STATE_OPERATION:
						// empty
						break;
					case ADD_STATE_OPERATION:
						newValue = currentValue + deltaValue;
						break;
					case SUBTRACT_STATE_OPERATION:
						newValue = currentValue - deltaValue;
						break;
					case DIVIDE_STATE_OPERATION:
						newValue = currentValue / deltaValue;
						break;
					case MULTIPLY_STATE_OPERATION:
						newValue = currentValue * deltaValue;
						break;
				}

				newStateValue = String.valueOf(newValue);
			}
		} catch (Exception exception) {
			log.error("Could not calculate new state value because of error:", exception);
			return currentStateValue;
		}

		return newStateValue;
	}

	private void triggerNotifications(MarketplaceEffect marketplaceEffect, ArrayList<EbsNotification> notifications, int delayMs)
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
			manager.getNotificationManager().handleEbsNotifications(this, marketplaceEffect, notifications);
		}, delayMs);
	}

	private void triggerProjectiles(SpawnedObject spawnedObject, ArrayList<EbsProjectileFrame> projectiles, int delayMs)
	{
		// guard: make sure there are valid frames
		if (projectiles == null)
		{
			return;
		}

		Iterator<EbsProjectileFrame> projectileFrameIterator = projectiles.iterator();
		Client client = manager.getClient();

		while (projectileFrameIterator.hasNext())
		{
			EbsProjectileFrame projectileFrame = projectileFrameIterator.next();
			int projectileDelayMs = (int) MarketplaceRandomizers.getValidRandomNumberByRange(projectileFrame.delayMs, 0, 0, 0, Integer.MAX_VALUE);

			manager.getPlugin().scheduleOnClientThread(() -> {

				// calculate most of the things here to make sure the delay is taken into account once the projectile is really fired
				// for example an animation can still take place before the projectile is fired.
				Integer projectileId = projectileFrame.id;
				String startLocationType = projectileFrame.startLocationType;
				String endLocationType = projectileFrame.endLocationType;
				Boolean followEndLocation = projectileFrame.followEndLocation;
				Boolean inLineOfSight = projectileFrame.inLineOfSight;
				Boolean avoidExistingSpawns = projectileFrame.avoidExistingSpawns;
				Boolean avoidPlayerLocation = projectileFrame.avoidPlayerLocation;
				Boolean avoidInvalidOverlay = projectileFrame.avoidInvalidOverlay;
				Actor endActor = followEndLocation ? getActorByLocationType(endLocationType) : null;
				LocalPoint startReferenceLocation = getLocalPointByLocationType(startLocationType, spawnedObject);
				LocalPoint endReferenceLocation = getLocalPointByLocationType(endLocationType, spawnedObject);


				// offset the locations with possible radiuses
				LocalPoint startLocation = offsetLocalPointByRadius(startReferenceLocation, inLineOfSight, avoidExistingSpawns, avoidPlayerLocation, avoidInvalidOverlay, projectileFrame.startLocationRadiusRange);
				LocalPoint endLocation = offsetLocalPointByRadius(endReferenceLocation, inLineOfSight, avoidExistingSpawns, avoidPlayerLocation, avoidInvalidOverlay, projectileFrame.endLocationRadiusRange);
				WorldView worldView = client.getTopLevelWorldView();

				int startZ = projectileFrame.startZ;
				int slope = projectileFrame.slope;
				int startHeight = projectileFrame.startHeight;
				int endHeight = projectileFrame.endHeight;
				int durationMs = projectileFrame.durationMs;
				int durationCycles = (durationMs / GAME_CYCLE_DURATION_MS);

				// guard: make sure the parameters are valid
				if (projectileId == null || durationCycles <= 0 || startLocation == null || endLocation == null)
				{
					return;
				}

				WorldPoint startWorldLocation = WorldPoint.fromLocal(client, startLocation);
				WorldPoint endWorldLocation = WorldPoint.fromLocal(client, endLocation);

				int plane = worldView.getPlane();
				int sceneX = startLocation.getSceneX();
				int sceneY = startLocation.getSceneY();
				int tileHeight = worldView.getTileHeights()[plane][sceneX][sceneY];
				int correctedStartZ = tileHeight + startZ; // correct for the starting tile height
				int startCycle = client.getGameCycle();
				int endCycle = startCycle + durationCycles;

				// trigger start spawns
				triggerSpawnOptionsAtWorldPoint(startWorldLocation, projectileFrame.startSpawnOptions);

				Projectile projectile = worldView.createProjectile(
					projectileId,
					plane,
					startLocation.getX(),
					startLocation.getY(),
			    	correctedStartZ,
					startCycle,
					endCycle,
					slope,
					startHeight,
					endHeight,
					endActor,
					endLocation.getX(),
					endLocation.getY()
				);
				worldView.getProjectiles().addLast(projectile);

				// trigger end spawns
				manager.getPlugin().scheduleOnClientThread(() -> {
					triggerSpawnOptionsAtWorldPoint(endWorldLocation, projectileFrame.endSpawnOptions);
				}, durationMs);
			}, delayMs + projectileDelayMs);
		}
	}

	@Nullable
	private Actor getActorByLocationType(String locationType)
	{
		Player localPlayer = manager.getClient().getLocalPlayer();
		Actor interactingActor = localPlayer.getInteracting();

		switch (locationType)
		{
			case CURRENT_TILE_LOCATION_TYPE:
			case PREVIOUS_TILE_LOCATION_TYPE:
				return localPlayer;
			case MODEL_TILE_LOCATION_TYPE:
				return null;
			case INTERACTING_TILE_LOCATION_TYPE:
				return interactingActor;
		}

		return null;
	}

	@Nullable
	private LocalPoint getLocalPointByLocationType(String locationType, SpawnedObject spawnedObject)
	{
		Client client = manager.getClient();
		WorldView worldView = client.getTopLevelWorldView();
		Actor actor = getActorByLocationType(locationType);
		LocalPoint actorLocation = null;

		if (actor != null)
		{
			actorLocation = actor.getLocalLocation();
		}

		switch (locationType)
		{
			case CURRENT_TILE_LOCATION_TYPE:
			case INTERACTING_TILE_LOCATION_TYPE:
				return actorLocation;
			case PREVIOUS_TILE_LOCATION_TYPE:
				WorldPoint previousLocation =  manager.getSpawnManager().getPreviousPlayerLocation();

				// guard: fallback to the current location
				if (previousLocation == null)
				{
					return actorLocation;
				}

				return LocalPoint.fromWorld(worldView, previousLocation);
			case MODEL_TILE_LOCATION_TYPE:
				return spawnedObject.getSpawnPoint().getLocalPoint(client);
		}

		return null;
	}

	private LocalPoint offsetLocalPointByRadius(LocalPoint localPoint, boolean inLineOfSight, boolean avoidExistingSpawns, boolean avoidPlayerLocation, boolean avoidInvalidOverlay, EbsRandomRange radiusRange)
	{

		// guard: ensure valid parameters
		if (localPoint == null || radiusRange == null)
		{
			return localPoint;
		}

		Client client = manager.getClient();
		WorldPoint worldPoint =  WorldPoint.fromLocal(client, localPoint);
		int minRadius = radiusRange.min.intValue();
		int maxRadius = radiusRange.max.intValue();

		SpawnPoint spawnPoint = manager.getSpawnManager().getSpawnPoint(
				minRadius,
				maxRadius,
				inLineOfSight,
				avoidExistingSpawns,
				avoidPlayerLocation,
				avoidInvalidOverlay,
				worldPoint
		);

		// guard: its possible no candidate can be found and if so we just pick the original point
		if (spawnPoint == null)
		{
			return localPoint;
		}

		return spawnPoint.getLocalPoint(client);
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
				null,
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
			if (hideEffects == null || hideEffects.size() <= 0)
			{
				spawnedObject.hide();
				manager.getSpawnManager().deregisterSpawnedObjectPlacement(spawnedObject);
				return;
			}

			// trigger the effects and at the end hide the object
			triggerEffects(
				hideEffects,
				0,
				spawnedObject,
				null,
				true,
				(resetDelayMs) -> {
					handleSpawnedObject(spawnedObject, resetDelayMs, () -> {
						spawnedObject.hide();
						manager.getSpawnManager().deregisterSpawnedObjectPlacement(spawnedObject);
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
	 * Calculate how long this product is going to be active
	 */
	public Duration getDuration()
	{
		return Duration.between(startedAt, expiredAt);
	}

	/**
	 * Calculate how long this product is going to be active
	 */
	public Duration getDurationLeft()
	{
		Instant now = Instant.now();

		return Duration.between(now, expiredAt);
	}

	/**
	 * Calculate the amount of time in milliseconds since this product was started
	 */
	public Duration getDurationPassed()
	{
		Instant now = Instant.now();

		return Duration.between(startedAt, now);
	}

	/**
	 * Get whether this effect can be seen as potentially dangerous.
	 */
	public boolean isDangerous()
	{
		return ebsProduct.dangerous;
	}
}
