package com.twitchliveloadout.seasonals;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.LambdaIterator;
import com.twitchliveloadout.twitch.TwitchState;
import static com.twitchliveloadout.TwitchLiveLoadoutConfig.*;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import org.apache.commons.lang3.ArrayUtils;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class SeasonalManager {
    private final TwitchLiveLoadoutPlugin plugin;
    private final TwitchState twitchState;
    private final Client client;
    private final Gson gson;

    private final CopyOnWriteArrayList<SeasonalItem> activeRelics = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<SeasonalItem> activeAreas = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Integer> activeTalents = new CopyOnWriteArrayList<>();
    private final HashMap<Integer, SeasonalItem> areaHighlightLookup = new HashMap<>();
    private final HashMap<Integer, Integer> talentTreeNodeToQueryParamLookup = new HashMap<>();

    private final static int INACTIVE_RELIC_TEXT_COLOR = 0xaaaaaa;
    private final static int RELIC_SCRIPT_ID = 3189;
    private final static int AREA_SCRIPT_ID = 3658;
    private final static int TALENT_SCRIPT_ID = 7892;
    private final static int AREA_HIGHLIGHT_GROUP_ID = 512;
    private final static int AREA_HIGHLIGHT_CHILD_ID = 18;
    private final static int RELIC_GROUP_ID = 655;
    private final static int RELIC_TEXT_CHILD_ID = 21;
    private final static int RELIC_ICON_CHILD_ID = 19;
    private final static int TALENT_TREE_GROUP_ID = 647;
    private final static int TALENT_TREE_CHILD_ID = 18;
    private final static int[] TALENT_TREE_ACTIVE_BACKGROUND_SPRITE_IDS = {7642, 7647, 7652, 7657, 7662, 7667};
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
        initializeTalentTreeLookup();
        onAccountChanged();
    }

    /**
     * Construct a lookup table where the background area highlight is mapped to the actual region
     * It is unfortunately not possible to dynamically get the names based on the highlight in the background.
     * Therefor a custom lookup is created, which needs to be adjusted for each league.
     */
    public void initializeAreaHighlightLookup()
    {
        areaHighlightLookup.put(8064, new SeasonalItem("Kandarin", 5907));
        areaHighlightLookup.put(8060, new SeasonalItem("Misthalin", 5901));
        areaHighlightLookup.put(8061, new SeasonalItem("Karamja", 5902));
        areaHighlightLookup.put(8065, new SeasonalItem("Desert", 5904));
        areaHighlightLookup.put(8069, new SeasonalItem("Kourend", 5910));
        areaHighlightLookup.put(8062, new SeasonalItem("Wilderness", 5906));
        areaHighlightLookup.put(8063, new SeasonalItem("Asgarnia", 5903));
        areaHighlightLookup.put(8066, new SeasonalItem("Fremennik", 5908));
        areaHighlightLookup.put(8067, new SeasonalItem("Tirannwn", 5909));
        areaHighlightLookup.put(8068, new SeasonalItem("Morytania", 5905));
        areaHighlightLookup.put(8070, new SeasonalItem("Varlamore", 5911));
    }

    /**
     * Construct a lookup table where the node indices of the client are mapped to the indices used in the Wiki talent tree builder.
     * With this we can construct a URL that links directly to the build.
     */
    public void initializeTalentTreeLookup()
    {

        // list from 0 until 131 for all the nodes that have different node IDs on the wiki
        talentTreeNodeToQueryParamLookup.put(0, 1); // simple increments for a while...
        talentTreeNodeToQueryParamLookup.put(1, 2);
        talentTreeNodeToQueryParamLookup.put(2, 3);
        talentTreeNodeToQueryParamLookup.put(3, 4);
        talentTreeNodeToQueryParamLookup.put(4, 5);
        talentTreeNodeToQueryParamLookup.put(5, 6);
        talentTreeNodeToQueryParamLookup.put(6, 7);
        talentTreeNodeToQueryParamLookup.put(7, 8);
        talentTreeNodeToQueryParamLookup.put(8, 9);
        talentTreeNodeToQueryParamLookup.put(9, 10);
        talentTreeNodeToQueryParamLookup.put(10, 11);
        talentTreeNodeToQueryParamLookup.put(11, 12);
        talentTreeNodeToQueryParamLookup.put(12, 13);
        talentTreeNodeToQueryParamLookup.put(13, 14);
        talentTreeNodeToQueryParamLookup.put(14, 15);
        talentTreeNodeToQueryParamLookup.put(15, 16);
        talentTreeNodeToQueryParamLookup.put(16, 17);
        talentTreeNodeToQueryParamLookup.put(17, 18);
        talentTreeNodeToQueryParamLookup.put(18, 19);
        talentTreeNodeToQueryParamLookup.put(19, 20);
        talentTreeNodeToQueryParamLookup.put(20, 21);
        talentTreeNodeToQueryParamLookup.put(21, 22);
        talentTreeNodeToQueryParamLookup.put(22, 23);
        talentTreeNodeToQueryParamLookup.put(23, 24);
        talentTreeNodeToQueryParamLookup.put(24, 25);
        talentTreeNodeToQueryParamLookup.put(25, 26);
        talentTreeNodeToQueryParamLookup.put(26, 27);
        talentTreeNodeToQueryParamLookup.put(27, 28);
        talentTreeNodeToQueryParamLookup.put(28, 29);
        talentTreeNodeToQueryParamLookup.put(29, 30);
        talentTreeNodeToQueryParamLookup.put(30, 31);
        talentTreeNodeToQueryParamLookup.put(31, 32);
        talentTreeNodeToQueryParamLookup.put(32, 33);
        talentTreeNodeToQueryParamLookup.put(33, 34);
        talentTreeNodeToQueryParamLookup.put(34, 38); // this is where the pattern breaks
        talentTreeNodeToQueryParamLookup.put(35, 39);
        talentTreeNodeToQueryParamLookup.put(36, 43); // back to increments again?
        talentTreeNodeToQueryParamLookup.put(37, 44);
        talentTreeNodeToQueryParamLookup.put(38, 45);
        talentTreeNodeToQueryParamLookup.put(39, 46);
        talentTreeNodeToQueryParamLookup.put(40, 47);
        talentTreeNodeToQueryParamLookup.put(41, 48);
        talentTreeNodeToQueryParamLookup.put(42, 53); // sort of...
        talentTreeNodeToQueryParamLookup.put(43, 54);
        talentTreeNodeToQueryParamLookup.put(44, 55);
        talentTreeNodeToQueryParamLookup.put(45, 56);
        talentTreeNodeToQueryParamLookup.put(46, 57);
        talentTreeNodeToQueryParamLookup.put(47, 58);
        talentTreeNodeToQueryParamLookup.put(48, 59);
        talentTreeNodeToQueryParamLookup.put(49, 60);
        talentTreeNodeToQueryParamLookup.put(50, 61);
        talentTreeNodeToQueryParamLookup.put(51, 62);
        talentTreeNodeToQueryParamLookup.put(52, 63);
        talentTreeNodeToQueryParamLookup.put(53, 64);
        talentTreeNodeToQueryParamLookup.put(54, 65);
        talentTreeNodeToQueryParamLookup.put(55, 66);
        talentTreeNodeToQueryParamLookup.put(56, 67);
        talentTreeNodeToQueryParamLookup.put(57, 68);
        talentTreeNodeToQueryParamLookup.put(58, 69);
        talentTreeNodeToQueryParamLookup.put(59, 70);
        talentTreeNodeToQueryParamLookup.put(60, 71);
        talentTreeNodeToQueryParamLookup.put(61, 72);
        talentTreeNodeToQueryParamLookup.put(62, 73);
        talentTreeNodeToQueryParamLookup.put(63, 74);
        talentTreeNodeToQueryParamLookup.put(64, 79);
        talentTreeNodeToQueryParamLookup.put(65, 80);
        talentTreeNodeToQueryParamLookup.put(66, 81);
        talentTreeNodeToQueryParamLookup.put(67, 82);
        talentTreeNodeToQueryParamLookup.put(68, 83);
        talentTreeNodeToQueryParamLookup.put(69, 84);
        talentTreeNodeToQueryParamLookup.put(70, 85);
        talentTreeNodeToQueryParamLookup.put(71, 86);
        talentTreeNodeToQueryParamLookup.put(72, 87);
        talentTreeNodeToQueryParamLookup.put(73, 88);
        talentTreeNodeToQueryParamLookup.put(74, 91);
        talentTreeNodeToQueryParamLookup.put(75, 92);
        talentTreeNodeToQueryParamLookup.put(76, 93);
        talentTreeNodeToQueryParamLookup.put(77, 94);
        talentTreeNodeToQueryParamLookup.put(78, 95);
        talentTreeNodeToQueryParamLookup.put(79, 96);
        talentTreeNodeToQueryParamLookup.put(80, 97);
        talentTreeNodeToQueryParamLookup.put(81, 98);
        talentTreeNodeToQueryParamLookup.put(82, 99);
        talentTreeNodeToQueryParamLookup.put(83, 100);
        talentTreeNodeToQueryParamLookup.put(84, 101);
        talentTreeNodeToQueryParamLookup.put(85, 102);
        talentTreeNodeToQueryParamLookup.put(86, 103);
        talentTreeNodeToQueryParamLookup.put(87, 106);
        talentTreeNodeToQueryParamLookup.put(88, 107);
        talentTreeNodeToQueryParamLookup.put(89, 108);
        talentTreeNodeToQueryParamLookup.put(90, 109);
        talentTreeNodeToQueryParamLookup.put(91, 111);
        talentTreeNodeToQueryParamLookup.put(92, 112);
        talentTreeNodeToQueryParamLookup.put(93, 113);
        talentTreeNodeToQueryParamLookup.put(94, 114);
        talentTreeNodeToQueryParamLookup.put(95, 117);
        talentTreeNodeToQueryParamLookup.put(96, 118);
        talentTreeNodeToQueryParamLookup.put(97, 119);
        talentTreeNodeToQueryParamLookup.put(98, 122);
        talentTreeNodeToQueryParamLookup.put(99, 123);
        talentTreeNodeToQueryParamLookup.put(100, 124);
        talentTreeNodeToQueryParamLookup.put(101, 127);
        talentTreeNodeToQueryParamLookup.put(102, 128);
        talentTreeNodeToQueryParamLookup.put(103, 129);
        talentTreeNodeToQueryParamLookup.put(104, 131);
        talentTreeNodeToQueryParamLookup.put(105, 133);
        talentTreeNodeToQueryParamLookup.put(106, 134);
        talentTreeNodeToQueryParamLookup.put(107, 135);
        talentTreeNodeToQueryParamLookup.put(108, 136);
        talentTreeNodeToQueryParamLookup.put(109, 139);
        talentTreeNodeToQueryParamLookup.put(110, 140);
        talentTreeNodeToQueryParamLookup.put(111, 141);
        talentTreeNodeToQueryParamLookup.put(112, 142);
        talentTreeNodeToQueryParamLookup.put(113, 143);
        talentTreeNodeToQueryParamLookup.put(114, 144);
        talentTreeNodeToQueryParamLookup.put(115, 145);
        talentTreeNodeToQueryParamLookup.put(116, 146);
        talentTreeNodeToQueryParamLookup.put(117, 150);
        talentTreeNodeToQueryParamLookup.put(118, 151);
        talentTreeNodeToQueryParamLookup.put(119, 152);
        talentTreeNodeToQueryParamLookup.put(120, 153);
        talentTreeNodeToQueryParamLookup.put(121, 154);
        talentTreeNodeToQueryParamLookup.put(122, 155);
        talentTreeNodeToQueryParamLookup.put(123, 156);
        talentTreeNodeToQueryParamLookup.put(124, 157);
        talentTreeNodeToQueryParamLookup.put(125, 161);
        talentTreeNodeToQueryParamLookup.put(126, 162);
        talentTreeNodeToQueryParamLookup.put(127, 163);
        talentTreeNodeToQueryParamLookup.put(128, 164);
        talentTreeNodeToQueryParamLookup.put(129, 165);
        talentTreeNodeToQueryParamLookup.put(130, 166);
        talentTreeNodeToQueryParamLookup.put(131, 167);
    }

    public void onAccountChanged()
    {
        activeRelics.clear();
        activeAreas.clear();
        activeTalents.clear();

        loadRelics();
        loadAreas();
        loadTalents();
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
            case TALENT_SCRIPT_ID:
                plugin.scheduleOnClientThread(this::gatherTalents, 1000);
                break;
        }
    }

    private void gatherTalents()
    {
        Widget talentTreeWidget = client.getWidget(TALENT_TREE_GROUP_ID, TALENT_TREE_CHILD_ID);

        if (talentTreeWidget == null)
        {
            return;
        }

        plugin.logSupport("Gathering seasonal talents...");

        Widget[] talentWidgets = talentTreeWidget.getDynamicChildren();

        // clear the current active talents before collecting the new ones
        activeTalents.clear();

        for (int talentIndex = 0; talentIndex < talentWidgets.length; talentIndex++)
        {
            Widget talentWidget = talentWidgets[talentIndex];
            Widget[] talentChildWidgets = talentWidget.getDynamicChildren();

            // guard: ensure there are two; a background and icon widget
            if (talentChildWidgets.length < 2)
            {
                continue;
            }

            Widget talentBackgroundWidget = talentChildWidgets[0];
            int talentBackgroundSpriteId = talentBackgroundWidget.getSpriteId();
            boolean isActive = ArrayUtils.contains(TALENT_TREE_ACTIVE_BACKGROUND_SPRITE_IDS, talentBackgroundSpriteId);

            if (!isActive)
            {
                continue;
            }

            plugin.logSupport("Found active seasonal talent: "+ talentIndex);
            activeTalents.add(talentIndex);
        }

        saveTalents();
        updateSeasonalInfo();
    }

    private void gatherAreas()
    {
        List<SeasonalItem> newActiveAreas = new ArrayList<>();
        Widget mapWidget = client.getWidget(AREA_HIGHLIGHT_GROUP_ID, AREA_HIGHLIGHT_CHILD_ID);

        if (mapWidget == null)
        {
            return;
        }

        plugin.logSupport("Gathering seasonal areas...");

        Widget[] areaHighlightWidgets = mapWidget.getStaticChildren();

        for (Widget areaHighlightWidget : areaHighlightWidgets)
        {
            int spriteId = areaHighlightWidget.getSpriteId();
            SeasonalItem area = areaHighlightLookup.get(spriteId);

            if (area == null)
            {
                continue;
            }

            plugin.logSupport("Found new area (sprite: "+ spriteId +"): "+ area.title);
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

        plugin.logSupport("Gathering seasonal relics...");

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

            plugin.logSupport("Found new relic (sprite: "+ spriteId +"): "+ relicName);
        }

        activeRelics.clear();
        activeRelics.addAll(newActiveRelics);

        saveRelics();
        updateSeasonalItems();
    }

    private void updateSeasonalInfo()
    {
        if (activeTalents.isEmpty())
        {
            return;
        }

        final HashMap<String, String> queryParameters = new HashMap<>();
        final ArrayList<Integer> handledTalentNodeIds = new ArrayList<>();

        LambdaIterator.handleAll(activeTalents, (activeTalentNodeId) -> {

            // guard: check the talent is known in the lookup
            if (!talentTreeNodeToQueryParamLookup.containsKey(activeTalentNodeId))
            {
                plugin.logSupport("An unknown talent tree node ID was found, this should not happen. Node ID: "+ activeTalentNodeId);
                return;
            }

            // guard: skip when the talent was already added to the query params
            // NOTE: this should not happen, but can fix some issues with a previous version of the plugin
            if (handledTalentNodeIds.contains(activeTalentNodeId))
            {
                return;
            }

            String nQueryParameter = queryParameters.get("n");

            if (nQueryParameter == null)
            {
                nQueryParameter = "";
            } else {
                nQueryParameter += "-";
            }

            nQueryParameter += talentTreeNodeToQueryParamLookup.get(activeTalentNodeId);
            handledTalentNodeIds.add(activeTalentNodeId);
            queryParameters.put("n", nQueryParameter);
        });

        String nQueryParameter = queryParameters.get("n");
        plugin.logSupport("Setting seasonal query parameter 'n': "+ nQueryParameter);
        twitchState.setSeasonalInfoQueryParameter("n", nQueryParameter);
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
            seasonalItems.add(new SeasonalItem("Areas"));
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

    private void loadTalents()
    {
        plugin.loadFromConfiguration(SEASONAL_TALENTS_CONFIG_KEY, (data) -> {
            ArrayList<Integer> newActiveTalents = gson.fromJson(data, new TypeToken<ArrayList<Integer>>(){}.getType());
            activeTalents.clear();
            activeTalents.addAll(newActiveTalents);
            updateSeasonalInfo();
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
    
    private void saveTalents()
    {
        plugin.setConfiguration(SEASONAL_TALENTS_CONFIG_KEY, convertItemsToJson(activeTalents));
    }

    private JsonElement convertItemsToJson(List<?> seasonalItems)
    {
        return gson.toJsonTree(seasonalItems, new TypeToken<List<?>>() {}.getType());
    }
}
