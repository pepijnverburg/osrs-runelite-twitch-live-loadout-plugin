package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.products.EbsInterfaceWidgetFrame;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

import java.util.concurrent.ConcurrentHashMap;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class WidgetManager extends InterfaceManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final Client client;

	/**
	 * Separate, centralized store of the original widget states because it is possible that multiple active products
	 * change one widget at the same time. This prevents that original widget states are states changed by other products
	 * and with that make sure the true original state is being tracked. There is still a risk other game state changes are
	 * missed because of this and the state might be out of sync. Because of this we need to make sure to only adjust
	 * widgets that don't have this.
	 */
	private final ConcurrentHashMap<Widget, OriginalWidget> originalWidgets = new ConcurrentHashMap();

	public WidgetManager(TwitchLiveLoadoutPlugin plugin, Client client)
	{
		super(WIDGET_EFFECT_MAX_SIZE);

		this.plugin = plugin;
		this.client = client;
	}

	@Override
	protected void applyEffect(InterfaceEffect effect)
	{
		EbsInterfaceWidgetFrame widgetFrame = (EbsInterfaceWidgetFrame) effect.getFrame();
		Widget widget = getWidget(widgetFrame);

		// guard: make sure this widget is known and valid
		if (widget == null)
		{
			return;
		}

		// always make sure a potential original is stored
		registerOriginalWidget(widget);

		String type = widgetFrame.type;
		String text = widgetFrame.text;
		Integer textColor = widgetFrame.textColor;
		Integer itemId = widgetFrame.itemId;
		Integer itemQuantity = widgetFrame.itemQuantity;
		String name = widgetFrame.name;
		Integer spriteId = widgetFrame.spriteId;

		plugin.runOnClientThread(() -> {
			// hide widget when disable is requested
			if (DISABLE_INTERFACE_WIDGET_TYPE.equals(type))
			{
				widget.setHidden(true);
			}

			// change text, color and more when alter is requested
			else if (ALTER_INTERFACE_WIDGET_TYPE.equals(type))
			{
				if (text != null)
				{
					widget.setText(text);
				}

				if (textColor != null)
				{
					widget.setTextColor(textColor);
				}

				if (itemId != null)
				{
					widget.setItemId(itemId);
				}

				if (itemQuantity != null)
				{
					widget.setItemQuantity(itemQuantity);
				}

				if (name != null)
				{
					widget.setName(name);
				}

				if (spriteId != null)
				{
					widget.setSpriteId(spriteId);
				}
			}
		});
	}

	@Override
	protected void restoreEffect(InterfaceEffect effect)
	{
		EbsInterfaceWidgetFrame widgetFrame = (EbsInterfaceWidgetFrame) effect.getFrame();
		Widget widget = getWidget(widgetFrame);

		// guard: make sure this widget is known and valid
		if (widget == null || !originalWidgets.containsKey(widget))
		{
			return;
		}

		OriginalWidget originalWidget = originalWidgets.get(widget);

		// restore the properties of the widget
		widget.setHidden(originalWidget.getHidden());
		widget.setText(originalWidget.getText());
		widget.setTextColor(originalWidget.getTextColor());
		widget.setItemId(originalWidget.getItemId());
		widget.setItemQuantity(originalWidget.getItemQuantity());
		widget.setName(originalWidget.getName());
		widget.setSpriteId(originalWidget.getSpriteId());
	}

	private void registerOriginalWidget(Widget widget)
	{

		// guard: make sure the widget is valid and not known yet
		if (widget == null || originalWidgets.containsKey(widget))
		{
			return;
		}

		OriginalWidget originalWidget = new OriginalWidget(widget);
		originalWidgets.put(widget, originalWidget);
	}

	private Widget getWidget(EbsInterfaceWidgetFrame widgetFrame)
	{
		try {
			final Integer parentId = widgetFrame.parentId;
			final Integer childId = widgetFrame.childId;
			final Integer listIndex = widgetFrame.listIndex;

			// guard: make sure the parent and child selectors are valid
			if (parentId < 0 || childId < 0)
			{
				return null;
			}

			Widget widget = client.getWidget(parentId, childId);

			// guard: check if no index is requested
			if (listIndex < 0)
			{
				return widget;
			}

			return widget.getChild(listIndex);
		} catch (Exception exception) {
			log.warn("Could not get a widget by widget frame, due to the following error:", exception);
		}

		return null;
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
}
