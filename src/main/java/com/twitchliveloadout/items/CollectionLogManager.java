package com.twitchliveloadout.items;

import com.google.gson.*;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.ComponentID;
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

	private static final int COLLECTION_LOG_ID = 621;
	private static final int COLLECTION_LOG_CONTAINER_ID = 1;
	private static final int COLLECTION_LOG_TITLE_INDEX = 1;
	private static final int COLLECTION_LOG_BOSSES_TAB = 4;
	private static final int COLLECTION_LOG_RAIDS_TAB = 5;
	private static final int COLLECTION_LOG_CLUES_TAB = 6;
	private static final int COLLECTION_LOG_MINIGAMES_TAB = 7;
	private static final int COLLECTION_LOG_TAB_TEXT_INDEX = 3;
	private static final int COLLECTION_LOG_TAB_INACTIVE_COLOR = 16750623;
	private static final int COLLECTION_LOG_TAB_ACTIVE_COLOR = 16754735;
	private static final int COLLECTION_LOG_OTHER_TAB = 8;
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
		if (scriptPostFired.getScriptId() == ScriptID.COLLECTION_DRAW_LIST)
		{
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
		Widget categoryHead = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_HEADER);

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
	 * Get the tab the current collection log selection in (bosses / raids / clues / minigames / other)
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
				() -> {
					updateCurrentCategory();
					updateObtainedAmounts();
				},
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

	private void updateObtainedAmounts()
	{
		try {
			Widget containerWidget = client.getWidget(COLLECTION_LOG_ID, COLLECTION_LOG_CONTAINER_ID);
			Widget titleWidget = containerWidget.getChild(COLLECTION_LOG_TITLE_INDEX);
			String title = titleWidget.getText();
			String[] titlePieces = title.split(" - ");

			if (titlePieces.length <= 1)
			{
				return;
			}

			String obtainedAmountFraction = titlePieces[1];
			String[] obtainedAmountPieces = obtainedAmountFraction.split("/");
			Integer obtainedAmount = Integer.parseInt(obtainedAmountPieces[0]);
			Integer obtainableAmount = Integer.parseInt(obtainedAmountPieces[1]);

			twitchState.setCollectionLogAmounts(obtainedAmount, obtainableAmount);
		} catch (Exception exception) {
			// empty
		}
	}

	private CopyOnWriteArrayList<CollectionLogItem> getCurrentItems()
	{
		final Widget itemsContainer = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);

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
}
