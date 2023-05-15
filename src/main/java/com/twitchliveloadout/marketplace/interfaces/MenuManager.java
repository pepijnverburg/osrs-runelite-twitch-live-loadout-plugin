package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.MarketplaceEffectManager;
import com.twitchliveloadout.marketplace.products.EbsMenuOptionFrame;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.util.Text;

import java.util.ArrayList;
import java.util.Iterator;

import static com.twitchliveloadout.TwitchLiveLoadoutPlugin.IN_DEVELOPMENT;
import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class MenuManager extends MarketplaceEffectManager<EbsMenuOptionFrame> {
	private final TwitchLiveLoadoutConfig config;

	public MenuManager(TwitchLiveLoadoutConfig config)
	{
		super(MENU_EFFECT_MAX_SIZE);

		this.config = config;
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
			}
		}

		if (config.debugMenuOptionClicks() && IN_DEVELOPMENT)
		{
			log.info("MENU OPTION CLICKED:");
			log.info("clickedOption: "+ clickedOption);
			log.info("clickedTarget: "+ clickedTarget);
			log.info("clickedEntityType: "+ clickedEntityType);
		}

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

			// guard: check if all is satisfied
			if (!satisfiesOptions || !satisfiesTargets || !satisfiesEntityTypes)
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
