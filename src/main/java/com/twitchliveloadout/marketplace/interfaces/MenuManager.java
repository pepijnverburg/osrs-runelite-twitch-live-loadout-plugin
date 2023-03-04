package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.MarketplaceEffectManager;
import com.twitchliveloadout.marketplace.products.EbsMenuOptionFrame;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.MenuOptionClicked;

import java.util.Iterator;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.DISABLE_MENU_OPTION_TYPE;
import static com.twitchliveloadout.marketplace.MarketplaceConstants.MENU_EFFECT_MAX_SIZE;

@Slf4j
public class MenuManager extends MarketplaceEffectManager<EbsMenuOptionFrame> {

	public MenuManager()
	{
		super(MENU_EFFECT_MAX_SIZE);
	}

	public void onGameTick()
	{
		updateEffects();
	}

	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String clickedOption = event.getMenuOption();
		Iterator<MarketplaceEffect<EbsMenuOptionFrame>> effectIterator = effects.iterator();

		// check if the event should be disabled
		while (effectIterator.hasNext())
		{
			MarketplaceEffect<EbsMenuOptionFrame> effect = effectIterator.next();
			MarketplaceProduct marketplaceProduct = effect.getMarketplaceProduct();
			EbsMenuOptionFrame menuOptionFrame = effect.getFrame();

			// guard: skip when not active
			if (!effect.isActive())
			{
				continue;
			}

			// guard: check if valid and if disable type
			if (menuOptionFrame == null ||
				!DISABLE_MENU_OPTION_TYPE.equals(menuOptionFrame.type) ||
				menuOptionFrame.matchedOptions == null)
			{
				continue;
			}

			// check if the option is disabled to cancel the event and trigger any notifications
			for (String option : menuOptionFrame.matchedOptions)
			{
				if (clickedOption.toLowerCase().startsWith(option.toLowerCase()))
				{
					event.consume();
					marketplaceProduct.triggerEffects(menuOptionFrame.onClickEffects);
				}
			}
		}
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
