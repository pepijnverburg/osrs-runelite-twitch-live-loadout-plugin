package com.twitchliveloadout.marketplace;

import com.google.gson.Gson;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.animations.AnimationManager;
import com.twitchliveloadout.marketplace.products.EbsProduct;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.StreamerProduct;
import com.twitchliveloadout.marketplace.spawns.SpawnManager;
import com.twitchliveloadout.marketplace.transmogs.TransmogManager;
import com.twitchliveloadout.twitch.TwitchState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PlayerChanged;

import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class MarketplaceManager {

	@Getter
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;

	@Getter
	private final Client client;

	@Getter
	private final TwitchLiveLoadoutConfig config;

	@Getter
	private final SpawnManager spawnManager;

	@Getter
	private final AnimationManager animationManager;

	@Getter
	private final TransmogManager transmogManager;

	/**
	 * List to keep track of all the queued products
	 */
	private final CopyOnWriteArrayList<MarketplaceProduct> queuedProducts = new CopyOnWriteArrayList();

	/**
	 * List to keep track of all the active products
	 */
	private final CopyOnWriteArrayList<MarketplaceProduct> activeProducts = new CopyOnWriteArrayList();

	public EbsProduct tmpEbsProduct;

	public MarketplaceManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client, TwitchLiveLoadoutConfig config)
	{
		this.plugin = plugin;
		this.twitchState = twitchState;
		this.client = client;
		this.config = config;
		this.spawnManager = new SpawnManager(plugin, client);
		this.animationManager = new AnimationManager(plugin, client);
		this.transmogManager = new TransmogManager();

		// test
		String json = "{\"id\":\"falador-party\",\"enabled\":true,\"type\":\"object-spawn\",\"name\":\"Falador Party\",\"description\":\"\",\"behaviour\":{\"interfaceEffectType\":\"shake-screen\",\"interfaceEffectInterval\":{\"chance\":0.5,\"delayMs\":1000,\"durationMs\":10000,\"repeatAmount\":1},\"playerAnimation\":{\"idleAnimationId\":100,\"runAnimationId\":100,\"walkAnimationId\":100},\"playerEquipment\":{\"amuletItemId\":1,\"bootsItemId\":1,\"chestItemId\":1,\"glovesItemId\":1,\"helmItemId\":1,\"legsItemId\":1,\"shieldItemId\":1,\"weaponItemId\":1},\"spawnInterval\":{\"chance\":0.5,\"delayMs\":1000,\"durationMs\":1000,\"repeatAmount\":1},\"spawnOptions\":[{\"chance\":0.5,\"spawnAmountMin\":5,\"spawnAmountMax\":10,\"spawns\":[{\"modelSets\":[{\"modelIds\":[2226],\"modelRotationType\":\"random\",\"modelScale\":0.9}],\"modelPlacement\":{\"locationType\":\"current-tile\",\"radiusType\":\"outward-radius\",\"radius\":10},\"hideAnimation\":{\"modelAnimation\":{\"id\":100,\"delayMs\":1000,\"durationMs\":1000},\"playerAnimation\":{\"id\":100,\"delayMs\":1000,\"durationMs\":1000},\"playerGraphic\":{\"id\":100,\"delayMs\":1000,\"durationMs\":1000}}}]}]}}";

		// falador party
		json = "{\"id\":\"falador-party\",\"enabled\":true,\"category\":\"object-spawn\",\"name\":\"Falador Party\",\"description\":\"\",\"behaviour\":{\"spawnOptions\":[{\"chance\":1,\"spawnAmount\":{\"min\":5,\"max\":10},\"spawnDelayMs\":{\"min\":0,\"max\":200},\"spawns\":[{\"modelSets\":[{\"modelIds\":[2226],\"modelRotationType\":\"random\"},{\"modelIds\":[2227],\"modelRotationType\":\"random\"},{\"modelIds\":[2228],\"modelRotationType\":\"random\"}],\"showAnimation\":{\"modelAnimation\":{\"id\":498,\"durationMs\":2400},\"playerAnimation\":{\"id\":866,\"delayMs\":1000}}}]}]}}";

		// jad
		json = "{\"id\":\"mini-jad\",\"enabled\":true,\"category\":\"npc-spawn\",\"name\":\"Mini Jad\",\"description\":\"A Jad following the streamer around and attacking them.\",\"behaviour\":{\"spawnOptions\":[{\"chance\":1,\"spawnAmount\":{\"min\":1},\"spawns\":[{\"modelSets\":[{\"modelIds\":[9319],\"modelRotationType\":\"player\",\"modelScale\":{\"min\":0.5}}],\"movementAnimations\":{\"idleAnimationId\":2650,\"walkAnimationId\":2651},\"randomAnimationInterval\":{\"chance\":1,\"delayMs\":5000},\"randomAnimations\":[{\"modelAnimation\":{\"id\":2652,\"durationMs\":1000},\"playerGraphic\":{\"id\":451,\"delayMs\":1000},\"playerAnimation\":{\"id\":404,\"delayMs\":2000}}]}]}]}}";

		// drunk
		json = "{\"id\":\"drunk-walk\",\"enabled\":true,\"category\":\"animation\",\"name\":\"Falador Party\",\"description\":\"\",\"behaviour\":{\"playerAnimations\":{\"idle\":3040,\"walk\":3039,\"run\":3039}}}";

		// on fire
		json = "{\"id\":\"on-fire\",\"enabled\":true,\"category\":\"animation\",\"name\":\"On FIRE!\",\"description\":\"\",\"behaviour\":{\"spawnInterval\":{\"delayMs\":100,\"chance\":1,\"repeatAmount\":-1},\"spawnOptions\":[{\"chance\":1,\"spawns\":[{\"modelPlacement\":{\"locationType\":\"previous-tile\",\"radiusType\":\"radius\",\"radius\":0},\"modelSets\":[{\"modelIds\":[26585]}],\"showAnimation\":{\"modelAnimation\":{\"id\":6853}}}]}]}}";

		tmpEbsProduct = new Gson().fromJson(json, EbsProduct.class);
		log.warn("Loaded TMP ebs product:");
		log.warn(tmpEbsProduct.name);
	}

	boolean didTestSpawn = false;

	/**
	 * Check for new products that should be spawned
	 */
	public void queueNewProducts()
	{

		// guard: only apply the products when the player is logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		// TODO: link to Twitch transactions
		if (!didTestSpawn) {
			startProduct(tmpEbsProduct);
			didTestSpawn = true;
		}

		int playerGraphicId = config.devPlayerGraphicId();

		if (playerGraphicId > 0) {
			client.getLocalPlayer().setGraphic(playerGraphicId);
		}
	}

	/**
	 * Check to clean any existing products that are expired
	 */
	public void cleanProducts()
	{
		// TODO
	}

	private void startProduct(EbsProduct ebsProduct)
	{

		// guard: check if the product is valid and enabled
		if (ebsProduct == null || !ebsProduct.enabled)
		{
			log.error("Could not start invalid or disabled EBS product.");
			return;
		}

		log.info("Starting EBS product: "+ ebsProduct.id);

		MarketplaceProduct newProduct = new MarketplaceProduct(
			this,
			new ExtensionTransaction(), // TODO
			ebsProduct,
			new StreamerProduct() // TODO
		);

		activeProducts.add(newProduct);
	}

	private void stopProduct(MarketplaceProduct product)
	{
		activeProducts.remove(product);
		product.stop();
	}

	/**
	 * Handle player changes
	 */
	public void onPlayerChanged(PlayerChanged playerChanged)
	{

		// guard: make sure we are logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		// guard: only update the local player
		if (playerChanged.getPlayer() != client.getLocalPlayer())
		{
			return;
		}

		animationManager.recordOriginalAnimations();
		animationManager.updateEffectAnimations();
	}

	/**
	 * Handle game state changes to respawn all objects, because they are cleared
	 * when a new scene is being loaded.
	 */
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState newGameState = event.getGameState();

		// guard: only respawn on the loading event
		// this means all spawned objects are removed from the scene
		// and need to be queued for a respawn, this is done periodically
		if (newGameState == GameState.LOADING)
		{
			spawnManager.registerDespawn();
		}
	}

	/**
	 * Handle all active products
	 */
	public void handleActiveProducts()
	{

		// guard: don't do anything when not logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		spawnManager.respawnRequested();
		spawnManager.recordPlayerLocation();

		// for all active products the game tick should be triggered
		for (MarketplaceProduct product : activeProducts)
		{
			product.handleBehaviour();
		}
	}

	/**
	 * Handle client tick
	 */
	public void onClientTick()
	{

		// for all active products the tick should be triggered
		for (MarketplaceProduct product : activeProducts)
		{
			product.onClientTick();
		}
	}

	/**
	 * Handle plugin shutdown / marketplace disable
	 */
	public void shutDown()
	{
		animationManager.revertAnimations();
		transmogManager.revertEquipment();
	}
}
