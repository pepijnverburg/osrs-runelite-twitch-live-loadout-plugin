package com.twitchliveloadout.marketplace;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.animations.AnimationManager;
import com.twitchliveloadout.marketplace.products.EbsProduct;
import com.twitchliveloadout.marketplace.products.EbsProductDuration;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.StreamerProduct;
import com.twitchliveloadout.marketplace.spawns.SpawnManager;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.marketplace.transmogs.TransmogManager;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.TwitchSegmentType;
import com.twitchliveloadout.twitch.TwitchStateEntry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PlayerChanged;
import okhttp3.Response;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class MarketplaceManager {

	@Getter
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchApi twitchApi;

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
	 * List to keep track of all the active products
	 */
	private final CopyOnWriteArrayList<MarketplaceProduct> activeProducts = new CopyOnWriteArrayList();

	/**
	 * List of all streamer products from the Twitch configuration segment
	 */
	private CopyOnWriteArrayList<StreamerProduct> streamerProducts = new CopyOnWriteArrayList();

	/**
	 * List of all EBS products from Twitch
	 */
	private CopyOnWriteArrayList<EbsProduct> ebsProducts = new CopyOnWriteArrayList();

	/**
	 * List of all the possible product durations from Twitch
	 */
	private CopyOnWriteArrayList<EbsProductDuration> ebsProductDurations = new CopyOnWriteArrayList();

	/**
	 * List of all extension transactions that should be handled
	 */
	private CopyOnWriteArrayList<TwitchTransaction> queuedTransactions = new CopyOnWriteArrayList();
	private Instant transactionsLastCheckedAt = null;

	public MarketplaceManager(TwitchLiveLoadoutPlugin plugin, TwitchApi twitchApi, Client client, TwitchLiveLoadoutConfig config)
	{
		this.plugin = plugin;
		this.twitchApi = twitchApi;
		this.client = client;
		this.config = config;
		this.spawnManager = new SpawnManager(plugin, client);
		this.animationManager = new AnimationManager(plugin, client);
		this.transmogManager = new TransmogManager();
	}

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
			new TwitchTransaction(), // TODO
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
	public void updateEffects()
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

	public void updateStreamerProducts()
	{
		JsonObject segmentContent = twitchApi.getConfigurationSegmentContent(TwitchSegmentType.BROADCASTER);

		if (segmentContent == null)
		{
			return;
		}

		JsonArray rawStreamerProducts = segmentContent.getAsJsonArray(TwitchStateEntry.STREAMER_PRODUCTS.getKey());

		if (rawStreamerProducts == null)
		{
			return;
		}

		CopyOnWriteArrayList<StreamerProduct> newStreamerProducts = new CopyOnWriteArrayList();

		rawStreamerProducts.forEach((element) -> {
			try {
				JsonObject rawStreamerProduct = element.getAsJsonObject();
				StreamerProduct streamerProduct = new Gson().fromJson(rawStreamerProduct, StreamerProduct.class);
				newStreamerProducts.add(streamerProduct);
			} catch (Exception exception) {
				// empty
			}
		});

		streamerProducts = newStreamerProducts;
	}

	public void updateEbsProducts()
	{
		try {
			Response response = twitchApi.getEbsProducts();
			JsonObject result = (new JsonParser()).parse(response.body().string()).getAsJsonObject();
			Boolean status = result.get("status").getAsBoolean();
			String message = result.get("message").getAsString();
			JsonArray products = result.getAsJsonArray("products");
			JsonArray durations = result.getAsJsonArray("durations");

			// guard: check if the status is valid
			// if not we want to keep the old products intact
			if (!status)
			{
				log.warn("Could not fetch EBS products from Twitch as the status is invalid with message: "+ message);
				return;
			}

			CopyOnWriteArrayList<EbsProduct> newEbsProducts = new CopyOnWriteArrayList();
			CopyOnWriteArrayList<EbsProductDuration> newEbsProductDurations = new CopyOnWriteArrayList();

			products.forEach((element) -> {
				EbsProduct ebsProduct = new Gson().fromJson(element, EbsProduct.class);
				newEbsProducts.add(ebsProduct);
			});
			durations.forEach((element) -> {
				EbsProductDuration ebsProductDuration = new Gson().fromJson(element, EbsProductDuration.class);
				newEbsProductDurations.add(ebsProductDuration);
			});

			ebsProducts = newEbsProducts;
			ebsProductDurations = newEbsProductDurations;
		} catch (Exception exception) {
			// empty
		}
	}

	public void updateTransactions()
	{
		try {
			Response response = twitchApi.getEbsTransactions(transactionsLastCheckedAt);
			JsonObject result = (new JsonParser()).parse(response.body().string()).getAsJsonObject();
			Boolean status = result.get("status").getAsBoolean();
			String message = result.get("message").getAsString();
			JsonArray newTransactions = result.getAsJsonArray("transactions");

			// guard: check if the status is valid
			if (!status)
			{
				log.warn("Could not fetch EBS transactions from Twitch as the status is invalid with message: "+ message);
				return;
			}

			log.info("Amount of new transactions: "+ newTransactions.size());

			newTransactions.forEach((element) -> {
				TwitchTransaction twitchTransaction = new Gson().fromJson(element, TwitchTransaction.class);
				queuedTransactions.add(twitchTransaction);
			});

			// only update the last checked at if everything is successful
			transactionsLastCheckedAt = Instant.now();
		} catch (Exception exception) {
			// empty
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
