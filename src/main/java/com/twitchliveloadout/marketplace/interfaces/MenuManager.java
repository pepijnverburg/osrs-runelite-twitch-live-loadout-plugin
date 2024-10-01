package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.MarketplaceEffectManager;
import com.twitchliveloadout.marketplace.products.EbsMenuOptionFrame;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.Iterator;

import static com.twitchliveloadout.TwitchLiveLoadoutPlugin.IN_DEVELOPMENT;
import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class MenuManager extends MarketplaceEffectManager<EbsMenuOptionFrame> {
	private final TwitchLiveLoadoutConfig config;
	private final Client client;

	public MenuManager(TwitchLiveLoadoutConfig config, Client client)
	{
		super(MENU_EFFECT_MAX_SIZE);

		this.config = config;
		this.client = client;
	}

	public void onGameTick()
	{
		updateEffects();
	}

	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuEntry menuEntry = event.getMenuEntry();
		String clickedOption = event.getMenuOption();
		String clickedTarget = event.getMenuTarget();
		String clickedEntityType = "";
		Actor clickedActor = event.getMenuEntry().getActor();
		LocalPoint clickedEntityLocation = clickedActor != null ? clickedActor.getLocalLocation() : null;
		TileObject clickedObject = null;
		Player localPlayer = client.getLocalPlayer();

		if (menuEntry != null)
		{

			// an item is also considered a widget, so for this reason
			// we need to check for a valid item ID first
			if (menuEntry.getItemId() >= 0) {
				clickedEntityType = ITEM_MENU_ENTITY_TYPE;
			} else if (menuEntry.getWidget() != null) {
				clickedEntityType = WIDGET_MENU_ENTITY_TYPE;
			} else if (menuEntry.getNpc() != null) {
				clickedEntityType = NPC_MENU_ENTITY_TYPE;
			} else if (menuEntry.getPlayer() != null) {
				clickedEntityType = PLAYER_MENU_ENTITY_TYPE;
			} else {

				switch (event.getMenuAction())
				{

					// game objects
					case WIDGET_TARGET_ON_GAME_OBJECT:
					case GAME_OBJECT_FIRST_OPTION:
					case GAME_OBJECT_SECOND_OPTION:
					case GAME_OBJECT_THIRD_OPTION:
					case GAME_OBJECT_FOURTH_OPTION:
					case GAME_OBJECT_FIFTH_OPTION:
					{
						int x = event.getParam0();
						int y = event.getParam1();
						int id = event.getId();
						clickedObject = findTileObject(x, y, id);

						// only fill properties when a valid object is found
						if (clickedObject != null)
						{
							clickedEntityType = OBJECT_MENU_ENTITY_TYPE;
							clickedEntityLocation = clickedObject.getLocalLocation();
						}
						break;
					}

//					// alternative player interactions
//					case WIDGET_TARGET_ON_PLAYER:
//						break;

					// ground items
					case WIDGET_TARGET_ON_GROUND_ITEM:
					case GROUND_ITEM_FIRST_OPTION:
					case GROUND_ITEM_SECOND_OPTION:
					case GROUND_ITEM_THIRD_OPTION:
					case GROUND_ITEM_FOURTH_OPTION:
					case GROUND_ITEM_FIFTH_OPTION:
						int x = event.getParam0();
						int y = event.getParam1();
						Tile tile = getSceneTile(x, y);

						// only fill properties when valid tile is found
						if (tile != null)
						{
							clickedEntityType = GROUND_ITEM_MENU_ENTITY_TYPE;
							clickedEntityLocation = tile.getLocalLocation();
						}
						break;
				}
			}
		}

		if (config.debugMenuOptionClicks() && IN_DEVELOPMENT)
		{
			log.info("MENU OPTION CLICKED:");
			log.info("clickedOption: "+ clickedOption);
			log.info("clickedTarget: "+ clickedTarget);
			log.info("clickedEntityType: "+ clickedEntityType);
			log.info("clickedEntityLocation: "+ clickedEntityLocation);
			log.info("getParam0: "+ event.getParam0());
			log.info("getParam1: "+ event.getParam1());
			log.info("getId: "+ event.getId());
		}

		boolean hasLocalPlayer = localPlayer != null;
		boolean hasClickedEntityLocation = clickedEntityLocation != null;
		Iterator<MarketplaceEffect<EbsMenuOptionFrame>> effectIterator = effects.iterator();

		// check if the event should be disabled
		while (effectIterator.hasNext())
		{
			MarketplaceEffect<EbsMenuOptionFrame> effect = effectIterator.next();

			// guard: skip when not active
			if (!effect.isActive())
			{
				continue;
			}

			MarketplaceProduct marketplaceProduct = effect.getMarketplaceProduct();
			EbsMenuOptionFrame menuOptionFrame = effect.getFrame();
			boolean satisfiesOptions = verifyPropertyMatch(clickedOption, menuOptionFrame.matchedOptions);
			boolean satisfiesTargets = verifyPropertyMatch(clickedTarget, menuOptionFrame.matchedTargets);
			boolean satisfiesEntityTypes = verifyPropertyMatch(clickedEntityType, menuOptionFrame.matchedEntityTypes);
			Integer minClickRange = menuOptionFrame.minClickRange;
			Integer maxClickRange = menuOptionFrame.maxClickRange;
			boolean hasClickRange = minClickRange != null || maxClickRange != null;
			boolean satisfiesClickRange = true;

			if (hasLocalPlayer && hasClickedEntityLocation && hasClickRange)
			{
				LocalPoint localPlayerLocation = localPlayer.getLocalLocation();
				int distance = localPlayerLocation.distanceTo(clickedEntityLocation) / 128;

				// handle diagonal tiles where we will consider single tile distance diagonal as two tiles!
				// this is in practice two tiles distance when interacting with things!
				if (distance == 1)
				{
					int deltaX = localPlayerLocation.getX() - clickedEntityLocation.getX();
					int deltaY = localPlayerLocation.getY() - clickedEntityLocation.getY();
					boolean isDiagonal = Math.abs(deltaX) > 0 && Math.abs(deltaY) > 0;

					if (isDiagonal)
					{
						distance = 2;
					}
				}

				// when there is a click range the default is that it's not satisfied
				satisfiesClickRange = false;

				if (config.debugMenuOptionClicks() && IN_DEVELOPMENT)
				{
					log.info("Distance to menu click: "+ distance);
				}

				if (minClickRange != null && distance < minClickRange)
				{
					satisfiesClickRange = true;
				}

				if (maxClickRange != null && distance > maxClickRange)
				{
					satisfiesClickRange = true;
				}
			}

			// guard: if one of the conditions is not satisfied we will skip the effect!
			if (!satisfiesOptions || !satisfiesTargets || !satisfiesEntityTypes || !satisfiesClickRange)
			{
				continue;
			}

			// handle disable effects
			if (DISABLE_MENU_OPTION_TYPE.equals(menuOptionFrame.type))
			{
				event.consume();
			}

			marketplaceProduct.triggerEffects(
				menuOptionFrame.onClickEffects,
				0,
				null,
				effect,
				false,
				null
			);
		}
	}

	private boolean verifyPropertyMatch(String property, ArrayList<String> candidates)
	{

		// guard: verify at once when there are no candidates
		// NOTE: an empty list is considered no matches!
		if (candidates == null)
		{
			return true;
		}

		// guard: make sure the property is valid if not, then we wont verify
		if (property == null)
		{
			return false;
		}

		String formattedProperty = Text.removeTags(property.toLowerCase().trim());

		// if at least one candidate matches it is verified!
		for (String candidate : candidates)
		{

			// guard: make sure the candidate is valid
			if (candidate == null)
			{
				continue;
			}

			String formattedCandidate = candidate.toLowerCase();
			if (formattedProperty.matches(formattedCandidate))
			{
				return true;
			}
		}

		return false;
	}

	/*
	 * Copyright (c) 2021, Adam <Adam@sigterm.info>
	 * All rights reserved.
	 *
	 * Redistribution and use in source and binary forms, with or without
	 * modification, are permitted provided that the following conditions are met:
	 *
	 * 1. Redistributions of source code must retain the above copyright notice, this
	 *    list of conditions and the following disclaimer.
	 * 2. Redistributions in binary form must reproduce the above copyright notice,
	 *    this list of conditions and the following disclaimer in the documentation
	 *    and/or other materials provided with the distribution.
	 *
	 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
	 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
	 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
	 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
	 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
	 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
	 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
	 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
	 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
	 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
	 */
	private TileObject findTileObject(int x, int y, int id)
	{
		Tile tile = getSceneTile(x, y);

		if (tile != null)
		{
			for (GameObject gameObject : tile.getGameObjects())
			{
				if (gameObject != null && gameObject.getId() == id)
				{
					return gameObject;
				}
			}

			WallObject wallObject = tile.getWallObject();
			if (wallObject != null && wallObject.getId() == id)
			{
				return wallObject;
			}

			DecorativeObject decorativeObject = tile.getDecorativeObject();
			if (decorativeObject != null && decorativeObject.getId() == id)
			{
				return decorativeObject;
			}

			GroundObject groundObject = tile.getGroundObject();
			if (groundObject != null && groundObject.getId() == id)
			{
				return groundObject;
			}
		}
		return null;
	}

	private Tile getSceneTile(int x, int y)
	{
		try {
			x += (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2;
			y += (Constants.EXTENDED_SCENE_SIZE - Constants.SCENE_SIZE) / 2;
			WorldView worldView = client.getTopLevelWorldView();
			Scene scene = client.getTopLevelWorldView().getScene();
			Tile[][][] tiles = scene.getExtendedTiles();
			Tile tile = tiles[worldView.getPlane()][x][y];

			return tile;
		} catch (Exception exception) {
			log.error("Could not find a scene tile from a menu click location: ", exception);
		}

		return null;
	}

	@Override
	protected void onAddEffect(MarketplaceEffect<EbsMenuOptionFrame> effect)
	{
		// empty
	}

	@Override
	protected void onDeleteEffect(MarketplaceEffect<EbsMenuOptionFrame> effect)
	{
		// empty
	}

	@Override
	protected void restoreEffect(MarketplaceEffect<EbsMenuOptionFrame> effect)
	{
		// empty
	}

	@Override
	protected void applyEffect(MarketplaceEffect<EbsMenuOptionFrame> effect)
	{
		// empty
	}
}
