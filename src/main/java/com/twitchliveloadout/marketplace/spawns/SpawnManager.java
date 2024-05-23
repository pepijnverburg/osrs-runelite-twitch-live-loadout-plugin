package com.twitchliveloadout.marketplace.spawns;

import com.google.common.collect.EvictingQueue;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.MarketplaceRandomizers;
import com.twitchliveloadout.marketplace.products.EbsModelPlacement;
import com.twitchliveloadout.marketplace.products.EbsRandomRange;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class SpawnManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final Client client;

	/**
	 * Lookup to see which world points are taken for future spawns
	 */
	private final ConcurrentHashMap<WorldPoint, CopyOnWriteArrayList<SpawnedObject>> objectPlacements = new ConcurrentHashMap<>();

	/**
	 * History of all the previous player tiles used for spawning relative to previous locations
	 */
	private EvictingQueue<WorldPoint> playerLocationHistory = EvictingQueue.create(PLAYER_TILE_HISTORY_SIZE);

	public SpawnManager(TwitchLiveLoadoutPlugin plugin, Client client)
	{
		this.plugin = plugin;
		this.client = client;
	}

	/**
	 * Check if any active products need to be respawned in the scene
	 */
	public void respawnRequested()
	{
		ArrayList<SpawnedObject> respawnQueue = new ArrayList<>();

		// loop all spawned objects and check whether they should respawn
		handleAllSpawnedObjects((spawnedObject) -> {

			// only respawn if in viewport and a respawn is required
			// to prevent animations to be reset all the time
			if (spawnedObject.isRespawnRequired() && spawnedObject.isInRegion()) {
				respawnQueue.add(spawnedObject);
				spawnedObject.setRespawnRequired(false);
			}
		});

		// guard: skip the queue when it is empty
		if (respawnQueue.size() <= 0)
		{
			return;
		}

		// run all respawns at the same time
		plugin.runOnClientThread(() -> {
			for (SpawnedObject spawnedObject : respawnQueue)
			{
				spawnedObject.respawn();
			}
		});
	}

	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState newGameState = gameStateChanged.getGameState();

		// only respawn on the loading event
		// this means all spawned objects are removed from the scene
		// and need to be queued for a respawn, this is done periodically
		if (newGameState == GameState.LOADING)
		{
			registerDespawn();
		}
	}

	/**
	 * Register all spawned objects to require a respawn to show them again.
	 */
	private void registerDespawn()
	{
		// set all objects to require a respawn, because after a loading of
		// a new scene all custom objects are cleared
		handleAllSpawnedObjects((spawnedObject) -> {
			spawnedObject.setRespawnRequired(true);
		});
	}

	public void recordPlayerLocation()
	{
		LocalPoint currentLocalPoint = client.getLocalPlayer().getLocalLocation();
		WorldPoint currentWorldPoint = WorldPoint.fromLocal(client, currentLocalPoint);

		// guard: check if this location is already at the end of the cyclic list
		if (currentWorldPoint.equals(getCurrentPlayerLocation()))
		{
			return;
		}

		playerLocationHistory.add(currentWorldPoint);
	}

	public WorldPoint getCurrentPlayerLocation()
	{
		return getPlayerLocationByHistoryOffset(0);
	}

	public WorldPoint getPreviousPlayerLocation()
	{
		return getPlayerLocationByHistoryOffset(1);
	}

	private WorldPoint getPlayerLocationByHistoryOffset(int offset)
	{
		int size = playerLocationHistory.size();
		int requestedIndex = size - 1 - offset;

		// guard: make sure the index is valid
		if (requestedIndex < 0 || requestedIndex >= size)
		{
			return null;
		}

		WorldPoint requestedWorldPoint = playerLocationHistory.toArray(new WorldPoint[size])[requestedIndex];
		return requestedWorldPoint;
	}

	/**
	 * Register a collection of spawned objects to the placement lookup for easy access
	 * to all spawned objects and to keep track of which tiles are taken.
	 */
	public void registerSpawnedObjectPlacement(SpawnedObject spawnedObject)
	{
		WorldPoint worldPoint = spawnedObject.getSpawnPoint().getWorldPoint();

		// check whether this world point already has a spawned object to add to
		if (objectPlacements.containsKey(worldPoint)) {
			CopyOnWriteArrayList<SpawnedObject> existingObjects = objectPlacements.get(worldPoint);
			existingObjects.add(spawnedObject);
		} else {
			CopyOnWriteArrayList<SpawnedObject> existingObjects = new CopyOnWriteArrayList<>();
			existingObjects.add(spawnedObject);
			objectPlacements.put(worldPoint, existingObjects);
		}
	}

	/**
	 * Remove a collection of spawned objects from the placement lookup.
	 */
	public void deregisterSpawnedObjectPlacement(SpawnedObject spawnedObject)
	{
		WorldPoint worldPoint = spawnedObject.getSpawnPoint().getWorldPoint();

		// guard: check if the placements are known
		if (!objectPlacements.containsKey(worldPoint))
		{
			return;
		}

		// remove from the existing spawned objects
		CopyOnWriteArrayList<SpawnedObject> existingObjects = objectPlacements.get(worldPoint);
		existingObjects.remove(spawnedObject);

		// remove the placement if empty so this world point is free again
		if (existingObjects.size() <= 0)
		{
			objectPlacements.remove(worldPoint);
		}
	}

	public void moveSpawnedObject(SpawnedObject spawnedObject, SpawnPoint newSpawnPoint)
	{
		SpawnPoint previousSpawnPoint = spawnedObject.getSpawnPoint();

		// guard: make sure the spawn point really changed
		if (previousSpawnPoint.getWorldPoint().equals(newSpawnPoint.getWorldPoint()))
		{
			return;
		}

		// first deregister BEFORE changing the spawn point
		deregisterSpawnedObjectPlacement(spawnedObject);

		// update the spawn point
		spawnedObject.setSpawnPoint(newSpawnPoint);

		// (re)register to the updated one
		registerSpawnedObjectPlacement(spawnedObject);

		// finally re-render the object
		spawnedObject.respawn();
	}

	/**
	 * Shortcut to loop all the spawned objects
	 */
	public void handleAllSpawnedObjects(MarketplaceManager.SpawnedObjectHandler handler)
	{
		for (CopyOnWriteArrayList<SpawnedObject> spawnedObjects : objectPlacements.values())
		{
			Iterator<SpawnedObject> spawnedObjectIterator = spawnedObjects.iterator();

			while (spawnedObjectIterator.hasNext())
			{
				SpawnedObject spawnedObject = spawnedObjectIterator.next();

				handler.execute(spawnedObject);
			}
		}
	}

	public SpawnPoint getOutwardSpawnPoint(int minRadius, int maxRadius, int radiusStepSize, boolean inLineOfSight, boolean avoidExistingSpawns, boolean avoidPlayerLocation, boolean avoidInvalidOverlay, WorldPoint referenceWorldPoint)
	{
		for (int radius = minRadius; radius <= maxRadius; radius++)
		{
			int randomizedRadius = radius + (int) Math.round(Math.random() * ((float) radiusStepSize));
			int usedRadius = Math.min(randomizedRadius, maxRadius);
			SpawnPoint candidateSpawnPoint = getSpawnPoint(usedRadius, usedRadius, inLineOfSight, avoidExistingSpawns, avoidPlayerLocation, avoidInvalidOverlay, referenceWorldPoint);

			if (candidateSpawnPoint != null) {
				return candidateSpawnPoint;
			}
		}

		return null;
	}

	public SpawnPoint getSpawnPoint(EbsModelPlacement placement, WorldPoint modelWorldPoint)
	{

		// make sure there are valid placement parameters
		if (placement == null)
		{
			placement = new EbsModelPlacement();
		}

		EbsRandomRange radiusRange = placement.radiusRange;
		int minRadius = (int) (radiusRange == null ? DEFAULT_MIN_RADIUS : radiusRange.min);
		int maxRadius = (int) MarketplaceRandomizers.getValidRandomNumberByRange(radiusRange, DEFAULT_MIN_RADIUS, DEFAULT_MAX_RADIUS, ABSOLUTE_MIN_RADIUS, ABSOLUTE_MAX_RADIUS);
		int radiusStepSize  = placement.radiusStepSize;
		String radiusType = placement.radiusType;
		String locationType = placement.locationType;
		Boolean inLineOfSight = placement.inLineOfSight;
		Boolean avoidExistingSpawns = placement.avoidExistingSpawns;
		Boolean avoidPlayerLocation = placement.avoidPlayerLocation;
		Boolean avoidInvalidOverlay = placement.avoidInvalidOverlay;
		WorldPoint referenceWorldPoint = client.getLocalPlayer().getWorldLocation();

		// check if we should change the reference to the previous tile
		// NOTE: current tile is not needed to be handled, because this is the default!
		if (PREVIOUS_TILE_LOCATION_TYPE.equals(locationType))
		{
			referenceWorldPoint = getPreviousPlayerLocation();

			if (referenceWorldPoint == null)
			{
				return null;
			}
		}

		if (MODEL_TILE_LOCATION_TYPE.equals(locationType) && modelWorldPoint != null)
		{
			referenceWorldPoint = modelWorldPoint;
		}

		if (NO_RADIUS_TYPE.equals(radiusType))
		{
			return new SpawnPoint(referenceWorldPoint);
		}

		if (OUTWARD_RADIUS_TYPE.equals(radiusType))
		{
			return getOutwardSpawnPoint(minRadius, maxRadius, radiusStepSize, inLineOfSight, avoidExistingSpawns, avoidPlayerLocation, avoidInvalidOverlay, referenceWorldPoint);
		}

		return getSpawnPoint(minRadius, maxRadius, inLineOfSight, avoidExistingSpawns, avoidPlayerLocation, avoidInvalidOverlay, referenceWorldPoint);
	}

	public SpawnPoint getSpawnPoint(int minRadius, int maxRadius, boolean inLineOfSight, boolean avoidExistingSpawns, boolean avoidPlayerLocation, boolean avoidInvalidOverlay, WorldPoint referenceWorldPoint)
	{
		final ArrayList<SpawnPoint> candidateSpawnPoints = new ArrayList<>();
		final int[][] collisionFlags = getSceneCollisionFlags();
		final Player player = client.getLocalPlayer();
		final WorldArea playerArea = player.getWorldArea();
		final WorldPoint playerWorldPoint = player.getWorldLocation();

		// make sure the reference WORLD point is always valid
		if (referenceWorldPoint == null)
		{
			referenceWorldPoint = playerWorldPoint;
		}

		// make sure the max radius is valid
		if (maxRadius < minRadius)
		{
			maxRadius = minRadius;
		}

		LocalPoint referenceLocalPoint = LocalPoint.fromWorld(client, referenceWorldPoint);

		// guard: make sure the LOCAL point is always valid
		if (referenceLocalPoint == null)
		{
			referenceLocalPoint = client.getLocalPlayer().getLocalLocation();
		}
		
		final int plane = referenceWorldPoint.getPlane();
		final Scene scene = client.getScene();
		final short[][][] overlayIds = scene.getOverlayIds();
		final short[][][] underlayIds = scene.getUnderlayIds();
		final int sceneX = referenceLocalPoint.getSceneX();
		final int sceneY = referenceLocalPoint.getSceneY();

		// loop all the possible tiles for the requested radius and look for
		// the candidate tiles to spawn the object on
		for (int deltaX = -1 * maxRadius; deltaX <= maxRadius; deltaX++) {
			for (int deltaY = -1 * maxRadius; deltaY <= maxRadius; deltaY++) {
				int sceneAttemptX = sceneX + deltaX;
				int sceneAttemptY = sceneY + deltaY;
				double deltaDiagonal = Math.sqrt(Math.pow(deltaX, 2) + Math.pow(deltaY, 2));

				// guard: skip all tiles that are not distant enough
				if (Math.abs(deltaX) < minRadius && Math.abs(deltaY) < minRadius && deltaDiagonal < minRadius)
				{
					continue;
				}

				// guard: make sure the tile has collision flags
				if (
					sceneAttemptX < 0
					|| sceneAttemptX >= collisionFlags.length
					|| sceneAttemptY < 0
					|| sceneAttemptY >= collisionFlags[sceneAttemptX].length
				) {
					continue;
				}

				int flagData = collisionFlags[sceneAttemptX][sceneAttemptY];
				int blockedFlags = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
				boolean isWalkable = (flagData & blockedFlags) == 0;
				int underlayOverlayIdOffset = (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2;
				int underlayOverlayAttemptX = sceneAttemptX + underlayOverlayIdOffset;
				int underlayOverlayAttemptY = sceneAttemptY + underlayOverlayIdOffset;
				short underlayId = underlayIds[plane][underlayOverlayAttemptX][underlayOverlayAttemptY];
				short overlayId = overlayIds[plane][underlayOverlayAttemptX][underlayOverlayAttemptY];
				boolean isBlackOnMinimap = (avoidInvalidOverlay && underlayId == 0 && overlayId == 0);

				// guard: make sure the tile is walkable
				if (!isWalkable || isBlackOnMinimap)
				{
					continue;
				}

				LocalPoint localPoint = LocalPoint.fromScene(sceneAttemptX, sceneAttemptY);
				WorldPoint worldPoint = WorldPoint.fromLocal(client, localPoint);

				// guard: check if this world point is already taken by another spawned object
				if (avoidExistingSpawns && objectPlacements.containsKey(worldPoint))
				{
					continue;
				}

				// guard: skip candidates that are the current player location
				// because when rendering the model it is always on top of the player
				// which is almost always not looking very nice
				if (avoidPlayerLocation && worldPoint.equals(playerWorldPoint))
				{
					continue;
				}

				// guard: make sure the tile is in line of sight
				if (inLineOfSight && !playerArea.hasLineOfSightTo(client.getTopLevelWorldView(), worldPoint))
				{
					continue;
				}

				// we have found a walkable tile to spawn the object on
				candidateSpawnPoints.add(new SpawnPoint(worldPoint));
			}
		}

		// guard: make sure there are candidate spawn points to prevent the
		// random selection of one to trigger errors
		if (candidateSpawnPoints.size() <= 0)
		{
			return null;
		}

		final Random spawnPointSelector = new Random();
		final int spawnPointIndex = spawnPointSelector.nextInt(candidateSpawnPoints.size());
		final SpawnPoint spawnPoint = candidateSpawnPoints.get(spawnPointIndex);

		return spawnPoint;
	}

	private int[][] getSceneCollisionFlags() {
		final CollisionData[] collisionMaps = client.getCollisionMaps();
		int[][] collisionFlags = new int[Constants.SCENE_SIZE][Constants.SCENE_SIZE];

		// if we have map collision flags we populate the starting point with them
		if (collisionMaps != null) {
			collisionFlags = collisionMaps[client.getPlane()].getFlags();
		}

		return collisionFlags;
	}
}
