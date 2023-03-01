package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.notifications.NotificationManager;
import com.twitchliveloadout.marketplace.products.EbsMenuOptionFrame;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.MenuOptionClicked;

import java.util.Iterator;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.DISABLE_MENU_OPTION_TYPE;
import static com.twitchliveloadout.marketplace.MarketplaceConstants.MENU_EFFECT_MAX_SIZE;

@Slf4j
public class MenuManager extends InterfaceManager {

	public MenuManager()
	{
		super(MENU_EFFECT_MAX_SIZE);
	}

	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String clickedOption = event.getMenuOption();
		Iterator<InterfaceEffect<EbsMenuOptionFrame>> effectIterator = effects.iterator();

		// check if the event should be disabled
		while (effectIterator.hasNext())
		{
			InterfaceEffect<EbsMenuOptionFrame> effect = effectIterator.next();
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
					marketplaceProduct.triggerVisualEffects(menuOptionFrame.onClickVisualEffects);
				}
			}
		}
	}

	@Override
	protected void onAddEffect(InterfaceEffect effect)
	{
		// empty
	}

	@Override
	protected void onDeleteEffect(InterfaceEffect effect)
	{
		// empty
	}

	@Override
	protected void restoreEffect(InterfaceEffect effect)
	{
		// empty
	}

	@Override
	protected void applyEffect(InterfaceEffect effect)
	{
		// empty
	}
}
