package net.runelite.client.plugins.twitchstreamer;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.kit.KitType;

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
	 * Constants.
	 */
	private final int KIT_AMOUNT = KitType.values().length;
	private final int SKILL_AMOUNT = Skill.values().length;
	private final int EQUIPMENT_ITEM_ID_OFFSET = 512;

	/**
	 * The injected client API.
	 */
	private Client client;

	/**
	 * Image encoder to send sprite information to Twitch as well.
	 */
	private SpriteEncoder imageEncoder;

	/**
	 * Display name of the player.
	 */
	private String playerName = "";

	/**
	 * Currently equipped gear using the same ordinal indexes.
	 */
	private int[] equipmentIds = new int[KIT_AMOUNT];

	/**
	 * The real item IDS for the equipment.
	 */
	private int[] equipmentItemIds = new int[KIT_AMOUNT];

	/**
	 * Currently equipped gear item icons as Base64 image buffers.
	 * This allows the Twitch extensions to be up to date at any time
	 * with the latest items. No other external assets required.
	 */
	private String[] equipmentSprites = new String[KIT_AMOUNT];

	/**
	 * Currently equipped gear names
	 */
	private String[] equipmentNames = new String[KIT_AMOUNT];

	/**
	 * The weight based on the equipment.
	 */
	private int weight = -1;

	/**
	 * The gained experience per skill using the same ordinal indexes.
	 */
	private int[] skillExperiences = new int[SKILL_AMOUNT];

	/**
	 * Combat level.
	 */
	private int combatLevel = -1;

	/**
	 * All the items stored in the bank
	 * NOTE: not sure how to sync this yet as this will
	 * cause the state to exceed the maximum of 5KB.
	 */
	private ItemContainer bankContainer;

	/**
	 * The last state that was synced to Twitch.
	 * We use this to check for changes.
	 */
	private JsonObject lastState = null;

	/**
	 * Flag to identify whether the state has changed after
	 * one of the setters was invoked. This allow for more
	 * efficient updating towards the Configuration Service.
	 */
	private boolean changed = false;

	/**
	 * Constructor
	 */
	public ConfigurationServiceState(Client client)
	{
		this.client = client;
		this.imageEncoder = new SpriteEncoder(client);
	}

	/**
	 * Map all the states to one JSON object that can be send
	 * to the Twitch API in string form.
	 * @return
	 */
	public JsonObject getState()
	{
		JsonObject state = new JsonObject();
		JsonArray equipmentItemIdsJson = convertToJson(getEquipmentItemIds());
		JsonArray equipmentNamesJson = convertToJson(getEquipmentNames());
		JsonArray equipmentSpritesJson = convertToJson(getEquipmentSprites());
		JsonArray skillExperiencesJson = convertToJson(getSkillExperiences());

		state.addProperty("playerName", getPlayerName());
		state.add("equipmentItemIds", equipmentItemIdsJson);
		state.add("equipmentNames", equipmentNamesJson);
		state.add("equipmentSprites", equipmentSpritesJson);
		state.addProperty("weight", getWeight());
		state.add("skillExperiences", skillExperiencesJson);
		state.addProperty("combatLevel", getCombatLevel());

		return state;
	}

	public JsonArray convertToJson(Object array)
	{
		return new GsonBuilder().create().toJsonTree(array).getAsJsonArray();
	}

	public void setPlayerName(String playerName)
	{

		// guard: check for change
		if (playerName != null && playerName.equals(this.playerName))
		{
			return;
		}

		this.playerName = playerName;
		triggerChange();
	}

	public String getPlayerName()
	{
		return playerName;
	}

	public void setEquipmentIds(int[] equipmentIds)
	{

		// guard: check for change
		if (java.util.Arrays.equals(equipmentIds, this.equipmentIds))
		{
			return;
		}

		// Update the sprites when something is changed.
		// Note that this should be done before the state is mutated.
		updateEquipmentProperties(equipmentIds);

		this.equipmentIds = equipmentIds;
		triggerChange();
	}

	public int[] updateEquipmentProperties(int[] equipmentIds)
	{
		for (int equipmentIndex = 0; equipmentIndex < KIT_AMOUNT; equipmentIndex++)
		{
			int equipmentId = equipmentIds[equipmentIndex];
			int itemId = this.getItemIdByEquipmentId(equipmentId);
			int currentItemId = this.equipmentItemIds[equipmentIndex];
			String itemName = null;
			String itemSprite = null;

			// Guard: check for change in this particular slot
			if (itemId == currentItemId)
			{
				continue;
			}

			ItemComposition item = client.getItemDefinition(itemId);

			// make sure the item is valid
			if (item != null)
			{
				itemName = item.getName();
				itemSprite = imageEncoder.getEncodedSpriteByItemId(itemId);
			}

			this.equipmentItemIds[equipmentIndex] = itemId;
			this.equipmentNames[equipmentIndex] = itemName;
			this.equipmentSprites[equipmentIndex] = itemSprite;
		}

		return this.equipmentItemIds;
	}

	public int[] getEquipmentIds()
	{
		return equipmentIds;
	}

	public int[] getEquipmentItemIds()
	{
		return equipmentItemIds;
	}

	public String[] getEquipmentNames()
	{
		return equipmentNames;
	}

	public String[] getEquipmentSprites()
	{
		return equipmentSprites;
	}

	public void setWeight(int weight)
	{

		// guard: check for change
		if (weight == this.weight) {
			return;
		}

		this.weight = weight;
		triggerChange();
	}

	public int getWeight()
	{
		return weight;
	}

	public void setSkillExperiences(int[] skillExperiences)
	{

		// guard: check for change
		if (java.util.Arrays.equals(skillExperiences, this.skillExperiences)) {
			return;
		}

		this.skillExperiences = skillExperiences;
		triggerChange();
	}

	public int[] getSkillExperiences()
	{
		return skillExperiences;
	}

	public void setCombatLevel(int combatLevel)
	{

		// guard: check for change
		if (combatLevel == this.combatLevel) {
			return;
		}

		this.combatLevel = combatLevel;
		triggerChange();
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}

	/**
	 * Get the item ID by its equipment ID.
	 * @param equipmentId
	 * @return
	 */
	public int getItemIdByEquipmentId(int equipmentId)
	{
		final int itemId = equipmentId - EQUIPMENT_ITEM_ID_OFFSET;

		// guard: check if an item is equipped
		if (itemId < 0) {
			return -1;
		}

		return itemId;
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
	public void triggerChange()
	{
		changed = true;
	}

	/**
	 * Flag the latest change to be passed along to the Twitch API correctly.
	 */
	public void acknowledgeChange()
	{
		changed = false;
	}
}
