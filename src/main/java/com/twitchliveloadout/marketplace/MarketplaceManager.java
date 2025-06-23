package com.twitchliveloadout.marketplace;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.fights.FightStateManager;
import com.twitchliveloadout.marketplace.animations.AnimationManager;
import com.twitchliveloadout.marketplace.draws.DrawManager;
import com.twitchliveloadout.marketplace.interfaces.MenuManager;
import com.twitchliveloadout.marketplace.interfaces.WidgetManager;
import com.twitchliveloadout.marketplace.notifications.NotificationManager;
import com.twitchliveloadout.marketplace.products.*;
import com.twitchliveloadout.marketplace.sounds.SoundManager;
import com.twitchliveloadout.marketplace.spawns.SpawnManager;
import com.twitchliveloadout.marketplace.spawns.SpawnOverheadManager;
import com.twitchliveloadout.marketplace.spawns.SpawnPoint;
import com.twitchliveloadout.marketplace.spawns.SpawnedObject;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.marketplace.transactions.TwitchTransactionOrigin;
import com.twitchliveloadout.marketplace.transactions.TwitchTransactionProductType;
import com.twitchliveloadout.marketplace.transmogs.TransmogManager;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.TwitchSegmentType;
import com.twitchliveloadout.twitch.TwitchState;
import com.twitchliveloadout.twitch.TwitchStateEntry;
import com.twitchliveloadout.twitch.eventsub.TwitchEventSubType;
import com.twitchliveloadout.twitch.eventsub.messages.BaseMessage;
import com.twitchliveloadout.utilities.GameEventType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.geometry.SimplePolygon;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import okhttp3.Response;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.twitchliveloadout.TwitchLiveLoadoutPlugin.IN_DEVELOPMENT;
import static com.twitchliveloadout.marketplace.MarketplaceConstants.EVENT_SUB_DEFAULT_EBS_PRODUCT_ID;

@Slf4j
public class MarketplaceManager {

	@Getter
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchApi twitchApi;
	@Getter
	private final TwitchState twitchState;

	@Getter
	private final Client client;

	@Getter
	private final TwitchLiveLoadoutConfig config;

	@Getter
	private final SpawnManager spawnManager;

	@Getter
	private final SpawnOverheadManager spawnOverheadManager;

	@Getter
	private final AnimationManager animationManager;

	@Getter
	private final TransmogManager transmogManager;

	@Getter
	private final WidgetManager widgetManager;

	@Getter
	private final MenuManager menuManager;

	@Getter
	private final DrawManager drawManager;

	@Getter
	private final NotificationManager notificationManager;

	@Getter
	private final SoundManager soundManager;

	@Getter
	private final Gson gson;

	@Getter
	private final FightStateManager fightStateManager;

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
	 * List of all custom EBS products loaded within RL
	 */
	@Getter
	private CopyOnWriteArrayList<EbsProduct> customEbsProducts = new CopyOnWriteArrayList<>();

	/**
	 * List of all EBS products from Twitch
	 */
	private CopyOnWriteArrayList<ChannelPointReward> channelPointRewards = new CopyOnWriteArrayList<>();

	/**
	 * List of all extension transactions that should be handled
	 */
	private final CopyOnWriteArrayList<TwitchTransaction> queuedTransactions = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<TwitchTransaction> archivedTransactions = new CopyOnWriteArrayList<>();

	private final CopyOnWriteArrayList<String> handledTransactionIds = new CopyOnWriteArrayList<>();
	private String lastTransactionId = null;

	/**
	 * Track several times that should be slower than client ticks but faster than game ticks
	 */
	private final ConcurrentHashMap<String, Instant> timerLastTriggeredAt = new ConcurrentHashMap<>();

	/**
	 * Flag whether any events are allowed to be active
	 */
	@Getter
	private boolean isActive = true;

	/**
	 * Timers for various modes where events can be tested through the Twitch extension
	 */
	private Instant testModeActivatedAt = null;
	public final static int TEST_MODE_EXPIRY_TIME_S = 1 * 60 * 60;
	public final static String TEST_MODE_EXPIRY_TIME_READABLE = "1h";
	private Instant chaosModeActivatedAt = null;
	public final static int CHAOS_MODE_EXPIRY_TIME_S = 8 * 60 * 60;
	public final static String CHAOS_MODE_EXPIRY_TIME_READABLE = "8h";
	private Instant freeModeActivatedAt = null;
	public final static int FREE_MODE_EXPIRY_TIME_S = 8 * 60 * 60;
	public final static String FREE_MODE_EXPIRY_TIME_READABLE = "8h";

	/**
	 * Flag to identify we are already fetching data so requests are not hoarding
	 */
	private boolean isFetchingEbsTransactions = false;
	private boolean isFetchingEbsProducts = false;
	private boolean isFetchingChannelPointRewards = false;
	@Getter
	private boolean fetchingEbsTransactionsErrored = false;

	/**
	 * Lookup to see until when a certain product is cooled down and should stay in the queue if there are any
	 * transactions made at the same time. This lookup also informs the viewers which products are in cooldown.
	 */
	private final ConcurrentHashMap<String, Instant> streamerProductCooldownUntil = new ConcurrentHashMap<>();
	private Instant sharedCooldownUntil;

	/**
	 * Testing variables for the end-to-end testing of marketplace products
	 */
	private int currentTestEbsProductIndex = 0;
	private Instant lastEbsProductTestedAt;

	/**
	 * Several states that are needed for various product effects / conditions
	 */
	@Getter
	@Setter
	private int currentRegionId = 0;

	/**
	 * Patterns within chat messages to trigger random events for
	 */
	private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+)");
	private static final Pattern BOSSKILL_MESSAGE_PATTERN = Pattern.compile("Your (.+) kill count is: <col=ff0000>(\\d+)</col>.");
	private static final ImmutableList<String> PET_MESSAGES = ImmutableList.of(
			"You have a funny feeling like you're being followed",
			"You feel something weird sneaking into your backpack",
			"You have a funny feeling like you would have been followed"
	);

	public MarketplaceManager(TwitchLiveLoadoutPlugin plugin, TwitchApi twitchApi, TwitchState twitchState, Client client, TwitchLiveLoadoutConfig config, ChatMessageManager chatMessageManager, ItemManager itemManager, OverlayManager overlayManager, Gson gson, FightStateManager fightStateManager)
	{
		this.plugin = plugin;
		this.twitchApi = twitchApi;
		this.twitchState = twitchState;
		this.client = client;
		this.config = config;
		this.gson = gson;
		this.fightStateManager = fightStateManager;
		this.spawnManager = new SpawnManager(plugin, client, this);
		this.spawnOverheadManager = new SpawnOverheadManager(client, overlayManager);
		this.animationManager = new AnimationManager(plugin, client);
		this.transmogManager = new TransmogManager(plugin, client, itemManager);
		this.notificationManager = new NotificationManager(plugin, config, chatMessageManager, client, twitchApi, this);
		this.widgetManager = new WidgetManager(plugin, client);
		this.menuManager = new MenuManager(plugin, config, client);
		this.drawManager = new DrawManager(client);
		this.soundManager = new SoundManager(client, config);
	}

	/**
	 * Get new Twitch transactions where the effects should be queued for.
	 */
	public void fetchAsyncNewEbsTransactions()
	{

		// guard: block when already fetching
		if (isFetchingEbsTransactions)
		{
			return;
		}

		// guard: skip updating new transactions when no products are enabled
		// fetching of new transactions in this case is useless anyways because
		// the configurations for them are not available without products
		// NOTE: exception when test mode is active, which allows testing without any active
		if ((streamerProducts.size() <= 0 || ebsProducts.size() <= 0) && !isTestModeActive())
		{
			return;
		}

		try {
			isFetchingEbsTransactions = true;
			twitchApi.fetchAsyncEbsTransactions(lastTransactionId, (Response response) -> {
				isFetchingEbsTransactions = false;
				fetchingEbsTransactionsErrored = false;
				JsonObject result = (new JsonParser()).parse(response.body().string()).getAsJsonObject();
				boolean status = result.get("status").getAsBoolean();
				String message = result.get("message").getAsString();
				JsonArray newTransactionsJson = result.getAsJsonArray("transactions");
				ArrayList<TwitchTransaction> newTransactions = new ArrayList<>();
				final AtomicBoolean updatedLastTransactionId = new AtomicBoolean(false);

				// guard: check if the status is valid
				if (!status)
				{
					plugin.logSupport("Could not fetch EBS transactions from Twitch as the status is invalid with message: "+ message);
					fetchingEbsTransactionsErrored = true;
					return;
				}

				newTransactionsJson.forEach((element) -> {

					// try catch for each individual transaction to not have one invalid transaction
					// cancel all others with the top-level try-catch in this function
					try {
						TwitchTransaction twitchTransaction = gson.fromJson(element, TwitchTransaction.class);
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
						if (handledTransactionIds.contains(transactionId))
						{
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
			}, (exception) -> {
				isFetchingEbsTransactions = false;
				fetchingEbsTransactionsErrored = true;
			});
		} catch (Exception exception) {
			// empty
		}

		// always set to false, also when there is an error
		isFetchingEbsTransactions = false;
	}

	/**
	 * Check for new products that should be applied. This process is a little bit more complex
	 * than you would expect at first, because we need to hook in to the Twitch product configuration and
	 * transactions. From the transaction we can fetch the Twitch product (by SKU). Then we can check
	 * whether the streamer really configured this product to have a specific effect (done in the configuration service).
	 * If yes, we have a Streamer product containing a reference to the Ebs Product, which contains the effect information.
	 * When applying new transactions we will check whether all of these steps are valid to prevent viewers
	 * triggering any effects that were never configured by the streamer.
	 */
	public void handleQueuedTransactions()
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
				String transactionId = transaction.id;
				TwitchProduct twitchProduct = getTwitchProductByTransaction(transaction);
				StreamerProduct streamerProduct = getStreamerProductByTransaction(transaction);

				// guard: make sure a products are exist for this transaction
				if (twitchProduct == null || streamerProduct == null)
				{
					plugin.logSupport("Could not match the transaction product details to a Twitch and Streamer product. Transaction ID: "+ transactionId);
					continue;
				}

				String streamerProductId = streamerProduct.id;
				String ebsProductId = streamerProduct.ebsProductId;
				Instant now = Instant.now();
				Instant cooldownUntil = streamerProductCooldownUntil.get(streamerProductId);
				EbsProduct ebsProduct = getEbsProductById(ebsProductId);
				String productType = transaction.product_type;
				boolean isProductCoolingDown = cooldownUntil != null && now.isBefore(cooldownUntil);
				boolean isSharedCoolingDown = sharedCooldownUntil != null && now.isBefore(sharedCooldownUntil);
				boolean isTestTransaction = productType.equals(TwitchTransactionProductType.TEST.getType());
				boolean isFreeTransaction = productType.equals(TwitchTransactionProductType.FREE.getType());
				boolean isManualTransaction = productType.equals(TwitchTransactionProductType.MANUAL.getType());
				boolean isValidEbsProduct = ebsProduct != null && ebsProduct.enabled && ebsProduct.behaviour != null;
				boolean hasValidAccountType = plugin.getAccountType() != null; // NOTE: can only run on client thread

				// guard: make sure this product is not cooling down
				// this can be the case when two transactions are done at the same time
				if (isProductCoolingDown || isSharedCoolingDown)
				{
					continue;
				}

				// guard: make sure an EBS product is configured for this streamer product
				// we will not remove from the queue because the EBS product might need to be loaded still
				if (!isValidEbsProduct)
				{
					continue;
				}

				// guard: while there is no account type known at the moment skip handling any transactions
				// the check for dangerous account types will come later to actually skip specific effects
				if (!hasValidAccountType)
				{
					continue;
				}

				EbsModelPlacement requiredModelPlacement = ebsProduct.behaviour.requiredModelPlacement;

				// guard: check if at least one spawn point is required for this product to be handled
				// if not then it will stay in the queue until there is a spawn point available
				// this allows support for tight spaces or when many random events are active to not waste
				// any incoming donations
				if (requiredModelPlacement != null)
				{
					SpawnPoint spawnPoint = spawnManager.getSpawnPoint(requiredModelPlacement, null);

					// guard: continue with the queue and don't remove from queue because we will wait for a valid spawn point
					if (spawnPoint == null)
					{
						plugin.logSupport("Skipping transaction because required model placement could not be satisfied: "+ transaction.id);
						continue;
					}
				}

				// keep this info verbose as it is a way of logging to debug any issues that might occur
				// when random events don't trigger and support is required
				log.info("Found a valid transaction that we can start: " + transaction.id);
				log.info("Twitch product SKU: " + streamerProduct.twitchProductSku);
				log.info("Streamer product name: " + streamerProduct.name);
				log.info("Ebs product ID: " + ebsProduct.id);

				// remove the transaction now it is going to be handled
				// we do this after the validation of all products
				// to queue transactions that might receive valid product data later
				queuedTransactions.remove(transaction);

				// guard: check if the version number is supported
				if (ebsProduct.version != MarketplaceConstants.EBS_REQUIRED_PRODUCT_VERSION)
				{
					log.info("Skipping transaction the version number of the EBS product ("+ ebsProduct.version +") is not compatible. Transaction ID: "+ transactionId);
					continue;
				}

				// guard: check for hardcore protection and dangerous random events
				if (ebsProduct.dangerous && !plugin.canPerformDangerousEffects())
				{
					log.error("Skipping transaction because it is deemed dangerous and protection is on, please notify the maintainer: "+ transactionId);
					continue;
				}

				// guard: check for a test transaction while testing mode is not active
				if (isTestTransaction && !isTestModeActive())
				{
					log.warn("Skipping transaction because it is a test transaction while testing is not active: "+ transactionId);
					continue;
				}

				// guard: check for a free transaction while free mode is not active
				if (isFreeTransaction && !isFreeModeActive())
				{
					log.warn("Skipping transaction because it is a free transaction while free mode is not active: "+ transactionId);
					continue;
				}

				// guard: skip manual transactions when not enabled
				if (isManualTransaction && !config.manualMarketplaceProductsEnabled())
				{
					log.warn("Skipping transaction because it is a manual transaction while manual mode is not active: "+ transactionId);
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

					// in case there are system clock differences we can handle some degree of offsets and
					// adjust the timestamp of the transaction to now if it falls within the tolerance
					// this is only an issue when the clock is AHEAD of time
					if (!newProduct.isExpired(-1 * MarketplaceConstants.TRANSACTION_EXPIRY_CLOCK_TOLERANCE_MS)) {
						transaction.timestamp = Instant.now().toString();
						log.info("Transaction falls within the clock tolerance settings and is therefore requeued with a new timestamp: " + transaction.timestamp);
						queuedTransactions.add(transaction);
					}

					log.info("It is skipped, because it has already expired at: "+ newProduct.getExpiredAt());
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
	 * Rerun a transaction in full by removing the ID from the handled list and adjusting the timestamp it is received.
	 */
	public void rerunTransaction(TwitchTransaction transaction)
	{

		// guard: make sure the transaction is valid and not queued yet
		if (transaction == null || queuedTransactions.contains(transaction))
		{
			return;
		}

		String transactionId = transaction.id;

		if (isTransactionActive(transactionId))
		{
			return;
		}

		log.info("A transaction is going to be rerun, transaction ID: "+ transactionId);

		// update the timestamp, so it appears to be a recent transaction
		transaction.timestamp = Instant.now().toString();

		// remove from the handled transactions and queue once again
		handledTransactionIds.remove(transactionId);
		queuedTransactions.add(transaction);
	}

	public boolean isTransactionActive(String transactionId)
	{

		// guard: don't rerun the transaction when it is already active
		for (MarketplaceProduct marketplaceProduct : activeProducts)
		{
			if (marketplaceProduct.getTransaction().id.equals(transactionId))
			{
				return true;
			}
		}

		return false;
	}

	public void handleCustomTransaction(TwitchTransaction transaction)
	{

		// guard: skip when not valid
		if (transaction == null)
		{
			return;
		}

		String transactionId = transaction.id;

		// guard: skip when already handled
		if (handledTransactionIds.contains(transactionId))
		{
			return;
		}

		// add it to the queue and archive
		queuedTransactions.add(transaction);
		handledTransactionIds.add(transactionId);
		archivedTransactions.add(0, transaction);
		updateMarketplacePanel();
	}

	public void handleGameEvent(GameEventType gameEventType)
	{
		String eventId = gameEventType.getId();
		StreamerProduct streamerProduct = getStreamerProductBySku(eventId);
		int activeAndQueuedAmount = countActiveAndQueuedTransactionsByGameEventType(gameEventType);

		// handle the game event for all active products as well
		handleActiveProducts((product) -> {
			product.handleGameEvent(gameEventType);
		});

		// guard: ensure the streamer product is valid
		if (streamerProduct == null)
		{
			return;
		}

		// guard: skip when the maximum transactions are reached for a specific game event
		if (activeAndQueuedAmount >= config.marketplaceMaxActiveProductsPerGameEvent())
		{
			plugin.logSupport("Skipping game event because too many of the same are active. Game event ID: "+ gameEventType.getId());
			return;
		}

		TwitchTransaction transaction = createTransactionFromGameEvent(gameEventType);
		handleCustomTransaction(transaction);
	}

	public TwitchTransaction createTransactionFromGameEvent(GameEventType gameEventType)
	{
		String nowString = Instant.now().toString();
		TwitchTransaction twitchTransaction = new TwitchTransaction();
		TwitchProduct twitchProduct = new TwitchProduct();
		TwitchProductCost twitchProductCost = new TwitchProductCost();

		twitchTransaction.id = generateRandomTestId();
		twitchTransaction.timestamp = nowString;

		twitchProduct.sku = gameEventType.getId();
		twitchProduct.cost = twitchProductCost;
		twitchTransaction.product_data = twitchProduct;
		twitchTransaction.product_type = gameEventType.getId();

		twitchTransaction.handled_at = Instant.now().toString();
		twitchTransaction.origin = TwitchTransactionOrigin.EVENT_SUB;
		twitchTransaction.gameEventType = gameEventType;

		// TODO: consider what should be used here
		twitchTransaction.broadcaster_name = plugin.getPlayerName();
		twitchTransaction.user_name = plugin.getPlayerName();

		return twitchTransaction;
	}

	/**
	 * Cyclic method to cycle through all the available EBS products and test them one by one at a configurable interval
	 */
	public void testNextEbsProduct()
	{
		Instant now = Instant.now();

		// guard: check if enough time has passed for the next product to be tested
		if (lastEbsProductTestedAt != null && lastEbsProductTestedAt.plusSeconds(config.testRandomEventsDelay()).isAfter(now))
		{
			return;
		}

		// guard: skip when there are no products loaded yet
		if (ebsProducts.size() <= 0)
		{
			return;
		}

		// make sure the index is valid
		if (currentTestEbsProductIndex >= ebsProducts.size() || currentTestEbsProductIndex < 0)
		{
			currentTestEbsProductIndex = 0;
		}

		EbsProduct ebsProduct = ebsProducts.get(currentTestEbsProductIndex);

		// check if a random one should be selected rather than selecting it in a cyclic way
		if (config.testRandomEventsRandomly())
		{
			ArrayList<EbsProduct> newEbsProducts = new ArrayList<>();
			newEbsProducts.addAll(ebsProducts);
			ebsProduct = MarketplaceRandomizers.getRandomEntryFromList(newEbsProducts);
		}

		// test this single product
		testEbsProduct(ebsProduct, TwitchTransactionProductType.TEST, TwitchTransactionOrigin.TEST);

		// move to the next products and delay
		currentTestEbsProductIndex += 1;
		lastEbsProductTestedAt = now;
	}

	/**
	 * Test an EBS product by manually creating a fake Twitch donation and queueing it.
	 */
	public void testEbsProduct(EbsProduct ebsProduct, TwitchTransactionProductType productType, TwitchTransactionOrigin origin)
	{
		TwitchTransaction twitchTransaction = new TwitchTransaction();
		TwitchProduct twitchProduct = new TwitchProduct();
		TwitchProductCost twitchProductCost = new TwitchProductCost();
		String transactionId = generateRandomTestId();
		String twitchSku = generateRandomTestId();
		double currencyAmount = 100d;
		String currencyType = "gp";

		twitchProductCost.amount = currencyAmount;
		twitchProductCost.type = currencyType;

		twitchProduct.sku = twitchSku;
		twitchProduct.domain = "test.ext.twitch.tv";
		twitchProduct.cost = twitchProductCost;
		twitchProduct.inDevelopment = true;
		twitchProduct.displayName = currencyAmount +" "+ currencyType;
		twitchProduct.expiration = "";
		twitchProduct.broadcast = true;

		twitchTransaction.id = transactionId;
		twitchTransaction.timestamp = Instant.now().toString();
		twitchTransaction.broadcaster_id = "0";
		twitchTransaction.broadcaster_login = "test-streamer";
		twitchTransaction.broadcaster_name = "Test Streamer";
		twitchTransaction.user_id = "0";
		twitchTransaction.user_login = "test-viewer";
		twitchTransaction.user_name = "Test Viewer";
		twitchTransaction.product_type = productType.getType();
		twitchTransaction.product_data = twitchProduct;
		twitchTransaction.ebs_product_id = ebsProduct.id;
		twitchTransaction.handled_at = Instant.now().toString();
		twitchTransaction.origin = origin;

		queuedTransactions.add(twitchTransaction);
	}

	private String generateRandomTestId()
	{
		return "test_"+ UUID.randomUUID();
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
		int sharedCooldownSeconds = getSharedCooldownS();

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
	 * Get the shared cooldown timer depending on which mode is active.
	 */
	public int getSharedCooldownS()
	{
		int cooldownS = config.marketplaceNormalModeCooldownS();

		if (isFreeModeActive())
		{
			cooldownS += config.marketplaceFreeModeCooldownS();
		}

		if (isChaosModeActive())
		{
			cooldownS += config.marketplaceChaosModeCooldownS();
		}

		return cooldownS;
	}

	/**
	 * Check to clean any existing products that are expired
	 */
	public void cleanExpiredProducts()
	{
		handleActiveProducts((marketplaceProduct) -> {
			boolean skipDangerous = marketplaceProduct.isDangerous() && !plugin.canPerformDangerousEffects();

			// guard: check if the product is not expired yet and is allowed to stay active
			if (!marketplaceProduct.isExpired() && !skipDangerous)
			{
				return;
			}

			marketplaceProduct.stop(false);
			activeProducts.remove(marketplaceProduct);
			updateMarketplacePanel();

			TwitchTransaction transaction = marketplaceProduct.getTransaction();
			String transactionId = marketplaceProduct.getTransaction().id;
			String ebsProductId = marketplaceProduct.getEbsProduct().id;
			int spawnAmount = marketplaceProduct.getSpawnAmount();
			boolean hasRequiredModelPlacement = marketplaceProduct.getEbsProduct().behaviour.requiredModelPlacement != null;

			// guard: check whether nothing has been spawned, but the effect has a spawn requirement
			// there has been unusual cases in very specific areas where this has been the case reported by streamers
			// for reference: https://github.com/pepijnverburg/osrs-runelite-twitch-live-loadout-plugin/issues/143
			// when this happens we will rerun the event.
			// NOTE: disabled because we'll be testing this more first.
//			if (spawnAmount == 0 && hasRequiredModelPlacement)
//			{
//				log.error("Rerunning an expired marketplace product due to not having spawned enough effects (EBS ID: "+ ebsProductId +", spawn amount: "+ spawnAmount +") for transaction: "+ transactionId);
//				rerunTransaction(transaction);
//				return;
//			}

			log.info("Cleaned an expired marketplace product (EBS ID: "+ ebsProductId +", spawn amount: "+ spawnAmount +") for transaction: "+ transactionId);
		});

		// fail-safe to check any spawned objects that are expired, but not properly cleaned up
		spawnManager.handleAllSpawnedObjects((spawnedObject) -> {
			MarketplaceProduct product = spawnedObject.getProduct();
			boolean hasExpiry = spawnedObject.getExpiredAt() != null;

			// guard: check if spawned object is not expired
			// NOTE: when there is no expiry set it is required the product is still active
			if ((hasExpiry && !spawnedObject.isExpired()) || activeProducts.contains(product))
			{
				return;
			}

			// hide and free up the tile
			spawnedObject.hide();
			spawnManager.deregisterSpawnedObjectPlacement(spawnedObject);
		});
	}

	/**
	 * Update the products the streamer has configured in the Twitch Extension.
	 */
	public void updateStreamerProducts()
	{
		JsonObject segmentContent = twitchApi.getConfigurationSegmentContent(TwitchSegmentType.BROADCASTER);
		boolean isFirstLoad = streamerProducts.isEmpty();

		// guard: skip when invalid configuration service content
		if (segmentContent == null)
		{
			return;
		}

		// guard: don't update the streamer products when in testing mode
		if (IN_DEVELOPMENT && config.testRandomEventsEnabled())
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
					StreamerProduct streamerProduct = gson.fromJson(rawStreamerProduct, StreamerProduct.class);
					newStreamerProducts.add(streamerProduct);
				} catch (Exception exception) {
					// empty
				}
			});

			streamerProducts = newStreamerProducts;

			// trigger the several initial game events on the first load of the streamer products that might've been missed
			// due to the game performing the events too fast before the initial load of the products
			if (isFirstLoad) {

				// send 'fake' login event
				if (plugin.isLoggedIn()) {
					handleGameEvent(GameEventType.LOGIN);
				}
			}
		} catch (Exception exception) {
			plugin.logSupport("Could not parse the raw streamer products to a valid set of products:", exception);
		}
	}

	/**
	 * Update the available effects and their configuration from the Twitch EBS.
	 */
	public void updateAsyncEbsProducts()
	{

		// guard: skip when already fetching
		if (isFetchingEbsProducts)
		{
			return;
		}

		// guard: skip updating the EBS products when there are no streamer products found
		// this prevents requests to be made by streamers who have not configured the marketplace
		// NOTE: we do allow an initial fetch to get an initial set of EBS products in case the
		// streamer products are still being fetched
		// NOTE: we also allow when test mode is active when streamers want to preview events
		if ((streamerProducts.size() <= 0 && ebsProducts.size() > 0) && !isTestModeActive())
		{
			return;
		}

		try {
			isFetchingEbsProducts = true;
			twitchApi.fetchAsyncEbsProducts((Response response) -> {
				isFetchingEbsProducts = false;
				JsonObject result = (new JsonParser()).parse(response.body().string()).getAsJsonObject();
				boolean status = result.get("status").getAsBoolean();
				String message = result.get("message").getAsString();
				JsonArray products = result.getAsJsonArray("products");

				// guard: check if the status is valid
				// if not we want to keep the old products intact
				if (!status)
				{
					plugin.logSupport("Could not fetch EBS products from Twitch as the status is invalid with message: "+ message);
					return;
				}

				CopyOnWriteArrayList<EbsProduct> newEbsProducts = new CopyOnWriteArrayList<>();

				// try-catch for every parse, to not let all products crash on one misconfiguration
				products.forEach((product) -> {
					try {
						EbsProduct ebsProduct = gson.fromJson(product, EbsProduct.class);
						newEbsProducts.add(ebsProduct);
					} catch (Exception exception) {
						plugin.logSupport("Could not parse the raw EBS product to a valid product: ", exception);
					}
				});

				ebsProducts = newEbsProducts;
			}, (exception) -> {
				isFetchingEbsProducts = false;
			});
		} catch (Exception exception) {
			plugin.logSupport("Could not fetch the new EBS products due to the following error: ", exception);
		}
	}

	public void updateAsyncChannelPointRewards()
	{

		// guard: skip when already fetching
		if (isFetchingChannelPointRewards)
		{
			return;
		}

		isFetchingChannelPointRewards = true;
		twitchApi.fetchAsyncChannelPointRewards(
			(response) -> {
				isFetchingChannelPointRewards = false;
				JsonObject result = (new JsonParser()).parse(response.body().string()).getAsJsonObject();
				JsonArray rewards = result.getAsJsonArray("data");
				CopyOnWriteArrayList<ChannelPointReward> newChannelPointRewards = new CopyOnWriteArrayList<>();

				// guard: check if the rewards could be fetched
				// NOTE: on empty data we will reset the channel point rewards to make sure 'old' data is not synced up
				if (rewards == null)
				{
					channelPointRewards = newChannelPointRewards;
					plugin.logSupport("Could not find any valid Channel Point Rewards.");
					return;
				}

				// try-catch for every parse, to not let all products crash on one misconfiguration
				rewards.forEach((reward) -> {
					try {
						ChannelPointReward channelPointReward = gson.fromJson(reward, ChannelPointReward.class);

						// guard: skip any rewards that are not enabled
						if (!channelPointReward.is_enabled)
						{
							return;
						}

						newChannelPointRewards.add(channelPointReward);
					} catch (Exception exception) {
						plugin.logSupport("Could not parse the raw Channel Point Reward: ", exception);
					}
				});

				plugin.logSupport("Updated to new channel point rewards with amount: "+ newChannelPointRewards.size());
				channelPointRewards = newChannelPointRewards;
			},
			(error) -> {
				isFetchingChannelPointRewards = false;
			}
		);
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
	 * Get a copy of the streamer products list to prevent mutations
	 */
	public CopyOnWriteArrayList<StreamerProduct> getStreamerProducts()
	{
		return new CopyOnWriteArrayList<>(streamerProducts);
	}

	/**
	 * Get a copy of the queued transactions list to prevent mutations
	 */
	public CopyOnWriteArrayList<TwitchTransaction> getQueuedTransactions()
	{
		return new CopyOnWriteArrayList<>(queuedTransactions);
	}

	/**
	 * Get a copy of the queued transactions list to prevent mutations
	 */
	public CopyOnWriteArrayList<TwitchTransaction> getArchivedTransactions()
	{
		return new CopyOnWriteArrayList<>(archivedTransactions);
	}

	/**
	 * Get a copy of the channel point rewards list to prevent mutations
	 */
	public CopyOnWriteArrayList<ChannelPointReward> getChannelPointRewards()
	{
		return new CopyOnWriteArrayList<>(channelPointRewards);
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
		boolean isNowLoggedIn = gameStateChanged.getGameState() == GameState.LOGGED_IN;

		spawnManager.onGameStateChanged(gameStateChanged);
		animationManager.onGameStateChanged(gameStateChanged);
		transmogManager.onGameStateChanged(gameStateChanged);

		// trigger any products tied to the login game event
		if (isNowLoggedIn)
		{
			handleGameEvent(GameEventType.LOGIN);
		}
	}

	/**
	 * Handle chat messages to identify game events on to trigger random event with
	 */
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM
				&& event.getType() != ChatMessageType.TRADE && event.getType() != ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			return;
		}

		String chatMessage = event.getMessage();
		Matcher bossKillMatcher = BOSSKILL_MESSAGE_PATTERN.matcher(chatMessage);
		Matcher numberMatcher = NUMBER_PATTERN.matcher(Text.removeTags(chatMessage));
		boolean hasPetAcquired = PET_MESSAGES.stream().anyMatch(chatMessage::contains);
		boolean hasNumber = numberMatcher.find();
		boolean hasBarrowsCompletion = hasNumber && chatMessage.startsWith("Your Barrows chest count is");
		boolean hasCoxCompletion = chatMessage.startsWith("Your completed Chambers of Xeric");
		boolean hasTobCompletion = chatMessage.startsWith("Your completed Theatre of Blood");
		boolean hasToaCompletion = chatMessage.startsWith("Your completed Tombs of Amascut");
		boolean hasRaidCompletion = hasNumber && (hasCoxCompletion || hasTobCompletion || hasToaCompletion);
		boolean hasBossKill = bossKillMatcher.matches();

		if (hasBarrowsCompletion || hasRaidCompletion)
		{
			handleGameEvent(GameEventType.RAID_COMPLETION);
		}

		if (hasBossKill)
		{
			handleGameEvent(GameEventType.BOSS_KILL);
		}

		if (hasPetAcquired)
		{
			handleGameEvent(GameEventType.PET_DROP);
		}
	}

	/**
	 * Listen to graphic changes to identify game events, such as level up
	 */
	public void onGraphicChanged(GraphicChanged event)
	{
		Actor actor = event.getActor();
		Player localPlayer = client.getLocalPlayer();

		// guard: only handle for the local player
		if (actor != localPlayer)
		{
			return;
		}

		// level up Fireworks
		if (actor.hasSpotAnim(199)
			|| actor.hasSpotAnim(1388)
			|| actor.hasSpotAnim((1389)))
		{
			handleGameEvent(GameEventType.LEVEL_UP);
		}
	}

	/**
	 * Handle a client tick for all active products for changes
	 * that need to happen really fast and are lightweight.
	 */
	public void onClientTick()
	{
		Instant now = Instant.now();

		// guard: don't do anything when not logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		// custom timer running on client ticks every x ms for more heavy things to be executed
		// this is because the @Schedule is delaying very often and some of the processes in here are time-sensitive
		if (passTimerOnce(MarketplaceTimer.RESPAWNS, now))
		{
			// respawn all spawned objects that require it
			// due to for example the reloading of a scene
			spawnManager.respawnRequested();
		}

		if (passTimerOnce(MarketplaceTimer.RECORD_LOCATION, now))
		{
			// record a history of the player location that we can use
			// when spawning new objects that are relative in some way to the player
			spawnManager.recordPlayerLocation();
		}

		if (passTimerOnce(MarketplaceTimer.DRAWS, now))
		{
			drawManager.updateEffects();
		}

		if (passTimerOnce(MarketplaceTimer.PRODUCT_BEHAVIOURS, now))
		{
			handleActiveProducts((marketplaceProduct) -> {
				marketplaceProduct.handleBehaviour();
			});
		}

		if (passTimerOnce(MarketplaceTimer.PRODUCT_EXPIRED_SPAWNS, now))
		{
			handleActiveProducts((marketplaceProduct) -> {
				marketplaceProduct.cleanExpiredSpawnedObjects();
			});
		}

		if (passTimerOnce(MarketplaceTimer.PRODUCT_SPAWN_ROTATIONS, now))
		{
			handleActiveProducts((marketplaceProduct) -> {
				marketplaceProduct.handleSpawnRotations();
			});
		}
	}

	public void onPostClientTick()
	{

		// widgets need to be updated post client tick to prevent flickering when
		// the widgets are updates by scripts or other external sources
		widgetManager.onPostClientTick();
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
		spawnOverheadManager.onGameTick();
	}

	/**
	 * Handle animation changes
	 */
	public void onAnimationChanged(AnimationChanged event)
	{
		animationManager.onAnimationChanged(event);
	}

	/**
	 * Handle on menu option clicks
	 */
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		menuManager.onMenuOptionClicked(event);
	}

	/**
	 * Add menu options based on which spawned object can be found with additional menu entries
	 */
	public void onMenuOpened(MenuOpened event)
	{
		Point mouseCanvasPoint = client.getMouseCanvasPosition();
		MenuEntry[] currentMenuEntries = client.getMenu().getMenuEntries();
		int firstAvailableMenuEntryIndex = 0;

		for (int menuEntryIndex = 0; menuEntryIndex < currentMenuEntries.length; menuEntryIndex++)
		{
			if (currentMenuEntries[menuEntryIndex].getOption().equals("Cancel"))
			{
				firstAvailableMenuEntryIndex = menuEntryIndex + 1;
				break;
			}
		}

		int finalFirstAvailableMenuEntryIndex = firstAvailableMenuEntryIndex;
		spawnManager.handleAllSpawnedObjects((spawnedObject) -> {
			MarketplaceProduct product = spawnedObject.getProduct();
			ArrayList<EbsMenuEntry> menuEntries = spawnedObject.getModelSet().menuEntries;

			if (menuEntries == null || menuEntries.isEmpty())
			{
				return;
			}

			// only calculate the polygon when there are menu entries
			SimplePolygon polygon = spawnedObject.calculatePolygon();

			// guard: skip when not in poly
			if (!polygon.contains(mouseCanvasPoint.getX(), mouseCanvasPoint.getY()))
			{
				return;
			}

			// add all menu entries in reverse order to make sure they are displayed correctly
			for (EbsMenuEntry menuEntry : menuEntries)
			{
				String option = menuEntry.option;
				String target = menuEntry.target;
				ArrayList<EbsEffect> onClickEffects = menuEntry.onClickEffects;
				String formattedOption = MarketplaceMessages.formatMessage(option, product, null);
				String formattedTarget = MarketplaceMessages.formatMessage(target, product, null);

				// guard: make sure the entry is valid
				if (option == null || target == null || option.isEmpty() || target.isEmpty())
				{
					continue;
				}

				client.getMenu().createMenuEntry(finalFirstAvailableMenuEntryIndex)
					.setOption(formattedOption)
					.setTarget(formattedTarget)
					.onClick((callback) -> {

						// guard: skip triggering the effects when the product is not active anymore
						if (!product.isActive())
						{
							return;
						}

						product.triggerEffects(onClickEffects, spawnedObject, 0);
					})
					.setType(MenuAction.RUNELITE)
					.setParam0(0)
					.setParam1(0)
					.setDeprioritized(true);
			}
		});
	}

	/**
	 * Handle on menu option clicks
	 */
	public boolean shouldDraw(Renderable renderable, boolean drawingUI)
	{
		return drawManager.shouldDraw(renderable, drawingUI);
	}

	private boolean passTimerOnce(MarketplaceTimer timer, Instant now)
	{

		// guard: make sure the timer name is valid
		if (timer == null)
		{
			return false;
		}

		String name = timer.getName();
		int delayMs = timer.getDelayMs();
		Instant lastTriggeredAt = timerLastTriggeredAt.get(name);
		boolean isPassed = lastTriggeredAt == null || now.isAfter(lastTriggeredAt.plusMillis((delayMs)));

		// update the timer when passed
		if (isPassed)
		{
			timerLastTriggeredAt.put(name, now);
		}

		return isPassed;
	}

	public int countActiveAndQueuedTransactionsByGameEventType(GameEventType gameEventType)
	{
		AtomicInteger amount = new AtomicInteger();

		handleActiveProducts((product) -> {
			TwitchTransaction transaction = product.getTransaction();

			if (transaction.gameEventType == gameEventType) {
				amount.getAndIncrement();
			}
		});

		LambdaIterator.handleAll(queuedTransactions, (transaction) -> {
			if (transaction.gameEventType == gameEventType) {
				amount.getAndIncrement();
			}
		});

		return amount.get();
	}

	public StreamerProduct getStreamerProductByTransaction(TwitchTransaction transaction)
	{
		TwitchProduct twitchProduct = getTwitchProductByTransaction(transaction);
		boolean isTestTransaction = transaction.isTestTransaction();
		boolean isManualTransaction = transaction.isManualTransaction();
		boolean isEventSubTransaction = transaction.isEventSubTransaction();
		BaseMessage eventSubMessage = transaction.eventSubMessage;

		// guard: make sure the twitch product is valid
		if (twitchProduct == null)
		{
			return null;
		}

		// if this is a test transaction force the EBS product ID based on what is passed in the transaction
		if (isTestTransaction)
		{
			String ebsProductId = transaction.ebs_product_id;
			EbsProduct ebsProduct = getEbsProductById(ebsProductId);
			StreamerProduct testStreamerProduct = new StreamerProduct();
			testStreamerProduct.id = generateRandomTestId();
			testStreamerProduct.ebsProductId = transaction.ebs_product_id;
			testStreamerProduct.twitchProductSku = generateRandomTestId();
			testStreamerProduct.name = "[PREVIEW] "+ ebsProduct.name;
			testStreamerProduct.duration = config.testRandomEventsDuration();
			testStreamerProduct.cooldown = 0;

			return testStreamerProduct;
		}

		// if this is a manual transaction force the EBS product ID based on what is passed in the transaction
		if (isManualTransaction)
		{
			String ebsProductId = transaction.ebs_product_id;
			EbsProduct ebsProduct = getEbsProductById(ebsProductId);
			Integer ebsProductFixedDurationMs = ebsProduct.fixedDurationMs;
			int duration = (ebsProductFixedDurationMs != null ? (ebsProductFixedDurationMs / 1000) : config.testRandomEventsDuration());
			StreamerProduct manualStreamerProduct = new StreamerProduct();

			manualStreamerProduct.id = generateRandomTestId();
			manualStreamerProduct.ebsProductId = transaction.ebs_product_id;
			manualStreamerProduct.twitchProductSku = generateRandomTestId();
			manualStreamerProduct.name = "[MANUAL] "+ ebsProduct.name;
			manualStreamerProduct.duration = duration;
			manualStreamerProduct.cooldown = 0;

			return manualStreamerProduct;
		}

		String twitchProductSku = twitchProduct.sku;
		StreamerProduct streamerProduct = getStreamerProductBySku(twitchProductSku);

		// create a custom streamer product with a default EBS product only showing some messages
		// only do this when no streamer product is known for this event
		if (streamerProduct == null && isEventSubTransaction)
		{
			TwitchEventSubType eventSubType = transaction.eventSubType;
			boolean isEventSubMessageEnabled = eventSubType.getDefaultMessageEnabledGetter().execute(plugin, config, eventSubMessage);

			// guard: skip when the event sub message is not enabled
			if (!isEventSubMessageEnabled)
			{
				return null;
			}

            StreamerProduct eventSubStreamerProduct = new StreamerProduct();
			eventSubStreamerProduct.id = UUID.randomUUID().toString();
			eventSubStreamerProduct.ebsProductId = EVENT_SUB_DEFAULT_EBS_PRODUCT_ID;
			eventSubStreamerProduct.twitchProductSku = twitchProductSku;
			eventSubStreamerProduct.name = "Channel Event"; //eventSubType.getName();
			eventSubStreamerProduct.cooldown = 0;

			return eventSubStreamerProduct;
		}

		return streamerProduct;
	}

	public TwitchProduct getTwitchProductByTransaction(TwitchTransaction transaction)
	{

		// guard: make sure the transaction is valid
		if (transaction == null)
		{
			return null;
		}

		TwitchProduct twitchProduct = transaction.product_data;

		return twitchProduct;
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
		EbsProduct ebsProduct = getEbsProductById(ebsProductId, ebsProducts);
		EbsProduct customEbsProduct = getEbsProductById(ebsProductId, customEbsProducts);

		// NOTE: prioritize the custom EBS product to allow overriding!
		return customEbsProduct != null ? customEbsProduct : ebsProduct;
	}

	private EbsProduct getEbsProductById(String ebsProductId, CopyOnWriteArrayList<EbsProduct> ebsProductCandidates)
	{
		Iterator<EbsProduct> iterator = ebsProductCandidates.iterator();

		while (iterator.hasNext())
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

	public void addCustomEbsProduct(EbsProduct ebsProduct)
	{
		String newEbsProductId = ebsProduct.id;
		EbsProduct existingCustomEbsProduct = getEbsProductById(newEbsProductId, customEbsProducts);

		// remove the custom EBS product when the ID was already added
		if (existingCustomEbsProduct != null)
		{
			customEbsProducts.remove(existingCustomEbsProduct);
		}

		customEbsProducts.add(ebsProduct);
	}

	private ChannelPointReward getChannelPointRewardById(String channelPointRewardId)
	{
		Iterator<ChannelPointReward> iterator = channelPointRewards.iterator();

		while(iterator.hasNext())
		{
			ChannelPointReward candidateProduct = iterator.next();

			// guard: check if a match is found
			if (channelPointRewardId.equals(candidateProduct.id))
			{
				return candidateProduct;
			}
		}

		return null;
	}

	/**
	 * Force stop all active products
	 */
	public void forceStopActiveProducts()
	{
		handleActiveProducts((product) -> {
			product.stop(true);
		});
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

		// re-apply them manually because they are event based and the active flag is not checked periodically
		// TODO: consider doing this periodically for future side-effects
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

	public void enableTestMode()
	{
		testModeActivatedAt = Instant.now();
	}

	public void disableTestMode()
	{
		testModeActivatedAt = null;
	}

	public boolean isTestModeActive()
	{

		// in development allow testing when config enables it as well
		// this is an easy permanent testing mode setup
		if (IN_DEVELOPMENT && config.testRandomEventsEnabled())
		{
			return true;
		}

		return testModeActivatedAt != null && Instant.now().minusSeconds(TEST_MODE_EXPIRY_TIME_S).isBefore(testModeActivatedAt);
	}

	public void enableChaosMode()
	{
		chaosModeActivatedAt = Instant.now();
	}

	public void disableChaosMode()
	{
		chaosModeActivatedAt = null;
	}

	public boolean isChaosModeActive()
	{
		return chaosModeActivatedAt != null && Instant.now().minusSeconds(CHAOS_MODE_EXPIRY_TIME_S).isBefore(chaosModeActivatedAt);
	}

	public void enableFreeMode()
	{
		freeModeActivatedAt = Instant.now();
	}

	public void disableFreeMode()
	{
		freeModeActivatedAt = null;
	}

	public boolean isFreeModeActive()
	{
		return freeModeActivatedAt != null && Instant.now().minusSeconds(FREE_MODE_EXPIRY_TIME_S).isBefore(freeModeActivatedAt);
	}

	/**
	 * Handle marketplace disable
	 */
	public void disable()
	{
		forceStopActiveProducts();
		animationManager.forceCleanAllEffects();
		transmogManager.forceCleanAllEffects();
		drawManager.forceCleanAllEffects();
		menuManager.forceCleanAllEffects();
		widgetManager.forceCleanAllEffects();
		widgetManager.hideCoveringOverlays();
		notificationManager.forceHideOverheadText();
		spawnOverheadManager.forceCleanAllEffects();
	}

	/**
	 * Handle plugin shutdown
	 */
	public void shutDown()
	{
		disable();
		spawnOverheadManager.removeOverlay();
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
