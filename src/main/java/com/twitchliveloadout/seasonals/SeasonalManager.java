package com.twitchliveloadout.seasonals;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchState;
import static com.twitchliveloadout.TwitchLiveLoadoutConfig.*;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class SeasonalManager {
    private final TwitchLiveLoadoutPlugin plugin;
    private final TwitchState twitchState;
    private final Client client;
    private final Gson gson;

    private final CopyOnWriteArrayList<SeasonalItem> activeRelics = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SeasonalItem> activeAreas = new CopyOnWriteArrayList<>();
    private final HashMap<Integer, SeasonalItem> areaHighlightLookup = new HashMap<>();
    private final static int INACTIVE_RELIC_TEXT_COLOR = 0xaaaaaa;
    private final static int RELIC_SCRIPT_ID = 3189;
    private final static int AREA_SCRIPT_ID = 3658;
    private final static int AREA_HIGHLIGHT_GROUP_ID = 512;
    private final static int AREA_HIGHLIGHT_CHILD_ID = 19;
    private final static int RELIC_GROUP_ID = 655;
    private final static int RELIC_TEXT_CHILD_ID = 21;
    private final static int RELIC_ICON_CHILD_ID = 19;

    public SeasonalManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client, Gson gson)
    {
        this.plugin = plugin;
        this.twitchState = twitchState;
        this.client = client;
        this.gson = gson;

        initializeAreaHighlightLookup();
        onAccountChanged();
    }

    /**
     * Construct a lookup table where the background area highlight is mapped to the actual region
     * It is unfortunately not possible to dynamically get the names based on the highlight in the background.
     * Therefor a custom lookup is created, which needs to be adjusted for each league.
     */
    public void initializeAreaHighlightLookup()
    {
        areaHighlightLookup.put(5453, new SeasonalItem("Kandarin", 5485));
        areaHighlightLookup.put(5449, new SeasonalItem("Misthalin", 5479));
        areaHighlightLookup.put(5450, new SeasonalItem("Karamja", 5480));
        areaHighlightLookup.put(5454, new SeasonalItem("Desert", 5482));
        areaHighlightLookup.put(5458, new SeasonalItem("Kourend", 5488));
        areaHighlightLookup.put(5451, new SeasonalItem("Wilderness", 5484));
        areaHighlightLookup.put(5452, new SeasonalItem("Asgarnia", 5481));
        areaHighlightLookup.put(5455, new SeasonalItem("Fremennik", 5486));
        areaHighlightLookup.put(5456, new SeasonalItem("Tirannwn", 5487));
        areaHighlightLookup.put(5457, new SeasonalItem("Morytania", 5483));
    }

    public void onAccountChanged()
    {
        activeRelics.clear();
        activeAreas.clear();

        loadRelics();
        loadAreas();
    }

    public void onScriptPostFired(ScriptPostFired scriptPostFired)
    {

        // fixed
//        scriptPostFired.getScriptId(): 2282
//        scriptPostFired.getScriptId(): 6156
//        scriptPostFired.getScriptId(): 6163
//        scriptPostFired.getScriptId(): 6157
//        scriptPostFired.getScriptId(): 3189
//        scriptPostFired.getScriptId(): 733
//

        // resized
//        scriptPostFired.getScriptId(): 1343
//        scriptPostFired.getScriptId(): 3188
//        scriptPostFired.getScriptId(): 2282
//        scriptPostFired.getScriptId(): 3189
//        scriptPostFired.getScriptId(): 733
        if (scriptPostFired.getScriptId() == RELIC_SCRIPT_ID)
        {
            plugin.runOnClientThread(this::gatherRelics);
        }

        // fixed
//        scriptPostFired.getScriptId(): 726
//        scriptPostFired.getScriptId(): 3684
//        scriptPostFired.getScriptId(): 3659
//        scriptPostFired.getScriptId(): 3660
//        scriptPostFired.getScriptId(): 3658
//        scriptPostFired.getScriptId(): 725
        if (scriptPostFired.getScriptId() == AREA_SCRIPT_ID)
        {
            plugin.runOnClientThread(this::gatherAreas);
        }
    }

    private void gatherAreas()
    {
        List<SeasonalItem> newActiveAreas = new ArrayList<>();
        Widget mapWidget = client.getWidget(AREA_HIGHLIGHT_GROUP_ID, AREA_HIGHLIGHT_CHILD_ID);

        if (mapWidget == null)
        {
            return;
        }

        Widget[] areaHighlightWidgets = mapWidget.getStaticChildren();

        for (Widget areaHighlightWidget : areaHighlightWidgets)
        {
            int spriteId = areaHighlightWidget.getSpriteId();
            SeasonalItem area = areaHighlightLookup.get(spriteId);

            if (area == null)
            {
                continue;
            }

            newActiveAreas.add(area);
        }

        activeAreas.clear();
        activeAreas.addAll(newActiveAreas);

        saveAreas();
        updateSeasonalItems();
    }

    private void gatherRelics()
    {
        List<SeasonalItem> newActiveRelics = new ArrayList<>();
        Widget relicTextsWidget = client.getWidget(RELIC_GROUP_ID, RELIC_TEXT_CHILD_ID);
        Widget relicIconsWidget = client.getWidget(RELIC_GROUP_ID, RELIC_ICON_CHILD_ID);

        if (relicTextsWidget == null || relicIconsWidget == null)
        {
            return;
        }

        Widget[] textWidgets = relicTextsWidget.getDynamicChildren();
        Widget[] iconWidgets = relicIconsWidget.getDynamicChildren();

        for (Widget textWidget : textWidgets)
        {
            int relicIndex = textWidget.getIndex();
            Widget spriteWidget = iconWidgets[relicIndex];
            String relicName = textWidget.getText();
            int spriteId = spriteWidget.getSpriteId();
            int textColor = textWidget.getTextColor();
            boolean isActive = textColor != INACTIVE_RELIC_TEXT_COLOR;

            // guard: skip this relic when inactive
            if (!isActive)
            {
                continue;
            }

            SeasonalItem activeRelic = new SeasonalItem(relicName, spriteId);
            newActiveRelics.add(activeRelic);
        }

        activeRelics.clear();
        activeRelics.addAll(newActiveRelics);

        saveRelics();
        updateSeasonalItems();
    }

    private void updateSeasonalItems()
    {

        ArrayList<SeasonalItem> seasonalItems = new ArrayList<>();

        if (!activeRelics.isEmpty())
        {
            seasonalItems.add(new SeasonalItem("Relics"));
            seasonalItems.addAll(activeRelics);
        }

        if (!activeAreas.isEmpty())
        {
            seasonalItems.add(new SeasonalItem("Regions"));
            seasonalItems.addAll(activeAreas);
        }

        JsonElement seasonalItemsElement = convertItemsToJson(seasonalItems);

        if (!seasonalItemsElement.isJsonArray())
        {
            return;
        }

        JsonArray seasonalItemsJson = seasonalItemsElement.getAsJsonArray();
        twitchState.setSeasonalItems(seasonalItemsJson);
    }

    private void loadRelics()
    {
        plugin.loadFromConfiguration(SEASONAL_RELICS_CONFIG_KEY, (data) -> {
            ArrayList<SeasonalItem> newActiveRelics = gson.fromJson(data, new TypeToken<ArrayList<SeasonalItem>>(){}.getType());
            activeRelics.clear();
            activeRelics.addAll(newActiveRelics);
        });
    }

    private void loadAreas()
    {
        plugin.loadFromConfiguration(SEASONAL_AREAS_CONFIG_KEY, (data) -> {
            ArrayList<SeasonalItem> newActiveAreas = gson.fromJson(data, new TypeToken<ArrayList<SeasonalItem>>(){}.getType());
            activeAreas.clear();
            activeAreas.addAll(newActiveAreas);
        });
    }

    private void saveRelics()
    {
        plugin.setConfiguration(SEASONAL_RELICS_CONFIG_KEY, convertItemsToJson(activeRelics));
    }

    private void saveAreas()
    {
        plugin.setConfiguration(SEASONAL_AREAS_CONFIG_KEY, convertItemsToJson(activeAreas));
    }

    private JsonElement convertItemsToJson(List<SeasonalItem> seasonalItems)
    {
        return gson.toJsonTree(seasonalItems, new TypeToken<List<SeasonalItem>>() {}.getType());
    }
}
