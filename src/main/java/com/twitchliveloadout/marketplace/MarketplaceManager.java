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
import com.twitchliveloadout.twitch.TwitchState;
import com.twitchliveloadout.twitch.TwitchStateEntry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.PlayerChanged;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.game.ItemManager;
import okhttp3.Response;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class MarketplaceManager {

	@Getter
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchApi twitchApi;
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
	private final CopyOnWriteArrayList<MarketplaceProduct> activeProducts = new CopyOnWriteArrayList<>();

	/**
	 * List of all streamer products from the Twitch configuration segment
	 */
	private CopyOnWriteArrayList<StreamerProduct> streamerProducts = new CopyOnWriteArrayList<>();

	/**
	 * List of all EBS products from Twitch
	 */
	private CopyOnWriteArrayList<EbsProduct> ebsProducts = new CopyOnWriteArrayList<>();

	/**
	 * List of all extension transactions that should be handled
	 */
	private final CopyOnWriteArrayList<TwitchTransaction> queuedTransactions = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<TwitchTransaction> archivedTransactions = new CopyOnWriteArrayList<>();

	private final CopyOnWriteArrayList<String> handledTransactionIds = new CopyOnWriteArrayList<>();
	private String lastTransactionId = null;

	/**
	 * Track when the active products were updated for the last time
	 */
	private Instant lastUpdateActiveProductsAt = null;

	/**
	 * Timer for when all the active products should be active again
	 */
	@Getter
	private boolean isActive = true;

	/**
	 * Flag to identify we are already fetching data so requests are not hoarding
	 */
	private boolean isFetchingEbsTransactions = false;
	private boolean isFetchingEbsProducts = false;

	/**
	 * Lookup to see until when a certain product is cooled down and should stay in the queue if there are any
	 * transactions made at the same time. This lookup also informs the viewers which products are in cooldown.
	 */
	private final ConcurrentHashMap<String, Instant> streamerProductCooldownUntil = new ConcurrentHashMap<>();
	private Instant sharedCooldownUntil;

	public MarketplaceManager(TwitchLiveLoadoutPlugin plugin, TwitchApi twitchApi, TwitchState twitchState, Client client, TwitchLiveLoadoutConfig config, ChatMessageManager chatMessageManager, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.twitchApi = twitchApi;
		this.twitchState = twitchState;
		this.client = client;
		this.config = config;
		this.spawnManager = new SpawnManager(plugin, client);
		this.animationManager = new AnimationManager(plugin, client);
		this.transmogManager = new TransmogManager(plugin, client, itemManager);
		this.notificationManager = new NotificationManager(plugin, config, chatMessageManager, client);
		this.widgetManager = new WidgetManager(plugin, client);
		this.menuManager = new MenuManager();
		this.soundManager = new SoundManager(client, config);
	}

	/**
	 * Get new Twitch transactions where the effects should be queued for.
	 */
	public void handleNewEbsTransactions()
	{

		// guard: block when already fetching
		if (isFetchingEbsTransactions)
		{
			return;
		}

		try {
			isFetchingEbsTransactions = true;
			Response response = twitchApi.getEbsTransactions(lastTransactionId);
			isFetchingEbsTransactions = false;
			JsonObject result = (new JsonParser()).parse(response.body().string()).getAsJsonObject();
			boolean status = result.get("status").getAsBoolean();
			String message = result.get("message").getAsString();
			JsonArray newTransactionsJson = result.getAsJsonArray("transactions");
			ArrayList<TwitchTransaction> newTransactions = new ArrayList<>();
			final AtomicBoolean updatedLastTransactionId = new AtomicBoolean(false);

			// guard: check if the status is valid
			if (!status)
			{
				log.warn("Could not fetch EBS transactions from Twitch as the status is invalid with message: "+ message);
				return;
			}

			newTransactionsJson.forEach((element) -> {

				// try catch for each individual transaction to not have one invalid transaction
				// cancel all others with the top-level try-catch in this function
				try {
					TwitchTransaction twitchTransaction = new Gson().fromJson(element, TwitchTransaction.class);
					String transactionId = twitchTransaction.id;

					// update the ID to tell for next requests to fetch newer transactions
					if (!updatedLastTransactionId.get())
					{
						lastTransactionId = transactionId;
						updatedLastTransactionId.set(true);
					}

					// guard: check if this transaction is already handled
					// this is required because we have an offset on the last checked at date
					// because with the HTTP request delays it is possible to miss a transaction
					if (handledTransactionIds.contains(transactionId)) {
						log.info("Skipping Twitch transaction because it was already handled: " + transactionId);
						return;
					}

					handledTransactionIds.add(transactionId);
					newTransactions.add(twitchTransaction);
					log.info("Queued a new Twitch transaction with ID: " + transactionId);
				} catch (Exception exception) {
					log.error("Could not parse Twitch Extension transaction due to the following error: ", exception);
				}
			});

			// guard: only update the lists and the panel when new transactions were found
			if (newTransactions.size() <= 0)
			{
				return;
			}

			// add in front of the archive as it is from new to old
			archivedTransactions.addAll(0, newTransactions);

			// add at the end of the queue from old to new
			// reverse is needed because the list is from NEW to OLD
			// and we want the oldest transactions to be first in the queue
			Collections.reverse(newTransactions);
			queuedTransactions.addAll(newTransactions);
			updateMarketplacePanel();

			// clean up archived transactions when exceeding maximum amount
			while (archivedTransactions.size() > config.marketplaceTransactionHistoryAmount())
			{
				archivedTransactions.remove(archivedTransactions.size() - 1);
			}
		} catch (Exception exception) {
			// empty
		}

		// always set to false when there is an error
		isFetchingEbsTransactions = false;
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

		// guard: skip when marketplace is paused
		if (!isActive())
		{
			return;
		}

		Iterator<TwitchTransaction> iterator = queuedTransactions.iterator();

		while (iterator.hasNext())
		{
			TwitchTransaction transaction = iterator.next();
			int activeProductAmount = activeProducts.size();

			// guard: check if the maximum amount of active products is exceeded
			// this means this transaction is kept in the queue until one of the products
			// is done with its effects.
			if (activeProductAmount >= config.marketplaceMaxActiveProducts())
			{
				break;
			}

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

				String streamerProductId = streamerProduct.id;
				String ebsProductId = streamerProduct.ebsProductId;
				Instant now = Instant.now();
				Instant cooldownUntil = streamerProductCooldownUntil.get(streamerProductId);
				EbsProduct ebsProduct = getEbsProductById(ebsProductId);
				boolean isProductCoolingDown = cooldownUntil != null && now.isBefore(cooldownUntil);
				boolean isSharedCoolingDown = sharedCooldownUntil != null && now.isBefore(sharedCooldownUntil);
				boolean isValidEbsProduct = ebsProduct != null && ebsProduct.enabled;

				// guard: make sure this product is not cooling down
				// this can be the case when two transactions are done at the same time
				if (isProductCoolingDown || isSharedCoolingDown)
				{
					continue;
				}

				// guard: make sure an EBS product is configured for this streamer product
				if (!isValidEbsProduct)
				{
					continue;
				}

				// keep this info verbose as it is a way of logging to debug any issues that might occur
				// when random events don't trigger and support is required
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
					continue;
				}

				log.info("It expires at: " + newProduct.getExpiredAt() + ", which is in " + newProduct.getExpiresInMs() + "ms");

				// start the product because all the checks have passed
				newProduct.start();

				// update the cooldown after the product is really started an not expired instantly
				// otherwise old transactions can impact the cooldown time
				// NOTE: set the cooldowns AFTER the product is activated completely, because
				// we want cooldowns between the actual activation of them and not between the queueing of them
				// otherwise it is possible that you still have a burst of products being spawned at once because
				// they were all cooled down before and in the queue, but not yet triggered.
				updateCooldown(streamerProduct);

				// register this product to be active, which is needed to check
				// for any periodic effects that might need to trigger
				activeProducts.add(newProduct);
				updateMarketplacePanel();
			} catch (Exception exception) {
				queuedTransactions.remove(transaction);
				log.error("Could not handle transaction due to the following error, it is being skipped: ", exception);
				log.error("The ID of the skipped transaction was: "+ transaction.id);
			}
		}
	}

	/**
	 * Update the cooldown period of a streamer product that will block future usage for x seconds
	 */
	private void updateCooldown(StreamerProduct streamerProduct)
	{

		// guard: make sure the product is valid
		if (streamerProduct == null)
		{
			return;
		}

		Instant now = Instant.now();
		String streamerProductId = streamerProduct.id;
		Integer productCooldownSeconds = streamerProduct.cooldown;
		Integer sharedCooldownSeconds = config.marketplaceSharedCooldownS();

		// check if the shared cooldown needs to be updated
		if (sharedCooldownSeconds > 0)
		{
			sharedCooldownUntil = now.plusSeconds(sharedCooldownSeconds);

			// sync the shared cooldown
			twitchState.setCurrentSharedCooldown(sharedCooldownUntil);
		}

		// check if a product cooldown is needed to be set
		if (productCooldownSeconds > 0)
		{
			// determine the cooldown ending time and update it
			Instant cooldownUntil = now.plusSeconds(productCooldownSeconds);
			streamerProductCooldownUntil.put(streamerProductId, cooldownUntil);

			// sync the cooldown map to the twitch state to update to users
			// that have missed the PubSub message, because they open the stream after the transaction
			twitchState.setCurrentProductCooldowns(streamerProductCooldownUntil);
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
			updateMarketplacePanel();

			String transactionId = marketplaceProduct.getTransaction().id;
			String ebsProductId = marketplaceProduct.getEbsProduct().id;
			log.info("Cleaned an expired marketplace product ("+ ebsProductId +") for transaction: "+ transactionId);
		});
	}

	/**
	 * Rebuild the marketplace panel completely
	 */
	private void updateMarketplacePanel()
	{
		plugin.getPluginPanel().getMarketplacePanel().requestRebuild();
	}

	/**
	 * Get a copied copy of the active products list to prevent mutations
	 */
	public CopyOnWriteArrayList<MarketplaceProduct> getActiveProducts()
	{
		return new CopyOnWriteArrayList<>(activeProducts);
	}

	/**
	 * Get a copied copy of the streamer products list to prevent mutations
	 */
	public CopyOnWriteArrayList<StreamerProduct> getStreamerProducts()
	{
		return new CopyOnWriteArrayList<>(streamerProducts);
	}

	/**
	 * Get a copied copy of the queued transactions list to prevent mutations
	 */
	public CopyOnWriteArrayList<TwitchTransaction> getQueuedTransactions()
	{
		return new CopyOnWriteArrayList<>(queuedTransactions);
	}

	/**
	 * Get a copied copy of the queued transactions list to prevent mutations
	 */
	public CopyOnWriteArrayList<TwitchTransaction> getArchivedTransactions()
	{
		return new CopyOnWriteArrayList<>(archivedTransactions);
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

		try {
			JsonArray rawStreamerProducts = segmentContent.getAsJsonArray(TwitchStateEntry.STREAMER_PRODUCTS.getKey());

			if (rawStreamerProducts == null) {
				return;
			}

			CopyOnWriteArrayList<StreamerProduct> newStreamerProducts = new CopyOnWriteArrayList<>();

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
		} catch (Exception exception) {
			log.warn("Could not parse the raw streamer products to a valid set of products:", exception);
		}
	}

	/**
	 * Update the available effects and their configuration from the Twitch EBS.
	 */
	public void updateEbsProducts()
	{

		// guard: skip when already fetching
		if (isFetchingEbsProducts)
		{
			return;
		}

		// guard: skip updating the EBS products when there are no streamer products found
		// this prevents requests to be made by streamers who have not configured the marketplace
		// NOTE: we do allow an initial fetch to get an initial set of EBS products.
		if (streamerProducts.size() <= 0 && ebsProducts.size() > 0)
		{
			return;
		}

		try {
			isFetchingEbsProducts = true;
			Response response = twitchApi.getEbsProducts();
			isFetchingEbsProducts = false;
			JsonObject result = (new JsonParser()).parse(response.body().string()).getAsJsonObject();
			boolean status = result.get("status").getAsBoolean();
			String message = result.get("message").getAsString();
			JsonArray products = result.getAsJsonArray("products");

			// guard: check if the status is valid
			// if not we want to keep the old products intact
			if (!status)
			{
				log.warn("Could not fetch EBS products from Twitch as the status is invalid with message: "+ message);
				return;
			}

			CopyOnWriteArrayList<EbsProduct> newEbsProducts = new CopyOnWriteArrayList<>();

			// try-catch for every parse, to not let all products crash on one misconfiguration
			products.forEach((element) -> {
				try {
					EbsProduct ebsProduct = new Gson().fromJson(element, EbsProduct.class);
					newEbsProducts.add(ebsProduct);
				} catch (Exception exception) {
					// empty?
				}
			});

			ebsProducts = newEbsProducts;
		} catch (Exception exception) {
			log.warn("Could not fetch the new EBS products due to the following error: ", exception);
		}

		// always set to false when there is an error
		isFetchingEbsProducts = false;
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

		transmogManager.onPlayerChanged(playerChanged);
		animationManager.onPlayerChanged(playerChanged);
	}

	/**
	 * Handle game state changes to respawn all objects, because they are cleared
	 * when a new scene is being loaded.
	 */
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		spawnManager.onGameStateChanged(gameStateChanged);
		animationManager.onGameStateChanged(gameStateChanged);
		transmogManager.onGameStateChanged(gameStateChanged);
	}

	/**
	 * Handle game ticks
	 */
	public void onGameTick()
	{
		notificationManager.onGameTick();
		menuManager.onGameTick();
		widgetManager.onGameTick();
		transmogManager.onGameTick();
		animationManager.onGameTick();
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
		handleActiveProducts(MarketplaceProduct::onClientTick);

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
		Iterator<StreamerProduct> iterator = streamerProducts.iterator();

		while(iterator.hasNext())
		{
			StreamerProduct candidateStreamerProduct = iterator.next();

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
		Iterator<EbsProduct> iterator = ebsProducts.iterator();

		while(iterator.hasNext())
		{
			EbsProduct candidateEbsProduct = iterator.next();

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
		handleActiveProducts(MarketplaceProduct::stop);
	}

	/**
	 * Pause all products
	 */
	public void pauseActiveProducts()
	{
		handleActiveProducts(MarketplaceProduct::pause);
		isActive = false;
	}

	/**
	 * Start all products
	 */
	public void playActiveProducts()
	{
		isActive = true;
		handleActiveProducts(MarketplaceProduct::play);

		// re-apply them manually because they are event based and the active flag
		// is not checked periodically TODO: consider doing this periodically for future side-effects
		transmogManager.applyActiveEffects();
		animationManager.applyActiveEffects();
	}

	/**
	 * Handle all active products using an iterator
	 */
	public void handleActiveProducts(LambdaIterator.ValueHandler<MarketplaceProduct> handler)
	{
		LambdaIterator.handleAll(activeProducts, handler);
	}

	/**
	 * Handle plugin shutdown / marketplace disable
	 */
	public void shutDown()
	{
		animationManager.forceCleanAllEffects();
		transmogManager.forceCleanAllEffects();
		menuManager.forceCleanAllEffects();
		widgetManager.forceCleanAllEffects();
		widgetManager.hideCoveringOverlays();
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

	public interface PlayerHandler {
		public void execute(Player player);
	}
}
