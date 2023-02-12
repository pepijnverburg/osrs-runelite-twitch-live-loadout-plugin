package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.notifications.NotificationManager;
import com.twitchliveloadout.marketplace.products.EbsMenuOptionFrame;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.MenuOptionClicked;

import java.util.Iterator;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.DISABLE_MENU_OPTION_TYPE;
import static com.twitchliveloadout.marketplace.MarketplaceConstants.MENU_EFFECT_MAX_SIZE;

@Slf4j
public class MenuManager extends InterfaceManager {
	private final MarketplaceManager manager;

	public MenuManager(MarketplaceManager manager)
	{
		super(MENU_EFFECT_MAX_SIZE);

		this.manager = manager;
	}

	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		NotificationManager notificationManager = manager.getNotificationManager();
		String clickedOption = event.getMenuOption();
		Iterator effectIterator = effects.iterator();

		// check if the event should be disabled
		while (effectIterator.hasNext())
		{
			InterfaceEffect effect = (InterfaceEffect) effectIterator.next();
			EbsMenuOptionFrame menuOptionFrame = (EbsMenuOptionFrame) effect.getFrame();

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
					notificationManager.handleEbsNotifications(
						effect.getMarketplaceProduct(),
						menuOptionFrame.onClickNotifications
					);
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
