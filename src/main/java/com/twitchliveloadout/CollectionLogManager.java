package com.twitchliveloadout;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

import static com.twitchliveloadout.TwitchLiveLoadoutConfig.COLLECTION_LOG_CONFIG_KEY;

@Slf4j
public class CollectionLogManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;
	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;

	private static final boolean DEBUG_WIDGETS = false;
	private static final boolean DEBUG_CURRENT_CATEGORY = false;
	private static final int COLLECTION_LOG_GROUP_ID = 621;
	private static final int COLLECTION_LOG_TITLE = 1;
	private static final int COLLECTION_LOG_BOSSES_TAB = 4;
	private static final int COLLECTION_LOG_RAIDS_TAB = 5;
	private static final int COLLECTION_LOG_CLUES_TAB = 6;
	private static final int COLLECTION_LOG_MINIGAMES_TAB = 7;
	private static final int COLLECTION_LOG_TAB_TEXT_INDEX =3;
	private static final int COLLECTION_LOG_TAB_INACTIVE_COLOR = 16750623;
	private static final int COLLECTION_LOG_TAB_ACTIVE_COLOR = 16754735;
	private static final int COLLECTION_LOG_OTHER_TAB = 8;
	private static final int COLLECTION_LOG_CATEGORY_LIST = 12;
	private static final int COLLECTION_LOG_CATEGORY_HEAD = 19;
	private static final int COLLECTION_LOG_CATEGORY_ITEMS = 35;
	private static final int COLLECTION_LOG_DRAW_LIST_SCRIPT_ID = 2730;
	private static final int COLLECTION_LOG_CATEGORY_VARBIT_INDEX = 2049;
	private static final int[] COLLECTION_LOG_TABS = {
		COLLECTION_LOG_BOSSES_TAB,
		COLLECTION_LOG_RAIDS_TAB,
		COLLECTION_LOG_CLUES_TAB,
		COLLECTION_LOG_MINIGAMES_TAB,
		COLLECTION_LOG_OTHER_TAB,
	};
	public static final String KILL_COUNT_KEY_NAME = "k";
	public static final String ITEMS_KEY_NAME = "i";

	public CollectionLogManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client, ClientThread clientThread, ConfigManager configManager)
	{
		this.plugin = plugin;
		this.twitchState = twitchState;
		this.client = client;
		this.clientThread = clientThread;
		this.configManager = configManager;

		loadCollectionLogCache();
	}

	public void onScriptPostFired(ScriptPostFired scriptPostFired)
	{
		if (scriptPostFired.getScriptId() == COLLECTION_LOG_DRAW_LIST_SCRIPT_ID)
		{
			clientThread.invokeLater(this::updateCurrentCategory);
		}
	}

	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		if (varbitChanged.getIndex() == COLLECTION_LOG_CATEGORY_VARBIT_INDEX)
		{
			clientThread.invokeLater(this::updateCurrentCategory);
		}
	}

	public void onPlayerNameChanged(String playerName)
	{
		loadCollectionLogCache();
	}

	private Widget getCategoryHead()
	{
		Widget categoryHead = client.getWidget(COLLECTION_LOG_GROUP_ID, COLLECTION_LOG_CATEGORY_HEAD);

		return categoryHead;
	}

	private String getCategoryTitle()
	{
		final Widget categoryHead = getCategoryHead();

		if (categoryHead == null)
		{
			return null;
		}

		final Widget[] children = categoryHead.getDynamicChildren();

		if (children == null || children.length <= 0)
		{
			return null;
		}

		String categoryTitle = children[0].getText();

		return categoryTitle;
	}

	/**
	 * Get the tab the current collection log seletion in (bosses / raids / clues / minigames / other)
	 */
	private String getTabTitle()
	{
		for (int tabId : COLLECTION_LOG_TABS)
		{
			final Widget tabWidget = client.getWidget(COLLECTION_LOG_GROUP_ID, tabId);

			if (tabWidget == null)
			{
				continue;
			}

			final Widget[] children = tabWidget.getDynamicChildren();

			if (children == null || COLLECTION_LOG_TAB_TEXT_INDEX >= children.length)
			{
				continue;
			}

			final Widget titleWidget = children[COLLECTION_LOG_TAB_TEXT_INDEX];
			final String title = titleWidget.getText();
			final int color = titleWidget.getTextColor();

			if (color == COLLECTION_LOG_TAB_ACTIVE_COLOR) {
				return title;
			}
		}

		return null;
	}

	/**
	 * Handle a change of selected category to collect the current items and quantities.
	 */
	private void updateCurrentCategory()
	{
		final CollectionLogItem[] items = getCurrentItems();
		final int killCount = getCurrentKillCount();
		final String categoryTitle = getCategoryTitle();
		final String tabTitle = getTabTitle();
		JsonObject collectionLog = twitchState.getCollectionLog();

		if (collectionLog == null)
		{
			collectionLog = new JsonObject();
		}

		if (items == null || categoryTitle == null || tabTitle == null)
		{
			return;
		}

		if (!collectionLog.has(tabTitle))
		{
			collectionLog.add(tabTitle, new JsonObject());
		}

		final JsonObject tabLog = collectionLog.getAsJsonObject(tabTitle);

		// always overwrite with new category log to make sure
		// new data structures in versioning are directly supported
		final JsonObject categoryLog = new JsonObject();
		final JsonArray serializedItems = new JsonArray();

		// serialize all items to arrays to be space efficient like:
		// [[itemId, quantity]] = [[995, 1], [2949, 2], [12304, 1]]
		for (CollectionLogItem item : items)
		{
			final JsonArray serializedItem = new JsonArray();
			serializedItem.add(item.getId());
			serializedItem.add(item.getQuantity());
			serializedItems.add(serializedItem);
		}

		categoryLog.addProperty(KILL_COUNT_KEY_NAME, killCount);
		categoryLog.add(ITEMS_KEY_NAME, serializedItems);
		tabLog.add(categoryTitle, categoryLog);

		if (DEBUG_WIDGETS)
		{
			for (int i = 0; i < 40; i++) {
				log.debug("---------------- Widget with ID: "+ i);
				Widget testWidget = client.getWidget(COLLECTION_LOG_GROUP_ID, i);

				if (testWidget == null)
				{
					continue;
				}
				int j = 0;
				for (Widget text : testWidget.getDynamicChildren()) {
					log.debug("Widget text on index "+ j +": "+ text.getText());
					log.debug("Widget text on index "+ j +": "+ text.getTextColor());
					j++;
				}
			}
		}

		if (DEBUG_CURRENT_CATEGORY)
		{
			log.debug("-------------------");
			log.debug("Category title: "+ categoryTitle);
			log.debug("Tab title: "+ tabTitle);
			log.debug("Kill count: "+ killCount);
			log.debug("Item count: "+ items.length);
			log.debug("New collection log is:");
			log.debug(collectionLog.toString());
		}

		// update the twitch state
		twitchState.setCollectionLog(collectionLog);

		// save to persistent storage
		plugin.setConfiguration(COLLECTION_LOG_CONFIG_KEY, collectionLog);
	}

	private CollectionLogItem[] getCurrentItems()
	{
		final Widget itemsContainer = client.getWidget(COLLECTION_LOG_GROUP_ID, COLLECTION_LOG_CATEGORY_ITEMS);

		if (itemsContainer == null)
		{
			return null;
		}

		final Widget[] widgetItems = itemsContainer.getDynamicChildren();
		final List<CollectionLogItem> items = new ArrayList<>();

		for (Widget widgetItem : widgetItems)
		{
			items.add(new CollectionLogItem(widgetItem));
		}

		return items.toArray(CollectionLogItem[]::new);
	}

	private int getCurrentKillCount()
	{
		final Widget categoryHead = getCategoryHead();

		if (categoryHead == null)
		{
			return -1;
		}

		final Widget[] children = categoryHead.getDynamicChildren();

		if (children == null || children.length < 3)
		{
			return -1;
		}

		int totalKillCount = 0;

		// add all kill counts of all lines in the widget (starting from child index 2)
		for (int childIndex = 2; childIndex < children.length; childIndex++)
		{
			final String rawKillCount = children[childIndex].getText();
			final String[] killCountPieces = rawKillCount.split(": ");

			// guard: make sure this is a KC line
			if (killCountPieces.length <= 1)
			{
				continue;
			}

			final String killCount = killCountPieces[1]
				.split(">")[1]
				.split("<")[0]
				.replace(",", "");

			try {
				totalKillCount += Integer.parseInt(killCount);
			} catch (Exception error) {
				// empty?
			}
		}

		return totalKillCount;
	}

	private void loadCollectionLogCache()
	{
		final String rawCollectionLog = plugin.getConfiguration(COLLECTION_LOG_CONFIG_KEY);
		JsonObject parsedCollectionLog = null;

		try {
			parsedCollectionLog = new JsonParser().parse(rawCollectionLog).getAsJsonObject();
		} catch (Exception error) {
			// no error
			return;
		}

		if (parsedCollectionLog == null)
		{
			return;
		}

		twitchState.setCollectionLog(parsedCollectionLog);
	}
}
