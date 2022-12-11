package com.twitchliveloadout.marketplace.spawns;

import com.google.common.collect.EvictingQueue;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;

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
	private final ConcurrentHashMap<WorldPoint, CopyOnWriteArrayList<SpawnedObject>> objectPlacements = new ConcurrentHashMap();

	/**
	 * History of all the previous player tiles used for spawning relative to previous locations
	 */
	EvictingQueue<WorldPoint> playerLocationHistory = EvictingQueue.create(PLAYER_TILE_HISTORY_SIZE);

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

		// guard: only apply the products when the player is logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		ArrayList<SpawnedObject> respawnQueue = new ArrayList();
		LocalPoint playerLocalPoint = client.getLocalPlayer().getLocalLocation();
		WorldPoint playerWorldPoint = WorldPoint.fromLocal(client, playerLocalPoint);

		// loop all spawned objects and check whether they should respawn
		handleAllSpawnedObjects((spawnedObject) -> {
			WorldPoint worldPoint = spawnedObject.getSpawnPoint().getWorldPoint();
			int distanceToPlayer = worldPoint.distanceTo(playerWorldPoint);
			boolean isInView = distanceToPlayer < Constants.SCENE_SIZE;
			boolean isRespawnRequired = spawnedObject.isRespawnRequired();

			// only respawn if in viewport and a respawn is required
			// to prevent animations to be reset all the time
			if (isInView && isRespawnRequired) {
				respawnQueue.add(spawnedObject);
				spawnedObject.setRespawnRequired(false);
			}
		});

		// run all respawns at the same time
		plugin.runOnClientThread(() -> {
			for (SpawnedObject spawnedObject : respawnQueue)
			{
				spawnedObject.respawn();
			}
		});
	}

	/**
	 * Register all spawned objects to require a respawn to show them again.
	 */
	public void registerDespawn()
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
	public void registerSpawnedObjectPlacements(ArrayList<SpawnedObject> objects)
	{
		Iterator iterator = objects.iterator();

		while (iterator.hasNext())
		{
			SpawnedObject spawnedObject = (SpawnedObject) iterator.next();
			LocalPoint localPoint = spawnedObject.getSpawnPoint().getLocalPoint(client);
			WorldPoint worldPoint = WorldPoint.fromLocal(client, localPoint);

			// check whether this world point already has a spawned object to add to
			if (objectPlacements.containsKey(worldPoint)) {
				CopyOnWriteArrayList<SpawnedObject> existingObjects = objectPlacements.get(worldPoint);
				existingObjects.add(spawnedObject);
			} else {
				CopyOnWriteArrayList<SpawnedObject> existingObjects = new CopyOnWriteArrayList();
				existingObjects.add(spawnedObject);
				objectPlacements.put(worldPoint, existingObjects);
			}
		}
	}

	/**
	 * Shortcut to loop all the spawned objects
	 */
	public void handleAllSpawnedObjects(SpawnedObjectHandler handler) {
		for (CopyOnWriteArrayList<SpawnedObject> spawnedObjects : objectPlacements.values())
		{
			for (SpawnedObject spawnedObject : spawnedObjects)
			{
				handler.execute(spawnedObject);
			}
		}
	}

	public SpawnPoint getOutwardSpawnPoint(int maxRadius, WorldPoint referenceWorldPoint)
	{
		return getOutwardSpawnPoint(1, 2, maxRadius, referenceWorldPoint);
	}

	public SpawnPoint getOutwardSpawnPoint(int startRadius, int radiusStepSize, int maxRadius, WorldPoint referenceWorldPoint)
	{
		for (int radius = startRadius; radius < maxRadius; radius += radiusStepSize)
		{
			int randomizedRadius = radius + ((int) (Math.random() * radiusStepSize));
			int usedRadius = Math.min(randomizedRadius, maxRadius);
			SpawnPoint candidateSpawnPoint = getSpawnPoint(usedRadius, referenceWorldPoint);

			if (candidateSpawnPoint != null) {
				return candidateSpawnPoint;
			}
		}

		return null;
	}

	public SpawnPoint getSpawnPoint(int radius, WorldPoint referenceWorldPoint)
	{
		final ArrayList<SpawnPoint> candidateSpawnPoints = new ArrayList();
		final int playerPlane = client.getPlane();
		final int[][] collisionFlags = getSceneCollisionFlags();

		// make sure the reference local point is always valid
		if (referenceWorldPoint == null)
		{
			referenceWorldPoint = client.getLocalPlayer().getWorldLocation();
		}

		LocalPoint referenceLocalPoint = LocalPoint.fromWorld(client, referenceWorldPoint);
		final int sceneX = referenceLocalPoint.getSceneX();
		final int sceneY = referenceLocalPoint.getSceneY();

		// loop all the possible tiles for the requested radius and look for
		// the candidate tiles to spawn the object on
		for (int deltaX = -1 * radius; deltaX <= radius; deltaX++) {
			for (int deltaY = -1 * radius; deltaY <= radius; deltaY++) {
				int sceneAttemptX = sceneX + deltaX;
				int sceneAttemptY = sceneY + deltaY;

				// guard: make sure the flag can be found
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

				// guard: check if this tile is not walkable
				if ((flagData & blockedFlags) != 0)
				{
					continue;
				}

				LocalPoint localPoint = LocalPoint.fromScene(sceneAttemptX, sceneAttemptY);
				WorldPoint worldPoint = WorldPoint.fromLocal(client, localPoint);

				// guard: check if this world point is already taken by another spawned object
				if (objectPlacements.containsKey(worldPoint))
				{
					continue;
				}

				// we have found a walkable tile to spawn the object on
				candidateSpawnPoints.add(new SpawnPoint(worldPoint, playerPlane));
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

	public interface SpawnedObjectHandler {
		public void execute(SpawnedObject spawnedObject);
	}
}
