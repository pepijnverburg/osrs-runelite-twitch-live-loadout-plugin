package com.twitchliveloadout.marketplace;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ModelData;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class MarketplaceProduct
{

	/**
	 * The Twitch transaction attributed to this product
	 */
	@Getter
	private final MarketplaceTransaction transaction;

	/**
	 * The options of models this product can spawn, not that this is two dimensional array
	 * where the first index is the options it randomly selects one from and the second index
	 * are all the models that should be spawned at the same time
	 */
	@Getter
	@Setter(AccessLevel.PROTECTED)
	private MarketplaceModel[][] marketplaceModels = {};

	/**
	 * The amount of milliseconds before this product disappears
	 */
	@Getter
	@Setter(AccessLevel.PROTECTED)
	private int expiryTimeMs = 0;

	/**
	 * The graphic that is shown on the player when spawning
	 */
	@Getter
	@Setter(AccessLevel.PROTECTED)
	private int playerGraphicId = -1;

	/**
	 * The animation the player will perform when spawning
	 */
	@Getter
	@Setter(AccessLevel.PROTECTED)
	private int playerAnimationId = -1;

	/**
	 * Whether a spawner object should be used before showing the actual model
	 */
	@Getter
	@Setter(AccessLevel.PROTECTED)
	private boolean useSpawner = true;

	/**
	 * The duration the spawner object is there before the actual model appears
	 */
	@Getter
	@Setter(AccessLevel.PROTECTED)
	private int spawnerDurationMs = 1000;

	/**
	 * The maximum delay the randomizer will be based on to spawn the product
	 */
	@Getter
	@Setter(AccessLevel.PROTECTED)
	private int randomMaxSpawnDelayMs = 0;

	/**
	 * A list of all the spawned objects for this product
	 */
	@Getter
	private final ConcurrentHashMap<WorldPoint, CopyOnWriteArrayList<MarketplaceSpawnedObject>> spawnedObjects = new ConcurrentHashMap();

	public MarketplaceProduct()
	{
		onInitializeProduct();
	}

	public abstract void onInitializeProduct();

	public void onBeforeSpawn()
	{
		// empty by default
	}

	public void onInitializeModel(ModelData model, int modelId)
	{
		// empty by default
	}

	public void onAfterSpawn()
	{
		// empty by default
	}

	public void onGameTick()
	{

	}

	private void removeSpawnedObject(MarketplaceSpawnedObject spawnedObject)
	{
		spawnedObjects.remove(spawnedObject);

		// remove this world point if no objects are left
		if (spawnedObjects.size() <= 0)
		{
			WorldPoint worldPoint = spawnedObject.getSpawnPoint().getWorldPoint();
			spawnedObjects.remove(worldPoint);
		}
	}

	public void handleAllSpawnedObjects(MarketplaceManager.SpawnedObjectHandler handler) {
		for (CopyOnWriteArrayList<MarketplaceSpawnedObject> spawnedObjects : spawnedObjects.values())
		{
			for (MarketplaceSpawnedObject spawnedObject : spawnedObjects)
			{
				handler.execute(spawnedObject);
			}
		}
	}

	public interface SpawnedObjectHandler {
		public void execute(MarketplaceSpawnedObject spawnedObject);
	}

	/**
	 * The default spawn point generator
	 *
	 * @param marketplaceManager
	 * @return list of spawn points where duplicates of this product are put
	 */
	public static Collection<MarketplaceSpawnPoint> getSpawnPoints(MarketplaceManager marketplaceManager)
	{
		final ArrayList<MarketplaceSpawnPoint> spawnPoints = new ArrayList();
		final MarketplaceSpawnPoint defaultSpawnPoint = marketplaceManager.getOutwardSpawnPoint(1, 2, 10, null);

		if (defaultSpawnPoint != null)
		{
			spawnPoints.add(defaultSpawnPoint);
		}

		return spawnPoints;
	}
}
