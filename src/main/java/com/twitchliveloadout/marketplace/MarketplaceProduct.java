package com.twitchliveloadout.marketplace;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.ModelData;
import net.runelite.api.RuneLiteObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

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

		initializeProduct();
	}

	private void initializeProduct()
	{

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
		EbsProduct.Behaviour behaviour = ebsProduct.behaviour;
		ArrayList<EbsProduct.SpawnBehaviourOption> spawnBehaviourOptions = behaviour.spawnBehaviourOptions;
		EbsProductInterval spawnBehaviourInterval = behaviour.spawnBehaviourInterval;

		// guard: check if objects need to be spawned
		if (spawnBehaviourOptions == null)
		{
			return;
		}

		// make sure the behaviour interval is valid
		if (spawnBehaviourInterval == null)
		{
			spawnBehaviourInterval = generateDefaultInterval();
		}

		// guard: check if the amount has passed
		if (spawnBehaviourCounter >= spawnBehaviourInterval.repeatAmount)
		{
			return;
		}

		// guard: check if the interval has not passed
		if (lastSpawnBehaviour != null && lastSpawnBehaviour.plusMillis(spawnBehaviourInterval.delayMs).isAfter(now))
		{
			return;
		}

		// select a random option
		EbsProduct.SpawnBehaviourOption spawnBehaviourOption = rollSpawnBehaviour(spawnBehaviourOptions);

		// guard: check if a valid option was selected
		if (spawnBehaviourOption == null)
		{
			return;
		}

		// an option is selected so we can change the timer and count
		lastSpawnBehaviour = now;
		spawnBehaviourCounter += 1;

		// randomize the amount of spawns
		Integer spawnAmountMin = spawnBehaviourOption.spawnAmountMin;
		Integer spawnAmountMax = spawnBehaviourOption.spawnAmountMax;
		int spawnAmountMinValidated = (spawnAmountMin == null) ? 1 : spawnAmountMin;
		int spawnAmountMaxValidated = (spawnAmountMax == null) ? 1 : spawnAmountMax;
		int spawnAmount = spawnAmountMinValidated + ((int) (Math.random() * ((float) Math.abs(spawnAmountMaxValidated - spawnAmountMinValidated))));
		ArrayList<EbsProduct.SpawnBehaviour> spawnBehaviours = spawnBehaviourOption.spawnBehaviours;

		// guard: make sure the spawn behaviours are valid
		if (spawnBehaviours == null)
		{
			return;
		}

		// execute the spawn for the requested amount of times along with all spawn behaviours
		for (int spawnIndex = 0; spawnIndex < spawnAmount; spawnIndex++)
		{
			for (EbsProduct.SpawnBehaviour spawnBehaviour : spawnBehaviours)
			{
				triggerSpawnBehaviour(spawnBehaviour);
			}
		}
	}

	private void triggerSpawnBehaviour(EbsProduct.SpawnBehaviour spawnBehaviour)
	{
		EbsModelPlacement placement =  spawnBehaviour.modelPlacement;
		Client client = manager.getClient();
		ArrayList<MarketplaceSpawnedObject> placedSpawnedObjects = new ArrayList();
		ArrayList<ModelData> placedModels = new ArrayList();

		// make sure there are valid placement parameters
		if (placement == null) {
			placement = generateDefaultModelPlacement();
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
			return;
		}

		// roll a random set of model IDs
		EbsProduct.Model model = rollModel(spawnBehaviour.models);

		// guard: make sure the selected model is valid
		if (model == null || model.modelIds == null)
		{
			return;
		}

		Double modelScale = model.modelScale;

		// loop all the individual model IDs making up this model
		for (int modelId : model.modelIds)
		{
			RuneLiteObject modelObject = client.createRuneLiteObject();
			ModelData modelData = client.loadModelData(modelId)
				.cloneVertices()
				.cloneColors();

//			MarketplaceSpawnedObject spawnedObject = new MarketplaceSpawnedObject(
//				client,
//				object,
//				marketplaceModel,
//				spawnPoint,
//				product
//			);

			// check if the model needs further customization (e.g. recolors)
			// this needs to be done before applying the light to the model
			if (modelScale != null)
			{
				int modelSize = (int) (MODEL_REFERENCE_SIZE * modelScale);
				modelData.scale(modelSize, modelSize, modelSize);
			}

			// set the object to the model
			modelObject.setModel(modelData.light());

			// move to the spawn location
			modelObject.setLocation(spawnPoint.getLocalPoint(), spawnPoint.getPlane());

			// keep track of all the models and spawned objects that were placed
			placedModels.add(modelData);
//			placedSpawnedObjects.add(spawnedObject);
		}
	}

	private EbsProduct.Model rollModel(ArrayList<EbsProduct.Model> models)
	{
		if (models == null || models.size() < 0)
		{
			return null;
		}

		Random modelSelector = new Random();
		int modelIndex = modelSelector.nextInt(models.size());
		EbsProduct.Model model = models.get(modelIndex);

		return model;
	}

	private EbsProduct.SpawnBehaviourOption rollSpawnBehaviour(ArrayList<EbsProduct.SpawnBehaviourOption> spawnBehaviourOptions)
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
			for (EbsProduct.SpawnBehaviourOption option : spawnBehaviourOptions)
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

	public void onClientTick()
	{

	}

	private EbsProductInterval generateDefaultInterval()
	{
		EbsProductInterval interval = new EbsProductInterval();
		interval.chance = 1.0d;
		interval.delayMs = 0;
		interval.durationMs = 0;
		interval.repeatAmount = 1;

		return interval;
	}

	private EbsModelPlacement generateDefaultModelPlacement()
	{
		EbsModelPlacement placement = new EbsModelPlacement();
		placement.locationType = CURRENT_TILE_LOCATION_TYPE;
		placement.radiusType = OUTWARD_RADIUS_TYPE;
		placement.radius = DEFAULT_RADIUS;

		return placement;
	}
}
