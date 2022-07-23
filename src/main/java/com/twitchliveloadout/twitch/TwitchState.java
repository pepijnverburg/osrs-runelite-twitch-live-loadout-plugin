package com.twitchliveloadout.twitch;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import net.runelite.api.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static com.twitchliveloadout.items.CollectionLogManager.ITEMS_KEY_NAME;

/**
 * In-memory state of all the data that is synced
 * to the Twitch Extension. All data is stored here
 * to allow each data type to be synced at different rates
 * and mechanisms (e.g. polling and event driven).
 *
 * Due to the fact we only have one entry ("broadcaster") to store
 * the data in the Twitch Configuration Service upon update of one
 * data type we need to send all the others as well. For this
 * an in-memory state is useful. Looks like this does not give significant
 * overhead.
 *
 * The Configuration and PubSub Service state do have limitations: it can only be a maximum of 5KB.
 *
 * Configuration Service documentation:
 * https://dev.twitch.tv/docs/extensions/reference/#set-extension-configuration-segment
 */
public class TwitchState {

	private final TwitchLiveLoadoutConfig config;

	/**
	 * Enable this if you want to test the state with all its limits
	 * where we will fill the combat stats, item quantities, etc. with maxed out integers.
	 * This will help us gain insights in how many bank items and combat fights we can allow.
	 */
	public final static boolean STATE_STRESS_TEST_ENABLED = false;
	public final static int MAX_ITEM_QUANTITY = 8000;
	public final static int MAX_FIGHT_STATISTIC_VALUE = 700;
	public final static int MAX_SKILL_EXPERIENCE = 200000000;
	public final static int MAX_SKILL_LEVEL = 126;

	/**
	 * The current state that is queued to be sent out.
	 */
	private JsonObject currentState = new JsonObject();

	/**
	 * The previous state sent out.
	 */
	private JsonObject previousState = new JsonObject();

	/**
	 * An additional cyclic state that cannot be sent out at once
	 * due to Twitch limitations, currently the bank and the collection log
	 * are sent in smaller parts via this state
	 */
	private final static int MAX_BANK_ITEMS_PER_SLICE = 200;
	private final static int MAX_COLLECTION_LOG_ITEMS_PER_SLICE = 200;
	private final static String COLLECTION_LOG_FILTER_SEPARATOR = ",";
	private JsonObject cyclicState = new JsonObject();
	private TwitchStateEntry currentCyclicEntry = TwitchStateEntry.BANK_TABBED_ITEMS;
	private int currentCyclicSliceIndex = 0;

	/**
	 * Flag to identify whether the state has changed after
	 * one of the setters was invoked. This allow for more
	 * efficient updating towards the Configuration Service.
	 */
	private boolean changed = false;

	/**
	 * True when the changed flag can be ignored when pushing state updates
	 * With the current Twitch extension new viewers are expected to get the latest
	 * state at once, because they are not using the Twitch Configuration Service data anymore.
	 * For v0.0.5+ onwards having the flag to true is recommended, let's test with it for now.
	 */
	private final static boolean CONTINUOUS_SYNC = true;
	private final static int CHANGED_DEBOUNCE_TIME = 1000; // ms
	private Instant changedAt;

	public TwitchState(TwitchLiveLoadoutConfig config)
	{
		this.config = config;

		// initialize the states that are not directly synced with events
		setOverlayTopPosition(config.overlayTopPosition());
		setVirtualLevelsEnabled(config.virtualLevelsEnabled());

		// set initial items as no events are triggered when they are empty
		setInventoryItems(new Item[0], 0);
		setEquipmentItems(new Item[0], 0);
		setWeight(0);
	}

	public JsonObject getState()
	{
		return currentState;
	}

	public void setPlayerName(String playerName)
	{
		currentState.addProperty(TwitchStateEntry.PLAYER_NAME.getKey(), playerName);
		checkForChange();
	}

	public void setOverlayTopPosition(int overlayTopPosition)
	{
		currentState.addProperty(TwitchStateEntry.TOP_POSITION.getKey(), overlayTopPosition);
		checkForChange();
	}

	public void setWeight(Integer weight)
	{
		currentState.addProperty(TwitchStateEntry.WEIGHT.getKey(), weight);
		checkForChange();
	}

	public void setSkillExperiences(int[] skillExperiences)
	{
		currentState.add(TwitchStateEntry.SKILL_EXPERIENCES.getKey(), convertToJson(skillExperiences, MAX_SKILL_EXPERIENCE));
		checkForChange();
	}

	public void setBoostedSkillLevels(int[] boostedSkillLevels)
	{
		currentState.add(TwitchStateEntry.BOOSTED_SKILL_LEVELS.getKey(), convertToJson(boostedSkillLevels, MAX_SKILL_LEVEL));
		checkForChange();
	}

	public void setVirtualLevelsEnabled(boolean virtualLevelsEnabled)
	{
		currentState.addProperty(TwitchStateEntry.VIRTUAL_LEVELS_ENABLED.getKey(), virtualLevelsEnabled);
		checkForChange();
	}

	public void setFightStatistics(JsonObject fightStatistics)
	{
		currentState.add(TwitchStateEntry.FIGHT_STATISTICS.getKey(), fightStatistics);
		checkForChange();
	}

	public void setInventoryItems(Item[] items, long totalPrice)
	{
		setItems(TwitchStateEntry.INVENTORY_ITEMS.getKey(), TwitchStateEntry.INVENTORY_PRICE.getKey(), items, totalPrice);
	}

	public void setEquipmentItems(Item[] items, long totalPrice)
	{
		setItems(TwitchStateEntry.EQUIPMENT_ITEMS.getKey(), TwitchStateEntry.EQUIPMENT_PRICE.getKey(), items, totalPrice);
	}

	public void setLootingBagItems(Item[] items, long totalPrice)
	{
		setItems(TwitchStateEntry.LOOTING_BAG_ITEMS.getKey(), TwitchStateEntry.LOOTING_BAG_PRICE.getKey(), items, totalPrice);
	}

	private void setItems(String itemsKey, String priceKey, Item[] items, long totalPrice)
	{
		currentState.add(itemsKey, convertToJson(items));
		currentState.addProperty(priceKey, totalPrice);
		checkForChange();
	}

	public void clear()
	{
		currentState = new JsonObject();
		checkForChange();
	}

	public void setBankItems(Item[] items, long totalPrice, int[] tabAmounts)
	{
		JsonArray tabbedBankItems = new JsonArray();
		int currentItemAmount = 0;

		// convert the client item structure to a nested array with the items
		for (int tabIndex = 0; tabIndex < tabAmounts.length; tabIndex++)
		{
			final int tabAmount = tabAmounts[tabIndex];
			final Item[] tabItems = Arrays.copyOfRange(items, currentItemAmount, currentItemAmount + tabAmount);

			tabbedBankItems.add(convertToJson(tabItems));
			currentItemAmount += tabAmount;
		}

		// get the remaining / zero tab and prepend it
		final Item[] zeroTabItems = Arrays.copyOfRange(items, currentItemAmount, items.length);
		JsonArray prependedTabbedBankItems = new JsonArray();
		prependedTabbedBankItems.add(convertToJson(zeroTabItems));
		prependedTabbedBankItems.addAll(tabbedBankItems);

		cyclicState.add(TwitchStateEntry.BANK_TABBED_ITEMS.getKey(), prependedTabbedBankItems);
		cyclicState.addProperty(TwitchStateEntry.BANK_PRICE.getKey(), totalPrice);
	}

	public void setCollectionLog(JsonObject collectionLog)
	{
		cyclicState.add(TwitchStateEntry.COLLECTION_LOG.getKey(), collectionLog);
	}

	public JsonObject getCollectionLog()
	{
		return cyclicState.getAsJsonObject(TwitchStateEntry.COLLECTION_LOG.getKey());
	}

	public JsonArray getTabbedBankItems()
	{
		return cyclicState.getAsJsonArray(TwitchStateEntry.BANK_TABBED_ITEMS.getKey());
	}

	public JsonObject getFilteredState()
	{
		JsonObject filteredState = getState().deepCopy();
		filteredState = addCyclicState(filteredState);
		filteredState = removeDisabledState(filteredState);

		return filteredState;
	}

	public JsonObject addCyclicState(JsonObject state)
	{

		// add the bank items when in this mode
		if (currentCyclicEntry == TwitchStateEntry.BANK_TABBED_ITEMS)
		{
			final String bankTabbedItemsKey = TwitchStateEntry.BANK_TABBED_ITEMS.getKey();
			final String bankPriceKey = TwitchStateEntry.BANK_PRICE.getKey();

			if (!cyclicState.has(bankTabbedItemsKey) || !cyclicState.has(bankPriceKey))
			{
				return state;
			}

			AtomicInteger currentItemAmount = new AtomicInteger();
			final JsonArray allTabbedBankItems = cyclicState.getAsJsonArray(bankTabbedItemsKey);
			final JsonArray slicedTabbedBankItems = new JsonArray();
			final JsonArray emptyItem = new JsonArray();
			emptyItem.add(-1); // item ID
			emptyItem.add(-1); // item quantity

			// loop all the bank items until we find the item where we need to start syncing
			for (int tabIndex = 0; tabIndex < allTabbedBankItems.size(); tabIndex++)
			{
				JsonArray tabItems = allTabbedBankItems.get(tabIndex).getAsJsonArray();
				JsonArray slicedTabItems = new JsonArray();

				for (int itemIndex = 0; itemIndex < tabItems.size(); itemIndex++)
				{
					final boolean afterSliceStart = currentItemAmount.get() >= currentCyclicSliceIndex;
					final boolean beforeSliceEnd = currentItemAmount.get() < (currentCyclicSliceIndex + MAX_BANK_ITEMS_PER_SLICE);
					currentItemAmount.addAndGet(1);

					// guard: add empty item when we are not in range
					if (!afterSliceStart || !beforeSliceEnd) {
						slicedTabItems.add(emptyItem);
						continue;
					}

					slicedTabItems.add(tabItems.get(itemIndex));
				}

				// add each sliced tab
				slicedTabbedBankItems.add(slicedTabItems);
			}

			state.add(bankTabbedItemsKey, slicedTabbedBankItems);
			state.addProperty(bankPriceKey, cyclicState.get(bankPriceKey).getAsLong());

			return state;
		}

		if (currentCyclicEntry == TwitchStateEntry.COLLECTION_LOG)
		{
			final JsonObject collectionLog = getCollectionLog();
			final JsonObject slicedCollectionLog = new JsonObject();
			AtomicInteger skippedItemAmount = new AtomicInteger();
			AtomicInteger includedItemAmount = new AtomicInteger();

			if (collectionLog == null)
			{
				return state;
			}

			collectionLog.keySet().forEach(tabTitle ->
			{
				JsonObject categories = collectionLog.getAsJsonObject(tabTitle);

				categories.keySet().forEach(categoryTitle ->
				{
					JsonObject category = categories.getAsJsonObject(categoryTitle);
					JsonArray items = category.getAsJsonArray(ITEMS_KEY_NAME);

					if (items == null)
					{
						return;
					}

					// guard: skip any categories that should not be included because of the filter
					if (!shouldIncludeInCollectionLog(tabTitle, categoryTitle))
					{
						return;
					}

					int itemAmount = items.size();

					// guard: check if we already passed the amount of items that were included
					// in the last slice that is synced to the viewers
					if (skippedItemAmount.get() < currentCyclicSliceIndex)
					{
						skippedItemAmount.addAndGet(itemAmount);
						return;
					}

					// guard: check if we exceeded the maximum amount of items
					// TODO: check if overflow in items might cause the max payload to exceed.
					// this can for example happen with large categories, perhaps wise to set the
					// max log items per slice to a fairly safe maximum so this cannot happen?
					if (includedItemAmount.get() > MAX_COLLECTION_LOG_ITEMS_PER_SLICE)
					{
						return;
					}

					// now we can include this category
					includedItemAmount.addAndGet(itemAmount);

					// make sure the tab exists
					if (!slicedCollectionLog.has(tabTitle))
					{
						slicedCollectionLog.add(tabTitle, new JsonObject());
					}

					JsonObject tabLog = slicedCollectionLog.getAsJsonObject(tabTitle);
					tabLog.add(categoryTitle, category);
				});
			});

			state.add(TwitchStateEntry.COLLECTION_LOG.getKey(), slicedCollectionLog);
		}

		return state;
	}

	private boolean shouldIncludeInCollectionLog(String tabTitle, String categoryTitle)
	{
		final String filter = config.collectionLogFilter().trim().toLowerCase();
		final String[] filterPieces = filter.split(COLLECTION_LOG_FILTER_SEPARATOR);
		final String trimmedTabTitle = tabTitle.trim().toLowerCase();
		final String trimmedCategoryTitle = categoryTitle.trim().toLowerCase();

		if (filter.equals(""))
		{
			return true;
		}

		for (final String filterPiece : filterPieces)
		{
			final String trimmedFilterPiece = filterPiece.trim();

			if (trimmedTabTitle.contains((trimmedFilterPiece)) || trimmedCategoryTitle.contains((trimmedFilterPiece)))
			{
				return true;
			}
		}

		return false;
	}

	public void nextCyclicState()
	{

		// guard: after bank items are synced we move to the collection log
		// we cannot sync the bank in one go either so we go through the slices
		if (currentCyclicEntry == TwitchStateEntry.BANK_TABBED_ITEMS)
		{
			final int itemAmount = getBankItemAmount();
			final int newSliceIndex = currentCyclicSliceIndex + MAX_BANK_ITEMS_PER_SLICE;
			currentCyclicSliceIndex = newSliceIndex;

			// if the current slices were already exceeding the current items
			// we can move to syncing the collection log once again
			if (!config.bankEnabled() || currentCyclicSliceIndex >= itemAmount)
			{
				currentCyclicEntry = TwitchStateEntry.COLLECTION_LOG;
				currentCyclicSliceIndex = 0;
			}
		}

		// guard: the collection log is a bit more complex as we cannot sync 1351+ items
		// in one go, for this reason we move across the object category by category
		else if (currentCyclicEntry == TwitchStateEntry.COLLECTION_LOG)
		{
			final int itemAmount = getCollectionLogItemAmount();
			final int newSliceIndex = currentCyclicSliceIndex + MAX_COLLECTION_LOG_ITEMS_PER_SLICE;
			currentCyclicSliceIndex = newSliceIndex;

			// if the current slices were already exceeding the current items
			// we can move to syncing the bank once again
			if (!config.collectionLogEnabled() || currentCyclicSliceIndex >= itemAmount)
			{
				currentCyclicEntry = TwitchStateEntry.BANK_TABBED_ITEMS;
				currentCyclicSliceIndex = 0;
			}
		}
	}

	public boolean hasCyclicState()
	{
		return cyclicState.size() > 0;
	}

	public boolean shouldAlwaysSync()
	{
		return CONTINUOUS_SYNC;
	}

	public JsonObject removeDisabledState(JsonObject state)
	{

		if (!config.syncEnabled())
		{
			state = new JsonObject();

			// set null for all keys to make sure all viewers have their state cleared as well
			for (TwitchStateEntry stateEntry : TwitchStateEntry.values())
			{
				state.add(stateEntry.getKey(), null);
			}
		}

		if (!config.playerInfoEnabled())
		{
			state.add(TwitchStateEntry.PLAYER_NAME.getKey(), null);
		}

		if (!config.inventoryEnabled())
		{
			state.add(TwitchStateEntry.INVENTORY_ITEMS.getKey(), null);
			state.add(TwitchStateEntry.INVENTORY_PRICE.getKey(), null);
		}

		if (!config.equipmentEnabled())
		{
			state.add(TwitchStateEntry.EQUIPMENT_ITEMS.getKey(), null);
			state.add(TwitchStateEntry.EQUIPMENT_PRICE.getKey(), null);
		}

		if (!config.lootingBagEnabled())
		{
			state.add(TwitchStateEntry.LOOTING_BAG_ITEMS.getKey(), null);
			state.add(TwitchStateEntry.LOOTING_BAG_PRICE.getKey(), null);
		}

		if (!config.bankEnabled())
		{
			state.add(TwitchStateEntry.BANK_TABBED_ITEMS.getKey(), null);
			state.add(TwitchStateEntry.BANK_PRICE.getKey(), null);
		}

		if (!config.bankPriceEnabled())
		{
			state.add(TwitchStateEntry.BANK_PRICE.getKey(), null);
		}

		if (!config.collectionLogEnabled())
		{
			state.add(TwitchStateEntry.COLLECTION_LOG.getKey(), null);
		}

		if (!config.fightStatisticsEnabled())
		{
			state.add(TwitchStateEntry.FIGHT_STATISTICS.getKey(), null);
		}

		if (!config.itemGoalsEnabled())
		{
			state.add(TwitchStateEntry.ITEM_GOALS.getKey(), null);
		}

		if (!config.skillsEnabled())
		{
			state.add(TwitchStateEntry.SKILL_EXPERIENCES.getKey(), null);
			state.add(TwitchStateEntry.BOOSTED_SKILL_LEVELS.getKey(), null);
		}

		if (!config.weightEnabled())
		{
			state.add(TwitchStateEntry.WEIGHT.getKey(), null);
		}

		return state;
	}

	private JsonArray convertToJson(Item[] items)
	{
		JsonArray itemsJson = new JsonArray();

		if (items == null) {
			return itemsJson;
		}

		for (Item item : items) {
			JsonArray itemJson = new JsonArray();
			final int id = item.getId();
			int quantity = item.getQuantity();

			if (TwitchState.STATE_STRESS_TEST_ENABLED)
			{
				quantity = (int) (Math.random() * TwitchState.MAX_ITEM_QUANTITY);
			}

			itemJson.add(id);
			itemJson.add(quantity);

			itemsJson.add(itemJson);
		}

		return itemsJson;
	}

	private JsonArray convertToJson(int[] array, int maxValue)
	{
		final int length = array.length;
		final int[] copiedArray = new int[length];

		// Prevent the original array to be mutated with testing mode
		// as it can be an array that is used for rendering.
		System.arraycopy(array, 0, copiedArray, 0, length);

		if (STATE_STRESS_TEST_ENABLED && maxValue > 0) {
			for (int i = 0; i < array.length; i++) {
				copiedArray[i] = (int) (Math.random() * maxValue);
			}
		}

		JsonArray json = new GsonBuilder().create().toJsonTree(copiedArray).getAsJsonArray();
		return json;
	}

	public boolean isChanged()
	{
		return changed;
	}

	public boolean isChangedLongEnough()
	{
		if (!isChanged())
		{
			return false;
		}

		final Instant now = Instant.now();
		final boolean longEnough = now.isAfter(changedAt.plusMillis(CHANGED_DEBOUNCE_TIME));

		return longEnough;
	}

	private boolean checkForChange()
	{
		if (currentState.equals(previousState))
		{
			return false;
		}

		forceChange();
		return true;
	}

	public void forceChange()
	{
		if (isChanged())
		{
			return;
		}

		changedAt = Instant.now();
		changed = true;
	}

	public void acknowledgeChange()
	{
		if (!isChanged()) {
			return;
		}

		previousState = currentState.deepCopy();
		changed = false;
	}

	private int getCollectionLogItemAmount()
	{
		final JsonObject collectionLog = getCollectionLog();
		AtomicInteger amount = new AtomicInteger();

		if (collectionLog == null)
		{
			return amount.get();
		}

		collectionLog.keySet().forEach(tabTitle ->
		{
			JsonObject categories = collectionLog.getAsJsonObject(tabTitle);

			categories.keySet().forEach(categoryTitle ->
			{
				JsonObject category = categories.getAsJsonObject(categoryTitle);
				JsonArray items = category.getAsJsonArray(ITEMS_KEY_NAME);

				if (items == null)
				{
					return;
				}

				// guard: skip any categories that should not be included because of the filter
				if (!shouldIncludeInCollectionLog(tabTitle, categoryTitle))
				{
					return;
				}

				amount.addAndGet(items.size());
			});
		});

		return amount.get();
	}

	private int getBankItemAmount()
	{
		final JsonArray tabbedBankItems = getTabbedBankItems();
		int amount = 0;

		if (tabbedBankItems == null) {
			return amount;
		}

		for (int tabIndex = 0; tabIndex < tabbedBankItems.size(); tabIndex++)
		{
			JsonArray tabItems = tabbedBankItems.get(tabIndex).getAsJsonArray();
			amount += tabItems.size();
		}

		return amount;
	}
}
