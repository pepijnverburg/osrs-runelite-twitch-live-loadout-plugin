package com.twitchliveloadout.achievements;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchState;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import net.runelite.client.util.Text;

public class CombatAchievementsManager {
    private static final int ACHIEVEMENTS_SCRIPT_ID = 4817;
    private static final int LIST_GROUP_ID = 715;
    private static final int PROGRESS_BAR_CHILD_ID = 3;
    private static final int PROGRESS_BAR_INDEX = 3;
    private static final int NAME_LIST_CHILD_ID = 10;
    private static final int NPC_LIST_CHILD_ID = 12;
    private static final int TIER_LIST_CHILD_ID = 15;

    private final TwitchLiveLoadoutPlugin plugin;
    private final TwitchState twitchState;
    private final Client client;

    public CombatAchievementsManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client)
    {
        this.plugin = plugin;
        this.twitchState = twitchState;
        this.client = client;
    }

    public void onScriptPostFired(ScriptPostFired scriptPostFired)
    {
        if (scriptPostFired.getScriptId() == ACHIEVEMENTS_SCRIPT_ID) {
            plugin.scheduleOnClientThread(this::updateCombatAchievements, 200);
        }
    }

    public void updateCombatAchievements()
    {
        Widget progressBarWidget = client.getWidget(LIST_GROUP_ID, PROGRESS_BAR_CHILD_ID);
        Widget namesListWidget = client.getWidget(LIST_GROUP_ID, NAME_LIST_CHILD_ID);
        Widget npcsListWidget = client.getWidget(LIST_GROUP_ID, NPC_LIST_CHILD_ID);
        Widget tierListWidget = client.getWidget(LIST_GROUP_ID, TIER_LIST_CHILD_ID);

        // guard: check if the widgets are valid
        if (progressBarWidget == null || namesListWidget == null || npcsListWidget == null || tierListWidget == null)
        {
            return;
        }

        // get the current state to allow expanding of the achievements if the user is opening the tiers one by one for example
        JsonObject combatAchievementsState = twitchState.getCombatAchievements();

        if (combatAchievementsState == null)
        {
            combatAchievementsState = new JsonObject();
        }

        Widget[] nameWidgets = namesListWidget.getDynamicChildren();
        String progressTitle = null;
        Widget progressTextWidget = progressBarWidget.getChild(PROGRESS_BAR_INDEX);

        if (progressTextWidget != null)
        {
            progressTitle = progressTextWidget.getText();
        }

        for (int nameWidgetIndex = 0; nameWidgetIndex < nameWidgets.length; nameWidgetIndex++)
        {
            Widget nameWidget = nameWidgets[nameWidgetIndex];
            Widget npcWidget = npcsListWidget.getChild(nameWidgetIndex);
            Widget tierWidget = tierListWidget.getChild(nameWidgetIndex);
            JsonArray combatAchievementEntry = new JsonArray();

            // guard: skip if all widgets are not found
            if (nameWidget == null || npcWidget == null || tierWidget == null)
            {
                continue;
            }

            String name = nameWidget.getText();
            String formattedNpcName = Text.removeTags(npcWidget.getText()).replaceAll("Monster: ","");

            // create array tuple instead of an object to maximize storage usage
            // add the color directly because we GZIP it anyways
            // so it will become more efficient in terms of transfer
            // the reason why we do this is, because colours might change in the future
            combatAchievementEntry.add(nameWidget.getTextColor()); // indicates completion status
            combatAchievementEntry.add(formattedNpcName); // name of the monster
            combatAchievementEntry.add(tierWidget.getSpriteId()); // sprite ID of the tier

            // the name of the achievement is the key to allow for overwriting and expanding
            // when a user is opening the tiers one by one
            combatAchievementsState.add(name, combatAchievementEntry);
        }

        twitchState.setCombatAchievementsProgress(progressTitle);
        twitchState.setCombatAchievements(combatAchievementsState);

        // debug in support
        plugin.logSupport("Combat achievement title: "+ progressTitle);
        plugin.logSupport("Combat achievement content: "+ combatAchievementsState.toString());
    }
}
