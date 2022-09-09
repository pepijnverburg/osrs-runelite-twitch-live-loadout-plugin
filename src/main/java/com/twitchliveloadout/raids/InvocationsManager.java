package com.twitchliveloadout.raids;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchState;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;

@Slf4j
public class InvocationsManager {
	private static final int LOBBY_REGION_ID = 13454;
	private static final int WIDGET_RAID_OVERLAY_PARENT_ID = 481;
	private static final int WIDGET_RAID_OVERLAY_CHILD_ID = 40;
	private static final int WIDGET_ID_INVOCATIONS_PARENT = 774;
	private static final int WIDGET_ID_INVOCATIONS_LIST = 52;
	private static final int WIDGET_ID_INVOCATIONS_RAID_LEVEL = 77;
	private static final int SCRIPT_ID_BUILD_TOA_PARTY_INTERFACE = 6729;
	private static final int SCRIPT_ID_TOA_PARTY_TOGGLE_REWARD_PANEL = 6732;

	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;
	private final Client client;

	public InvocationsManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client)
	{
		this.plugin = plugin;
		this.twitchState = twitchState;
		this.client = client;
	}

	public void onScriptPostFired(ScriptPostFired event) {
		// This is run when the party screen is brought up, whenever a tab is changed, and whenever an invocation is clicked
		if (event.getScriptId() == SCRIPT_ID_BUILD_TOA_PARTY_INTERFACE || event.getScriptId() == SCRIPT_ID_TOA_PARTY_TOGGLE_REWARD_PANEL) {
			updateCurrentActiveInvocations();
			updateCurrentRaidLevel();
		}
	}

	public void checkIfInToA() {
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

		String raidLevel = raidLevelWidget.getText();
		twitchState.setInvocationsRaidLevel(raidLevel);
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
			Widget activeWidget = widgets[activeWidgetIndex];
			String title = titleWidget.getText();
			Object[] activeOps = activeWidget.getOnOpListener();

			// guard: check if operations are valid
			if (activeOps == null || activeOps.length < 4 || !(activeOps[3] instanceof Integer))
			{
				continue;
			}
			boolean isActive = (Integer) activeOps[3] == 1;

//			log.debug("Title of invocation title widget "+ titleWidgetIndex +" is:" + title);
//			log.debug("Sprite of invocation icon widget "+ widgetIndex +" is:" +spriteId);
//			log.debug("State of invocation active widget "+ activeWidgetIndex +" is:"+ isActive);

			JsonObject invocation = new JsonObject();
			invocation.addProperty("title", title);
			invocation.addProperty("spriteId", spriteId);
			invocation.addProperty("active", isActive);

			invocations.add(invocation);
		}

		twitchState.setInvocations(invocations);
	}
}
