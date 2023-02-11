package com.twitchliveloadout.marketplace;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.animations.AnimationManager;
import com.twitchliveloadout.marketplace.interfaces.MenuManager;
import com.twitchliveloadout.marketplace.interfaces.WidgetManager;
import com.twitchliveloadout.marketplace.notifications.NotificationManager;
import com.twitchliveloadout.marketplace.products.*;
import com.twitchliveloadout.marketplace.sounds.SoundManager;
import com.twitchliveloadout.marketplace.spawns.SpawnManager;
import com.twitchliveloadout.marketplace.spawns.SpawnedObject;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.marketplace.transmogs.TransmogManager;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.TwitchSegmentType;
import com.twitchliveloadout.twitch.TwitchStateEntry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PlayerChanged;
import net.runelite.client.chat.ChatMessageManager;
import okhttp3.Response;

import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.UPDATE_ACTIVE_PRODUCTS_DELAY_MS;

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

	@Getter
	private final WidgetManager widgetManager;

	@Getter
	private final MenuManager menuManager;

	@Getter
	private final NotificationManager notificationManager;

	@Getter
	private final SoundManager soundManager;

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
	private CopyOnWriteArrayList<String> handledTransactionIds = new CopyOnWriteArrayList();
	private Instant transactionsLastCheckedAt = null;

	/**
	 * Track when the active products were updated for the last time
	 */
	private Instant lastUpdateActiveProductsAt = null;

	public MarketplaceManager(TwitchLiveLoadoutPlugin plugin, TwitchApi twitchApi, Client client, TwitchLiveLoadoutConfig config, ChatMessageManager chatMessageManager)
	{
		this.plugin = plugin;
		this.twitchApi = twitchApi;
		this.client = client;
		this.config = config;
		this.spawnManager = new SpawnManager(plugin, client);
		this.animationManager = new AnimationManager(plugin, client);
		this.transmogManager = new TransmogManager();
		this.notificationManager = new NotificationManager(plugin, chatMessageManager, client);
		this.widgetManager = new WidgetManager(plugin, client);
		this.menuManager = new MenuManager(this);
		this.soundManager = new SoundManager(client);
	}

	/**
	 * Get new Twitch transactions where the effects should be queued for.
	 */
	public void handleNewTwitchTransactions()
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

			newTransactions.forEach((element) -> {

				// try catch for each individual transaction to not have one invalid transaction
				// cancel all others with the top-level try-catch in this function
				try {
					TwitchTransaction twitchTransaction = new Gson().fromJson(element, TwitchTransaction.class);
					String transactionId = twitchTransaction.id;

					// guard: check if this transaction is already handled
					// this is required because we have an offset on the last checked at date
					// because with the HTTP request delays it is possible to miss a transaction
					if (handledTransactionIds.contains(transactionId)) {
						log.info("Skipping Twitch transaction because it was already handled: " + transactionId);
						return;
					}

					queuedTransactions.add(twitchTransaction);
					handledTransactionIds.add(transactionId);
					log.info("Queued a new Twitch transaction with ID: " + transactionId);
				} catch (Exception exception) {
					log.error("Could not parse Twitch Extension transaction due to the following error: ", exception);
				}
			});

			// only update the last checked at if everything is successful
			transactionsLastCheckedAt = Instant.now();
		} catch (Exception exception) {
			// empty
		}
	}

	/**
	 * Check for new products that should be applied. This process is a little bit more complex
	 * then you would expect at first, because we need to hook in to the Twitch product configuration and
	 * transactions. From the transaction we can fetch the Twitch product (by SKU). Then we can check
	 * whether the streamer really configured this product to have a specific effect (done in the configuration service).
	 * If yes, we have a Streamer product containing a reference to the Ebs Product, which contains the effect information.
	 * When applying new transactions we will check whether all of these steps are valid to prevent viewers
	 * triggering any effects that were never configured by the streamer.
	 */
	public void applyQueuedTransactions()
	{

		// guard: only apply the products when the player is logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		Iterator iterator = queuedTransactions.iterator();

		while (iterator.hasNext())
		{
			TwitchTransaction transaction = (TwitchTransaction) iterator.next();

			// try to handle each individual transaction to prevent one invalid transaction in the queue
			// to cancel all other transactions and with that all their effects
			try {
				TwitchProduct twitchProduct = transaction.product_data;

				// guard: make sure the twitch product is valid
				if (twitchProduct == null)
				{
					continue;
				}

				String twitchProductSku = twitchProduct.sku;
				StreamerProduct streamerProduct = getStreamerProductBySku(twitchProductSku);

				// guard: make sure a streamer product is configured for this SKU
				if (streamerProduct == null)
				{
					continue;
				}

				String ebsProductId = streamerProduct.ebsProductId;
				EbsProduct ebsProduct = getEbsProductById(ebsProductId);

				// guard: make sure an EBS product is configured for this streamer product
				if (ebsProduct == null || !ebsProduct.enabled)
				{
					continue;
				}

				log.info("Found a valid transaction that we can start: " + transaction.id);
				log.info("Twitch product SKU: " + twitchProduct.sku);
				log.info("Streamer product name: " + streamerProduct.name);
				log.info("Ebs product ID: " + ebsProduct.id);

				// remove the transaction now it is going to be handled
				// we do this after the validation of all products
				// to queue transactions that might receive valid product data later
				queuedTransactions.remove(transaction);

				// guard: check for hardcore protection and dangerous random events
				if (ebsProduct.dangerous && plugin.isDangerousAccountType() && config.marketplaceProtectionEnabled())
				{
					log.info("Skipping transaction because it is deemed dangerous and protection is on: " + transaction.id);
					continue;
				}

				// create a new marketplace product where all the other products
				// are merged together in one instance for reference
				MarketplaceProduct newProduct = new MarketplaceProduct(
					this,
					transaction,
					ebsProduct,
					streamerProduct,
					twitchProduct
				);

				log.info("The marketplace product is configured for the time-frame:");
				log.info("It starts at: " + newProduct.getStartedAt());

				// guard: check if the product is already expired
				// skipping it here is a bit more efficient, because there is a chance
				// some of the behaviours are triggered right before removing it immediately.
				if (newProduct.isExpired())
				{
					log.info("It is skipped, because it has already expired at: " + newProduct.getExpiredAt());
					newProduct.stop();
					continue;
				}

				log.info("It expires at: " + newProduct.getExpiredAt() + ", which is in " + newProduct.getExpiresInMs() + "ms");

				// register this product to be active, which is needed to check
				// for any periodic effects that might need to trigger
				activeProducts.add(newProduct);
			} catch (Exception exception) {
				log.error("Could not handle transaction due to the following error, it is being skipped: ", exception);
				queuedTransactions.remove(transaction);
				log.error("The ID of the skipped transaction was: "+ transaction.id);
			}
		}
	}

	/**
	 * Check to clean any existing products that are expired
	 */
	public void cleanExpiredProducts()
	{
		handleActiveProducts((marketplaceProduct) -> {

			// guard: check if the product is not expired yet
			if (!marketplaceProduct.isExpired())
			{
				return;
			}

			marketplaceProduct.stop();
			activeProducts.remove(marketplaceProduct);

			String transactionId = marketplaceProduct.getTransaction().id;
			String ebsProductId = marketplaceProduct.getEbsProduct().id;
			log.info("Cleaned an expired marketplace product ("+ ebsProductId +") for transaction: "+ transactionId);
		});
	}

	/**
	 * Handle HEAVY periodic effects of the active products,
	 * such as spawning or random animations.
	 */
	private void updateActiveProducts()
	{

		// guard: don't do anything when not logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		// respawn all spawned objects that require it
		// due to for example the reloading of a scene
		spawnManager.respawnRequested();

		// record a history of the player location that we can use
		// when spawning new objects that are relative in some way to the player
		spawnManager.recordPlayerLocation();

		// handle each active product individually
		handleActiveProducts((marketplaceProduct) -> {
			marketplaceProduct.handleBehaviour();
			marketplaceProduct.cleanExpiredSpawnedObjects();
		});
	}

	/**
	 * Update the products the streamer has configured in the Twitch Extension.
	 */
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

	/**
	 * Update the available effects and their configuration from the Twitch EBS.
	 */
	public void updateEbsProducts()
	{

		// guard: skip updating the EBS products when there are no streamer products found
		// this prevents requests to be made by streamers who have not configured the marketplace
		// NOTE: we do allow an initial fetch to get an initial set of EBS products.
		if (streamerProducts.size() <= 0 && ebsProducts.size() > 0)
		{
			return;
		}

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

			// try-catch for every parse, to not let all products crash on one misconfiguration
			products.forEach((element) -> {
				try {
					EbsProduct ebsProduct = new Gson().fromJson(element, EbsProduct.class);
					newEbsProducts.add(ebsProduct);
				} catch (Exception exception) {
					// empty?
				}
			});

			durations.forEach((element) -> {
				try {
					EbsProductDuration ebsProductDuration = new Gson().fromJson(element, EbsProductDuration.class);
					newEbsProductDurations.add(ebsProductDuration);
				} catch (Exception exception) {
					// empty?
				}
			});

			ebsProducts = newEbsProducts;
			ebsProductDurations = newEbsProductDurations;
		} catch (Exception exception) {
			log.warn("Could not fetch the new EBS products due to the following error: ", exception);
		}
	}

	/**
	 * Handle player changes to update current animations or equipment transmogs.
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

		transmogManager.recordOriginalEquipment();
		transmogManager.updateEffectEquipment();
		animationManager.recordOriginalMovementAnimations();
		animationManager.setCurrentMovementAnimations();
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
	 * Handle game ticks
	 */
	public void onGameTick()
	{
		notificationManager.onGameTick();
		widgetManager.onGameTick();
		menuManager.onGameTick();
	}

	/**
	 * Handle on menu option clicks
	 */
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		menuManager.onMenuOptionClicked(event);
	}

	/**
	 * Handle a client tick for all active products for changes
	 * that need to happen really fast and are lightweight.
	 */
	public void onClientTick()
	{
		Instant now = Instant.now();

		// trigger client tick for all active products
		handleActiveProducts((marketplaceProduct) -> {
			marketplaceProduct.onClientTick();
		});

		// custom timer running on client ticks every x ms for more heavy things to be executed
		// this is because the @Schedule is delaying now and then and some of the processes in here
		// are time-sensitive
		if (lastUpdateActiveProductsAt == null || now.isAfter(lastUpdateActiveProductsAt.plusMillis((UPDATE_ACTIVE_PRODUCTS_DELAY_MS))))
		{
			updateActiveProducts();
			lastUpdateActiveProductsAt = now;
		}
	}

	private StreamerProduct getStreamerProductBySku(String twitchProductSku)
	{
		Iterator iterator = streamerProducts.iterator();

		while(iterator.hasNext())
		{
			StreamerProduct candidateStreamerProduct = (StreamerProduct) iterator.next();

			// guard: check if a match is found
			if (twitchProductSku.equals(candidateStreamerProduct.twitchProductSku))
			{
				return candidateStreamerProduct;
			}
		}

		return null;
	}

	private EbsProduct getEbsProductById(String ebsProductId)
	{
		Iterator iterator = ebsProducts.iterator();

		while(iterator.hasNext())
		{
			EbsProduct candidateEbsProduct = (EbsProduct) iterator.next();

			// guard: check if a match is found
			if (ebsProductId.equals(candidateEbsProduct.id))
			{
				return candidateEbsProduct;
			}
		}

		return null;
	}

	/**
	 * Stop all active products
	 */
	public void stopActiveProducts()
	{
		Iterator iterator = activeProducts.iterator();

		while (iterator.hasNext())
		{
			MarketplaceProduct marketplaceProduct = (MarketplaceProduct) iterator.next();
			marketplaceProduct.stop();
		}
	}

	/**
	 * Handle all active products using an iterator
	 */
	public void handleActiveProducts(MarketplaceProductHandler handler)
	{
		Iterator iterator = activeProducts.iterator();

		while (iterator.hasNext())
		{
			MarketplaceProduct marketplaceProduct = (MarketplaceProduct) iterator.next();
			handler.execute(marketplaceProduct);
		}
	}

	/**
	 * Handle plugin shutdown / marketplace disable
	 */
	public void shutDown()
	{
		animationManager.revertAnimations();
		transmogManager.revertEquipment();
		stopActiveProducts();
	}

	public interface EmptyHandler {
		public void execute();
	}

	public interface GetTimeHandler {
		public Instant execute();
	}

	public interface SpawnedObjectHandler {
		public void execute(SpawnedObject spawnedObject);
	}

	public interface MarketplaceProductHandler {
		public void execute(MarketplaceProduct marketplaceProduct);
	}

	public interface PlayerHandler {
		public void execute(Player player);
	}
}
