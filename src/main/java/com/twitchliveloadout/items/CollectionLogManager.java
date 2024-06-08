package com.twitchliveloadout.items;

import com.google.common.collect.ImmutableList;
import com.google.gson.*;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.client.events.NpcLootReceived;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class CollectionLogManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;
	private final Client client;

	private final HashMap<String, Integer> killCounts = new HashMap<>();

	private static final int COLLECTION_LOG_ID = 621;
	private static final int COLLECTION_LOG_ITEM_CONTAINER_ID = 36;
	private static final int COLLECTION_LOG_CATEGORY_ID = 19;
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
	private static final int COLLECTION_LOG_DRAW_LIST_SCRIPT_ID = 2730;
	private static final int[] COLLECTION_LOG_CATEGORY_VARBIT_IDS = {6905, 6906};
	private static final int[] COLLECTION_LOG_TABS = {
		COLLECTION_LOG_BOSSES_TAB,
		COLLECTION_LOG_RAIDS_TAB,
		COLLECTION_LOG_CLUES_TAB,
		COLLECTION_LOG_MINIGAMES_TAB,
		COLLECTION_LOG_OTHER_TAB,
	};
	private ScheduledFuture scheduledUpdateCurrentCategory = null;
	public static final String COUNTERS_KEY_NAME = "c";
	public static final String ITEMS_KEY_NAME = "i";

	public CollectionLogManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client)
	{
		this.plugin = plugin;
		this.twitchState = twitchState;
		this.client = client;
	}

	public void onScriptPostFired(ScriptPostFired scriptPostFired)
	{
		if (scriptPostFired.getScriptId() == COLLECTION_LOG_DRAW_LIST_SCRIPT_ID)
		{
			log.info("ON SCRIPT!!!");
			scheduleUpdateCurrentCategory();
		}
	}

	public void onVarbitChanged(VarbitChanged varbitChanged)
	{
		int varbitId = varbitChanged.getVarbitId();

		for (int candidateVarbitId : COLLECTION_LOG_CATEGORY_VARBIT_IDS)
		{
			if (candidateVarbitId == varbitId)
			{
				log.info("ON VARBIT!!!");
				scheduleUpdateCurrentCategory();
				return;
			}
		}
	}

	public void onNpcLootReceived(NpcLootReceived event)
	{
		//JsonObject collectionLog = twitchState.getCollectionLog();
		// TODO: implement updating of log
	}

	private Widget getCategoryHead()
	{
		Widget categoryHead = client.getWidget(COLLECTION_LOG_ID, COLLECTION_LOG_CATEGORY_ID);

		return categoryHead;
	}

	private String getCategoryTitle(Widget categoryHead)
	{
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
			final Widget tabWidget = client.getWidget(COLLECTION_LOG_ID, tabId);

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
	 * Somehow there is a race condition within the client when reading the kill counts
	 * When inspecting the widgets for the kill counts is possible when they are read that the kill count
	 * from the previous category is concatenated to the name of the new category. So for example in the situation of:
	 * - You have a KC of 1 for Scorpia
	 * - You have a KC of 0 for Scurrius
	 *
	 * When switching the tabs really fast the client can actually return a raw KC string for Scurrius with:
	 * "Scurrius kills: 1"
	 *
	 * This is then split on the ":" and will result in the wrong KC for that boss. This is very strange behaviour,
	 * because you would expect this whole string to be updated as a whole. It however looks like first the name part is updated
	 * and after that the KC part. With the assumption this is a race condition a tiny delay is added below to update the current category.
	 * When testing this has resolved the issue right now.
	 *
	 * This delay does mean we should cancel any other scheduled updates in the past when a new update is requested within the delay time.
	 */
	private void scheduleUpdateCurrentCategory()
	{
		if (scheduledUpdateCurrentCategory != null && !scheduledUpdateCurrentCategory.isDone())
		{
			scheduledUpdateCurrentCategory.cancel(true);
		}

		// NOTE: make sure to add a delay here to fix the race condition mentioned above
		// this race condition can not be fixed within the plugin as it is part of the client
		scheduledUpdateCurrentCategory = plugin.scheduleOnClientThread(
			this::updateCurrentCategory,
			100
		);
	}

	/**
	 * Handle a change of selected category to collect the current items and quantities.
	 */
	private void updateCurrentCategory()
	{
		// debug errors because this method is run on the client thread
		try {
			final CopyOnWriteArrayList<CollectionLogItem> items = getCurrentItems();
			final Widget categoryHead = getCategoryHead();
			final JsonObject counters = getCurrentCounters(categoryHead);
			final String categoryTitle = getCategoryTitle(categoryHead);
			final String tabTitle = getTabTitle();
			JsonObject collectionLog = twitchState.getCollectionLog();

			if (collectionLog == null) {
				collectionLog = new JsonObject();
			}

			if (items == null || categoryTitle == null || tabTitle == null) {
				return;
			}

			if (!collectionLog.has(tabTitle)) {
				collectionLog.add(tabTitle, new JsonObject());
			}

			final JsonObject tabLog = collectionLog.getAsJsonObject(tabTitle);

			// always overwrite with new category log to make sure
			// new data structures in versioning are directly supported
			final JsonObject categoryLog = new JsonObject();
			final JsonArray serializedItems = new JsonArray();

			// serialize all items to arrays to be space efficient like:
			// [[itemId, quantity]] = [[995, 1], [2949, 2], [12304, 1]]
			for (CollectionLogItem item : items) {
				final JsonArray serializedItem = new JsonArray();
				serializedItem.add(item.getId());
				serializedItem.add(item.getQuantity());
				serializedItems.add(serializedItem);
			}

			categoryLog.add(COUNTERS_KEY_NAME, counters);
			categoryLog.add(ITEMS_KEY_NAME, serializedItems);
			tabLog.add(categoryTitle, categoryLog);

//			log.info("-------------------");
//			log.info("CATEGORY TITLE: "+ categoryTitle);
//			log.info("COUNTERS: "+ counters.toString());

			// update the twitch state
			twitchState.setCollectionLog(collectionLog);
		} catch (Exception exception) {
			log.warn("Could not update the collection log due to the following error: ", exception);
		}
	}

	private CopyOnWriteArrayList<CollectionLogItem> getCurrentItems()
	{
		final Widget itemsContainer = client.getWidget(COLLECTION_LOG_ID, COLLECTION_LOG_ITEM_CONTAINER_ID);

		if (itemsContainer == null)
		{
			return null;
		}

		final Widget[] widgetItems = itemsContainer.getDynamicChildren();
		final CopyOnWriteArrayList<CollectionLogItem> items = new CopyOnWriteArrayList<>();

		for (Widget widgetItem : widgetItems)
		{
			items.add(new CollectionLogItem(widgetItem));
		}

		return items;
	}

	private JsonObject getCurrentCounters(Widget categoryHead)
	{
		final JsonObject counters = new JsonObject();

		if (categoryHead == null)
		{
			return counters;
		}

		final Widget[] children = categoryHead.getDynamicChildren();

		if (children == null || children.length < 3)
		{
			return counters;
		}

		// add all counters of all lines in the widget (starting from child index 2)
		for (int childIndex = 2; childIndex < children.length; childIndex++)
		{
			final String rawCounter = children[childIndex].getText();
			final String[] counterPieces = rawCounter.split(": ");

			// guard: make sure this is a KC line
			if (counterPieces.length <= 1)
			{
				continue;
			}

			final String counterTitle = counterPieces[0];
			final String counterAmount = counterPieces[1]
				.split(">")[1]
				.split("<")[0]
				.replace(",", "");

			try {
				counters.addProperty(counterTitle, Integer.parseInt(counterAmount));
			} catch (Exception error) {
				// empty?
			}
		}

		return counters;
	}


	private static final List<Integer> COLLECTION_LOG_TAB_STRUCT_IDS = ImmutableList.of(
			471, // Bosses
			472, // Raids
			473, // Clues
			474, // Minigames
			475  // Other
	);
	private static final int COLLECTION_LOG_KILL_COUNT_SCRIPT_ID = 2735;
	private static final int COLLECTION_LOG_TAB_NAME_PARAM_ID = 682;
	private static final int COLLECTION_LOG_TAB_ENUM_PARAM_ID = 683;
	private static final int COLLECTION_LOG_PAGE_NAME_PARAM_ID = 689;
	private static final int COLLECTION_LOG_PAGE_ITEMS_ENUM_PARAM_ID = 690;

	/**
	 * Init CollectionLog object with all items in the collection log. Does not include quantity or obtained status.
	 * Based off cs2 scripts
	 * <a href="https://github.com/Joshua-F/cs2-scripts/blob/master/scripts/%5Bproc,collection_draw_list%5D.cs2">2731 proc_collection_draw_list</a>
	 * and
	 * <a href="https://github.com/Joshua-F/cs2-scripts/blob/master/scripts/%5Bproc,collection_draw_log%5D.cs2">2732 proc_collection_draw_log</a>
	 * If a user has previously clicked through the collection log with the plugin installed,
	 * obtained and quantity will be set for each item if item exists in local save file.
	 */
	public void updateKillCounts()
	{
		plugin.runOnClientThread(() -> {
			for (Integer structId : COLLECTION_LOG_TAB_STRUCT_IDS) {
				StructComposition tabStruct = client.getStructComposition(structId);
				String tabName = tabStruct.getStringValue(COLLECTION_LOG_TAB_NAME_PARAM_ID);
				int tabEnumId = tabStruct.getIntValue(COLLECTION_LOG_TAB_ENUM_PARAM_ID);
				EnumComposition tabEnum = client.getEnum(tabEnumId);

				for (Integer pageStructId : tabEnum.getIntVals()) {
					StructComposition pageStruct = client.getStructComposition(pageStructId);
					String pageName = pageStruct.getStringValue(COLLECTION_LOG_PAGE_NAME_PARAM_ID);
					int pageItemsEnumId = pageStruct.getIntValue(COLLECTION_LOG_PAGE_ITEMS_ENUM_PARAM_ID);
					EnumComposition pageItemsEnum = client.getEnum(pageItemsEnumId);
					log.info("PAGE NAME: "+ pageName);
					/*
					 * Run script to get available kill count names. Amounts are set in var2048 which isn't set unless
					 * pages are manually opened in-game. Override amounts with 0 or previously saved amounts.
					 *
					 * https://github.com/Joshua-F/cs2-scripts/blob/master/scripts/%5Bproc,collection_category_count%5D.cs2
					 */
					client.runScript(COLLECTION_LOG_KILL_COUNT_SCRIPT_ID, pageStruct.getId());
					List<String> killCountStrings = new ArrayList<>(
							Arrays.asList(Arrays.copyOfRange(client.getStringStack(), 0, 3))
					);
					Collections.reverse(killCountStrings);

					for (String killCountString : killCountStrings)
					{
						if (killCountString.isEmpty())
						{
							continue;
						}

						log.info("KC STRING: "+ killCountString);
					}
				}
			}
		});
	}
}
