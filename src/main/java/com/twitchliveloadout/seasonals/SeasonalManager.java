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
import java.util.Collection;
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
    private final CopyOnWriteArrayList<SeasonalItem> activeMasteries = new CopyOnWriteArrayList<>();
    private final HashMap<Integer, SeasonalItem> areaHighlightLookup = new HashMap<>();
    private final static int INACTIVE_RELIC_TEXT_COLOR = 0xaaaaaa;
    private final static int INACTIVE_MASTERY_OPACITY = 255;
    private final static int RELIC_SCRIPT_ID = 3189;
    private final static int AREA_SCRIPT_ID = 3658;
    private final static int MASTERY_SCRIPT_ID = 3186; // FIND
    private final static int AREA_HIGHLIGHT_GROUP_ID = 512;
    private final static int AREA_HIGHLIGHT_CHILD_ID = 18;
    private final static int RELIC_GROUP_ID = 655;
    private final static int RELIC_TEXT_CHILD_ID = 21;
    private final static int RELIC_ICON_CHILD_ID = 19;
    private final static int MASTERY_GROUP_ID = 311;
    private final static int MASTERY_OPACITY_CHILD_ID = 32;
    private final static int MASTERY_ICON_CHILD_ID = 33;
    private final static int TOTALS_GROUP_ID = 656;
    private final static int TOTAL_POINTS_CHILD_ID = 28;
    private final static int TOTAL_TASKS_CHILD_ID = 26;

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
        areaHighlightLookup.put(5870, new SeasonalItem("Kandarin", 5907));
        areaHighlightLookup.put(5866, new SeasonalItem("Misthalin", 5901));
        areaHighlightLookup.put(5867, new SeasonalItem("Karamja", 5902));
        areaHighlightLookup.put(5871, new SeasonalItem("Desert", 5904));
        areaHighlightLookup.put(5875, new SeasonalItem("Kourend", 5910));
        areaHighlightLookup.put(5868, new SeasonalItem("Wilderness", 5906));
        areaHighlightLookup.put(5869, new SeasonalItem("Asgarnia", 5903));
        areaHighlightLookup.put(5872, new SeasonalItem("Fremennik", 5908));
        areaHighlightLookup.put(5873, new SeasonalItem("Tirannwn", 5909));
        areaHighlightLookup.put(5874, new SeasonalItem("Morytania", 5905));
        areaHighlightLookup.put(5876, new SeasonalItem("Varlamore", 5911));
    }

    public void onAccountChanged()
    {
        activeRelics.clear();
        activeAreas.clear();
        activeMasteries.clear();

        loadRelics();
        loadAreas();
        loadMasteries();
    }

//    ArrayList<Integer> seenScriptIds = new ArrayList<>();

    public void onScriptPostFired(ScriptPostFired scriptPostFired)
    {
        int scriptId = scriptPostFired.getScriptId();

//        if (!seenScriptIds.contains(scriptId))
//        {
//            seenScriptIds.add(scriptId);
//            System.out.println("NEW SCRIPT ID FOUND: "+ scriptId);
//        }

        switch (scriptId) {
            case RELIC_SCRIPT_ID:
                plugin.runOnClientThread(this::gatherRelics);
                break;
            case AREA_SCRIPT_ID:
                plugin.runOnClientThread(this::gatherAreas);
                break;
            case MASTERY_SCRIPT_ID:
                plugin.scheduleOnClientThread(this::gatherMasteries, 1000);
                break;
        }
    }

    private void gatherMasteries()
    {
        Widget masteryOpacityContainerWidget = client.getWidget(MASTERY_GROUP_ID, MASTERY_OPACITY_CHILD_ID);
        Widget masteryIconContainerWidget = client.getWidget(MASTERY_GROUP_ID, MASTERY_ICON_CHILD_ID);

        if (masteryOpacityContainerWidget == null || masteryIconContainerWidget == null)
        {
            return;
        }

        Widget[] opacityWidgets = masteryOpacityContainerWidget.getDynamicChildren();
        Widget[] iconWidgets = masteryIconContainerWidget.getDynamicChildren();

        // keep track of which style is in which tier, because we cannot extract the combat style names or any other titles
        HashMap<Integer, Integer> styleCounters = new HashMap<>();

        // keep track of only the last tiers as seasonal items for each style
        HashMap<Integer, SeasonalItem> lastTiersPerStyle = new HashMap<>();

        for (int masteryIndex = 0; masteryIndex < opacityWidgets.length; masteryIndex++)
        {
            Widget opacityWidget = opacityWidgets[masteryIndex];
            Widget iconWidget = iconWidgets[masteryIndex];
            int opacity = opacityWidget.getOpacity();
            int spriteId = iconWidget.getSpriteId();
            int stylePositionY = iconWidget.getOriginalY();
            boolean isActive = opacity != INACTIVE_MASTERY_OPACITY;

            // initialize the counter if needed
            // NOTE: set to 0 to make sure we start with 1 in the titles
            if (!styleCounters.containsKey(stylePositionY)) {
                styleCounters.put(stylePositionY, 0);
            }

            int currentStyleCounter = styleCounters.get(stylePositionY);
            int newStyleCounter = currentStyleCounter + 1;

            // always increase the style counter regardless of whether its active
            styleCounters.put(stylePositionY, newStyleCounter);

            if (!isActive)
            {
                continue;
            }

            SeasonalItem activeMastery = new SeasonalItem("Tier #"+ newStyleCounter, spriteId);
            lastTiersPerStyle.put(stylePositionY, activeMastery);
        }

        Collection<SeasonalItem> newActiveMasteries = lastTiersPerStyle.values();
        activeMasteries.clear();
        activeMasteries.addAll(newActiveMasteries);

        saveMasteries();
        updateSeasonalItems();
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
            int tierPositionX = textWidget.getOriginalX();
            boolean foundInactiveInTier = false;
            boolean isActive = textColor != INACTIVE_RELIC_TEXT_COLOR;

            // check for the edge case where all relics in a specified tier have the 'active' text colour
            // this happens when artistically the text colours are set to the active one when the tier is not unlocked
            // in this case we'll force this whole tier to not have active relics
            // we detect the tier based on the x position of the text box
            for (Widget similarRelicTextWidget : textWidgets)
            {
                int similarRelicPositionX = similarRelicTextWidget.getOriginalX();
                int similarRelicTextColor = similarRelicTextWidget.getTextColor();
                boolean isSimilarTier = tierPositionX == similarRelicPositionX;
                boolean isSimilarRelicActive = similarRelicTextColor != INACTIVE_RELIC_TEXT_COLOR;

                // guard: skip when not in same tier
                if (!isSimilarTier)
                {
                    continue;
                }

                if (!isSimilarRelicActive)
                {
                    foundInactiveInTier = true;
                }
            }

            // guard: when there is no inactive relic in this tier then the tier is not unlocked!
            if (!foundInactiveInTier)
            {
                continue;
            }

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

        if (!activeMasteries.isEmpty())
        {
            seasonalItems.add(new SeasonalItem("Masteries"));
            seasonalItems.addAll(activeMasteries);
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
            updateSeasonalItems();
        });
    }

    private void loadAreas()
    {
        plugin.loadFromConfiguration(SEASONAL_AREAS_CONFIG_KEY, (data) -> {
            ArrayList<SeasonalItem> newActiveAreas = gson.fromJson(data, new TypeToken<ArrayList<SeasonalItem>>(){}.getType());
            activeAreas.clear();
            activeAreas.addAll(newActiveAreas);
            updateSeasonalItems();
        });
    }

    private void loadMasteries()
    {
        plugin.loadFromConfiguration(SEASONAL_MASTERIES_CONFIG_KEY, (data) -> {
            ArrayList<SeasonalItem> newActiveMasteries = gson.fromJson(data, new TypeToken<ArrayList<SeasonalItem>>(){}.getType());
            activeMasteries.clear();
            activeMasteries.addAll(newActiveMasteries);
            updateSeasonalItems();
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
    
    private void saveMasteries()
    {
        plugin.setConfiguration(SEASONAL_MASTERIES_CONFIG_KEY, convertItemsToJson(activeMasteries));
    }

    private JsonElement convertItemsToJson(List<SeasonalItem> seasonalItems)
    {
        return gson.toJsonTree(seasonalItems, new TypeToken<List<SeasonalItem>>() {}.getType());
    }
}
