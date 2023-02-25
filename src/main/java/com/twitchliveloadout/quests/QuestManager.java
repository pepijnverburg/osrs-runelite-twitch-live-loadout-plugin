package com.twitchliveloadout.quests;

import com.google.gson.JsonArray;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

@Slf4j
public class QuestManager {
	private static final int QUEST_LIST_GROUP_ID = 399;
	private static final int QUEST_LIST_CHILD_ID = 7;

	private static final int CATEGORY_QUEST_TEXT_COLOR = 16750623; // free / member / mini quests title
	private static final int INVALID_QUEST_TEXT_COLOR = 0; // recipe for disaster subquests

	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;
	private final Client client;

	public QuestManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client)
	{
		this.plugin = plugin;
		this.twitchState = twitchState;
		this.client = client;
	}

	public void updateQuests()
	{
		Widget questListWidget = client.getWidget(QUEST_LIST_GROUP_ID, QUEST_LIST_CHILD_ID);
		JsonArray questsState = new JsonArray();

		// guard: check if the quest list is valid
		if (questListWidget == null)
		{
			return;
		}

		for (Widget questWidget : questListWidget.getDynamicChildren())
		{
			String text = questWidget.getText();
			int textColor = questWidget.getTextColor();
			JsonArray questEntryState = new JsonArray();

			// guard: skip invalid text colors
			if (textColor == CATEGORY_QUEST_TEXT_COLOR || textColor == INVALID_QUEST_TEXT_COLOR)
			{
				continue;
			}

			// add the color directly because we GZIP it anyways
			// so it will become more efficient in terms of transfer
			// the reason why we do this is because you can customize the text colors
			// in the settings, so it is hard to predict what the status is
			questEntryState.add(text);
			questEntryState.add(textColor);
			questsState.add(questEntryState);
		}

		twitchState.setQuests(questsState);
	}
}
