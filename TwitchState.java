package net.runelite.client.plugins.twitchliveloadout;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.*;
import net.runelite.client.game.ItemManager;

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
public class TwitchState {

	private final TwitchLiveLoadoutConfig config;

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

	public TwitchState(TwitchLiveLoadoutConfig config, ItemManager itemManager)
	{
		this.config = config;

		// initialize the states that are not directly synced with events
		setOverlayTopPosition(config.overlayTopPosition());
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
		currentState.add(TwitchStateEntry.SKILL_EXPERIENCES.getKey(), convertToJson(skillExperiences));
		checkForChange();
	}

	public void setBoostedSkillLevels(int[] boostedSkillLevels)
	{
		currentState.add(TwitchStateEntry.BOOSTED_SKILL_LEVELS.getKey(), convertToJson(boostedSkillLevels));
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

	public void setBankItems(Item[] items, long totalPrice, int[] tabAmounts)
	{
		currentState.add(TwitchStateEntry.BANK_TAB_AMOUNTS.getKey(), convertToJson((tabAmounts)));
		setItems(TwitchStateEntry.BANK_ITEMS.getKey(), TwitchStateEntry.BANK_PRICE.getKey(), items, totalPrice);
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

	public JsonObject getFilteredState()
	{
		JsonObject filteredState = getState().deepCopy();

		if (config.syncDisabled())
		{
			filteredState = new JsonObject();
		}

		if (!config.playerInfoEnabled())
		{
			filteredState.add(TwitchStateEntry.PLAYER_NAME.getKey(), null);
		}

		if (!config.inventoryEnabled())
		{
			filteredState.add(TwitchStateEntry.INVENTORY_ITEMS.getKey(), null);
			filteredState.add(TwitchStateEntry.INVENTORY_PRICE.getKey(), null);
		}

		if (!config.equipmentEnabled())
		{
			filteredState.add(TwitchStateEntry.EQUIPMENT_ITEMS.getKey(), null);
			filteredState.add(TwitchStateEntry.EQUIPMENT_PRICE.getKey(), null);
		}

		if (!config.bankEnabled())
		{
			filteredState.add(TwitchStateEntry.BANK_ITEMS.getKey(), null);
			filteredState.add(TwitchStateEntry.BANK_PRICE.getKey(), null);
			filteredState.add(TwitchStateEntry.BANK_TAB_AMOUNTS.getKey(), null);
		}

		if (!config.fightStatisticsEnabled())
		{
			filteredState.add(TwitchStateEntry.FIGHT_STATISTICS.getKey(), null);
		}

		if (!config.itemGoalsEnabled())
		{
			filteredState.add(TwitchStateEntry.ITEM_GOALS.getKey(), null);
		}

		if (!config.skillsEnabled())
		{
			filteredState.add(TwitchStateEntry.SKILL_EXPERIENCES.getKey(), null);
			filteredState.add(TwitchStateEntry.BOOSTED_SKILL_LEVELS.getKey(), null);
		}

		if (!config.weightEnabled())
		{
			filteredState.add(TwitchStateEntry.WEIGHT.getKey(), null);
		}

		return filteredState;
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

		forceChange();
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
