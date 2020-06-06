package net.runelite.client.plugins.twitchstreamer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.*;

import java.util.HashMap;

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
		EQUIPMENT_ITEMS("equipment"),
		BANK_ITEMS("bank"),
		SKILL_EXPERIENCES("skillExperiences"),
		BOOSTED_SKILL_LEVELS("boostedSkillLevels"),
		COMBAT_LEVEL("combatLevel"),
		WEIGHT("weight");

		private final String key;

		StateKey(String key) {
			this.key = key;
		}

		public String getKey()
		{
			return key;
		}
	}

	/**
	 * The injected client API.
	 */
	private Client client;

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

	public ConfigurationServiceState(Client client)
	{
		this.client = client;
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
		setItems(StateKey.INVENTORY_ITEMS.getKey(), items);
	}

	public void setEquipmentItems(Item[] items)
	{
		setItems(StateKey.EQUIPMENT_ITEMS.getKey(), items);
	}

	public void setBankItems(Item[] items)
	{
		setItems(StateKey.BANK_ITEMS.getKey(), items);
	}

	private void setItems(String stateKey, Item[] items)
	{
		Item[] cachedItems = itemsCache.get(stateKey);

		// optimization to not convert all items to JSON
		if (itemArraysEqual(items, cachedItems))
		{
			return;
		}

		itemsCache.put(stateKey, items);
		currentState.add(stateKey, convertToJson(items));
		checkForChange();
	}

	public void setWeight(int weight)
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

	public boolean itemArraysEqual(Item[] currentItems, Item[] newItems)
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

	public JsonArray convertToJson(Item[] items)
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

	public JsonArray convertToJson(int[] array)
	{
		JsonArray json = new GsonBuilder().create().toJsonTree(array).getAsJsonArray();
		return json;
	}

	/**
	 * Check whether a change was seen after the last acknowledgement.
	 * @return
	 */
	public boolean isChanged()
	{
		return changed;
	}

	/**
	 * This will flag the current state to be required to be updated
	 * using the Twitch API.
	 */
	private boolean checkForChange()
	{
		if (currentState.equals(previousState))
		{
			return false;
		}

		changed = true;
		return true;
	}

	/**
	 * Flag the latest change to be passed along to the Twitch API correctly.
	 */
	public void acknowledgeChange()
	{
		if (!isChanged()) {
			return;
		}

		previousState = currentState.deepCopy();
		changed = false;
	}
}
