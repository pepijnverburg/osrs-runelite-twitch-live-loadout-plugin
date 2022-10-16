package com.twitchliveloadout.raids;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;

@Slf4j
public class InvocationsManager {
	private static final int LOBBY_REGION_ID = 13454;
	private static final int WIDGET_RAID_OVERLAY_PARENT_ID = 481;
	private static final int WIDGET_RAID_OVERLAY_CHILD_ID = 40;
	private static final int WIDGET_ID_INVOCATIONS_PARENT = 774;
	private static final int WIDGET_ID_INVOCATIONS_LIST = 52;
	private static final int WIDGET_ID_INVOCATIONS_HEADER = 3;
	private static final int WIDGET_ID_PARTY_MEMBERS_PARENT = 773;
	private static final int WIDGET_ID_PARTY_MEMBERS_LIST = 5;
	private static final int WIDGET_ID_INVOCATIONS_RAID_LEVEL = 73;
	private static final int SCRIPT_ID_BUILD_TOA_PARTY_INTERFACE = 6729;
	private static final int SCRIPT_ID_TOA_PARTY_TOGGLE_REWARD_PANEL = 6732;
	private static final int INVOCATION_ACTIVE_TEXT_COLOR = 13868852;
	private static final int INVOCATION_INACTIVE_TEXT_COLOR = 10461087;

	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;
	private final Client client;

	public InvocationsManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client)
	{
		this.plugin = plugin;
		this.twitchState = twitchState;
		this.client = client;
	}

	public void onScriptPostFired(ScriptPostFired event)
	{
		// This is run when the party screen is brought up, whenever a tab is changed, and whenever an invocation is clicked
		if (event.getScriptId() != SCRIPT_ID_BUILD_TOA_PARTY_INTERFACE && event.getScriptId() != SCRIPT_ID_TOA_PARTY_TOGGLE_REWARD_PANEL)
		{
			return;
		}

		// guard: make sure the invocations interface that is open is the one of the current
		// party leader, this prevents syncing the wrong data when opening other parties you have not joined.
		if (!isJoinedPartyInterface())
		{
			return;
		}

		updateCurrentActiveInvocations();
		updateCurrentRaidLevel();
	}

	public boolean isJoinedPartyInterface()
	{
		Widget partyWidget = client.getWidget(WIDGET_ID_PARTY_MEMBERS_PARENT, WIDGET_ID_PARTY_MEMBERS_LIST);
		Widget headerWidget = client.getWidget(WIDGET_ID_INVOCATIONS_PARENT, WIDGET_ID_INVOCATIONS_HEADER);
		int headerWidgetTitleIndex = 1;

		if (partyWidget == null || headerWidget == null || headerWidget.getChildren().length <= headerWidgetTitleIndex)
		{
			return false;
		}

		try {
			String partyText = partyWidget.getText();
			String headerText = headerWidget.getChild(headerWidgetTitleIndex).getText();
			String[] partyTextTokens = partyText.split("<br>");
			String[] headerTextTokens = headerText.split("Party of ");
			String leaderInParty = partyTextTokens[0]; // index 0, because party text is like: "NAME<br>-<br>-<br>-<br>-<br>-<br>-<br>-"
			String leaderInHeader = headerTextTokens[1]; // index 1, because header text is like: "Party of NAME"

			// when the names equal we know that we are currently in the joined party interface
			return leaderInParty.equals(leaderInHeader);
		} catch (Exception exception) {
			log.warn("Could not check party due the following error:", exception);
		}

		return false;
	}

	public void checkIfInToA()
	{
		plugin.runOnClientThread(() -> {
			Player localPlayer = client.getLocalPlayer();

			if (localPlayer == null)
			{
				return;
			}

			LocalPoint localPoint = localPlayer.getLocalLocation();
			Widget raidOverlayWidget = client.getWidget(WIDGET_RAID_OVERLAY_PARENT_ID, WIDGET_RAID_OVERLAY_CHILD_ID);

			if (localPoint == null)
			{
				return;
			}

			int currentRegionId = WorldPoint.fromLocalInstance(client, localPoint).getRegionID();

			boolean inLobby = (currentRegionId == LOBBY_REGION_ID);
			boolean hasOverlay = (raidOverlayWidget != null && !raidOverlayWidget.isHidden());
			boolean inToA = inLobby || hasOverlay;

			twitchState.setInToA(inToA);
		});
	}

	private void updateCurrentRaidLevel()
	{
		Widget raidLevelWidget = client.getWidget(WIDGET_ID_INVOCATIONS_PARENT, WIDGET_ID_INVOCATIONS_RAID_LEVEL);

		// guard: check if widget is valid
		if (raidLevelWidget == null)
		{
			return;
		}

		try {
			String raidLevelRaw = raidLevelWidget.getText();
			String[] raidLevelTokens = raidLevelRaw.split(" ");
			String raidLevel = raidLevelTokens[2];
			twitchState.setInvocationsRaidLevel(raidLevel);
		} catch (Exception exception) {
			log.warn("Could not get the raid level from one of the widgets due to the following error:", exception);
		}
	}

	private void updateCurrentActiveInvocations()
	{
		Widget invocationsWidget = client.getWidget(WIDGET_ID_INVOCATIONS_PARENT, WIDGET_ID_INVOCATIONS_LIST);

		// guard: skip if widget is not found
		if (invocationsWidget == null || invocationsWidget.isHidden() || invocationsWidget.getChildren() == null)
		{
			return;
		}

		JsonArray invocations = new JsonArray();
		Widget[] widgets = invocationsWidget.getChildren();

		for (int widgetIndex = 0; widgetIndex < widgets.length; widgetIndex++)
		{
			Widget spriteWidget = widgets[widgetIndex];

			if (spriteWidget == null)
			{
				continue;
			}

			int spriteId = spriteWidget.getSpriteId();
			boolean isInvocationIcon = spriteId > -1;
			int titleWidgetIndex = widgetIndex + 1;
			int activeWidgetIndex = widgetIndex - 1;

			// guard: only handle the icons
			if (!isInvocationIcon)
			{
				continue;
			}

			// guard: make sure the title and active widget exists
			if (titleWidgetIndex >= widgets.length && activeWidgetIndex >= 0)
			{
				continue;
			}

			Widget titleWidget = widgets[titleWidgetIndex];
			String title = titleWidget.getText();
			int titleColor = titleWidget.getTextColor();
			boolean isActive = (titleColor == INVOCATION_ACTIVE_TEXT_COLOR);

			// log.info("Title of invocation title widget "+ titleWidgetIndex +" is:" + title);
			// log.info("Title color of invocation title widget "+ titleWidgetIndex +" is:" + titleColor);
			// log.info("Sprite of invocation icon widget "+ widgetIndex +" is:" +spriteId);
			// log.info("State of invocation active widget "+ activeWidgetIndex +" is:"+ isActive);

			JsonObject invocation = new JsonObject();
			invocation.addProperty("title", title);
			invocation.addProperty("spriteId", spriteId);
			invocation.addProperty("active", isActive);

			invocations.add(invocation);
		}

		twitchState.setInvocations(invocations);
	}
}
