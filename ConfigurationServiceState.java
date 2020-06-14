package net.runelite.client.plugins.twitchstreamer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.*;
import net.runelite.client.game.ItemManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

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
 * For each property getters and setters are available to make this plugin
 * easily testable in the future. In the future it is also possible to define
 * properties that are dependent on others and determine them on the fly when
 * calling a getter.
 *
 * The Configuration Service state does have limitations: it can only be a maximum of 5KB.
 *
 * Configuration Service documentation:
 * https://dev.twitch.tv/docs/extensions/reference/#set-extension-configuration-segment
 */
public class ConfigurationServiceState {

	/**
	 * State key entries
	 */
	private enum StateKey {
		PLAYER_NAME("playerName"),
		INVENTORY_ITEMS("inventory"),
		INVENTORY_PRICE("inventoryPrice"),
		EQUIPMENT_ITEMS("equipment"),
		EQUIPMENT_PRICE("equipmentPrice"),
		BANK_ITEMS("bank"),
		BANK_PRICE("bankPrice"),
		SKILL_EXPERIENCES("skillExperiences"),
		BOOSTED_SKILL_LEVELS("boostedSkillLevels"),
		COMBAT_LEVEL("combatLevel"),
		WEIGHT("weight"),
		TOP_POSITION("topPosition");

		private final String key;

		StateKey(String key) {
			this.key = key;
		}

		public String getKey()
		{
			return key;
		}
	}

	private TwitchStreamerConfig config;
	private ItemManager itemManager;

	/**
	 * The current state that is queued to be sent out.
	 */
	private JsonObject currentState = new JsonObject();

	/**
	 * The previous state sent out.
	 */
	private JsonObject previousState = new JsonObject();

	/**
	 * Cache to optimize the various item containers that are synced.
	 * This helps to avoid item syncing done via polling to not require
	 * JSON parsing every time the polling cycle ends.
	 */
	private HashMap<String, Item[]> itemsCache = new HashMap();

	/**
	 * Flag to identify whether the state has changed after
	 * one of the setters was invoked. This allow for more
	 * efficient updating towards the Configuration Service.
	 */
	private boolean changed = false;

	public ConfigurationServiceState(TwitchStreamerConfig config, ItemManager itemManager)
	{
		this.config = config;
		this.itemManager = itemManager;
	}

	public JsonObject getState()
	{
		return currentState;
	}

	public void setPlayerName(String playerName)
	{
		currentState.addProperty(StateKey.PLAYER_NAME.getKey(), playerName);
		checkForChange();
	}

	public void setInventoryItems(Item[] items)
	{
		setItems(StateKey.INVENTORY_ITEMS.getKey(), StateKey.INVENTORY_PRICE.getKey(), items);
	}

	public void setEquipmentItems(Item[] items)
	{
		setItems(StateKey.EQUIPMENT_ITEMS.getKey(), StateKey.EQUIPMENT_PRICE.getKey(), items);
	}

	public void setBankItems(Item[] items)
	{
		setItems(StateKey.BANK_ITEMS.getKey(), StateKey.BANK_PRICE.getKey(), items);
	}

	public void setWeight(Integer weight)
	{
		currentState.addProperty(StateKey.WEIGHT.getKey(), weight);
		checkForChange();
	}

	public void setSkillExperiences(int[] skillExperiences)
	{
		currentState.add(StateKey.SKILL_EXPERIENCES.getKey(), convertToJson(skillExperiences));
		checkForChange();
	}

	public void setBoostedSkillLevels(int[] boostedSkillLevels)
	{
		currentState.add(StateKey.BOOSTED_SKILL_LEVELS.getKey(), convertToJson(boostedSkillLevels));
		checkForChange();
	}

	public void clear()
	{
		currentState = new JsonObject();
		checkForChange();
	}

	public JsonObject getFilteredState()
	{
		JsonObject filteredState = getState().deepCopy();

		// Add positional states
		filteredState.addProperty(StateKey.TOP_POSITION.getKey(), config.overlayTopPosition());

		if (config.syncDisabled())
		{
			filteredState = new JsonObject();
		}

		if (!config.playerInfoEnabled())
		{
			filteredState.add(StateKey.PLAYER_NAME.getKey(), null);
		}

		if (!config.inventoryEnabled())
		{
			filteredState.add(StateKey.INVENTORY_ITEMS.getKey(), null);
			filteredState.add(StateKey.INVENTORY_PRICE.getKey(), null);
		}

		if (!config.equipmentEnabled())
		{
			filteredState.add(StateKey.EQUIPMENT_ITEMS.getKey(), null);
			filteredState.add(StateKey.EQUIPMENT_PRICE.getKey(), null);
		}

		if (!config.bankEnabled())
		{
			filteredState.add(StateKey.BANK_ITEMS.getKey(), null);
			filteredState.add(StateKey.BANK_PRICE.getKey(), null);
		}

		if (!config.skillsEnabled())
		{
			filteredState.add(StateKey.SKILL_EXPERIENCES.getKey(), null);
			filteredState.add(StateKey.BOOSTED_SKILL_LEVELS.getKey(), null);
		}

		if (!config.weightEnabled())
		{
			filteredState.add(StateKey.WEIGHT.getKey(), null);
		}

		return filteredState;
	}

	private void setItems(String itemsKey, String priceKey, Item[] items)
	{
		Item[] cachedItems = itemsCache.get(itemsKey);

		// optimization to not convert all items to JSON
		if (itemArraysEqual(items, cachedItems))
		{
			return;
		}

		// calculate before bank item limit slicing
		final long totalPrice = getTotalPrice(items);

		if (itemsKey == StateKey.BANK_ITEMS.getKey())
		{
			items = getHighestPricedItems(items, config.MAX_BANK_ITEMS);
		}

		itemsCache.put(itemsKey, items);
		currentState.add(itemsKey, convertToJson(items));
		currentState.addProperty(priceKey, totalPrice);
		checkForChange();
	}

	private boolean itemArraysEqual(Item[] currentItems, Item[] newItems)
	{
		if ((currentItems == null && newItems != null) ||
			(currentItems != null && newItems == null) ||
			(currentItems.length != newItems.length))
		{
			return false;
		}

		for (int itemIndex = 0; itemIndex < currentItems.length; itemIndex++) {
			Item currentItem = currentItems[itemIndex];
			Item newItem = newItems[itemIndex];

			if (currentItem.getId() != newItem.getId() || currentItem.getQuantity() != newItem.getQuantity()) {
				return false;
			}
		}

		return true;
	}

	public List<PricedItem> getPricedItems(Item[] items)
	{
		final List<PricedItem> pricedItems = new ArrayList();
		for (Item item : items)
		{
			final int itemId = item.getId();
			final int itemQuantity = item.getQuantity();
			final long itemPrice = ((long) itemManager.getItemPrice(itemId)) * itemQuantity;
			final PricedItem pricedItem = new PricedItem(item, itemPrice);

			pricedItems.add(pricedItem);
		}

		return pricedItems;
	}

	public long getTotalPrice(Item[] items)
	{
		long totalPrice = 0;
		final List<PricedItem> pricedItems = getPricedItems(items);

		for (PricedItem pricedItem : pricedItems)
		{
			totalPrice += pricedItem.getPrice();
		}

		return totalPrice;
	}

	public Item[] getHighestPricedItems(Item[] items, int maxAmount)
	{
		final List<PricedItem> pricedItems = getPricedItems(items);
		Collections.sort(pricedItems);

		final List<PricedItem> highestPricedItems = pricedItems.subList(0, maxAmount);
		final Item[] selectedItems = new Item[highestPricedItems.size()];

		for (int pricedItemIndex = 0; pricedItemIndex < highestPricedItems.size(); pricedItemIndex++) {
			final Item selectedItem = highestPricedItems.get(pricedItemIndex).getItem();
			selectedItems[pricedItemIndex] = selectedItem;
		}

		return selectedItems;
	}

	private JsonArray convertToJson(Item[] items)
	{
		JsonArray itemsJson = new JsonArray();

		if (items == null) {
			return itemsJson;
		}

		for (Item item : items) {
			JsonArray itemJson = new JsonArray();
			itemJson.add(item.getId());
			itemJson.add(item.getQuantity());

			itemsJson.add(itemJson);
		}

		return itemsJson;
	}

	private JsonArray convertToJson(int[] array)
	{
		JsonArray json = new GsonBuilder().create().toJsonTree(array).getAsJsonArray();
		return json;
	}

	public boolean isChanged()
	{
		return changed;
	}

	private boolean checkForChange()
	{
		if (currentState.equals(previousState))
		{
			return false;
		}

		changed = true;
		return true;
	}

	public void forceChange()
	{
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
}
