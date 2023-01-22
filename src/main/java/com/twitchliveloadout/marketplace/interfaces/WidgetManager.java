package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.products.EbsInterfaceWidgetFrame;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.SoundEffectID;
import net.runelite.api.widgets.Widget;

import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class WidgetManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final Client client;

	/**
	 * Tracking of all the widget effects by any widget frames from any product. They are stored here and not in the
	 * marketplace products because they can also originate from periodic effects, where the marketplace products
	 * are unaware of and have no way of tracking.
	 */
	private final CopyOnWriteArrayList<WidgetEffect> widgetEffects = new CopyOnWriteArrayList();

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
		this.plugin = plugin;
		this.client = client;
	}

	public void onGameTick()
	{
		cleanWidgetEffects();
		applyWidgetEffects();
	}

	public void addWidgetEffect(MarketplaceProduct product, EbsInterfaceWidgetFrame widgetFrame)
	{

		// guard: make sure the maximum is not exceeded for security and performance reasons
		if (widgetEffects.size() >= WIDGET_EFFECT_MAX_SIZE)
		{
			return;
		}

		// guard: make sure the widget frame is valid
		if (product == null || widgetFrame == null)
		{
			return;
		}

		// by default the expiry time is linked to the product, but can be overridden with a custom duration
		Integer durationMs = widgetFrame.durationMs;
		Instant expiresAt = Instant.now().plusMillis(product.getExpiresInMs());

		if (durationMs != null && durationMs >= 0)
		{
			expiresAt = Instant.now().plusMillis(durationMs);
		}

		log.info("Adding new widget effect for widget frame parent "+ widgetFrame.parentId +" and child "+ widgetFrame.childId);

		// register the new widget effect
		WidgetEffect widgetEffect = new WidgetEffect(product, widgetFrame, expiresAt);
		widgetEffects.add(widgetEffect);
	}

	private void cleanWidgetEffects()
	{
		Iterator widgetEffectIterator = widgetEffects.iterator();

		while (widgetEffectIterator.hasNext())
		{
			WidgetEffect widgetEffect = (WidgetEffect) widgetEffectIterator.next();
			MarketplaceProduct marketplaceProduct = widgetEffect.getMarketplaceProduct();
			boolean isExpired = widgetEffect.isExpired() || marketplaceProduct.isExpired();
			boolean isActive = marketplaceProduct.isActive();

			// check if we should remove this active widget frame
			if (isExpired)
			{
				Widget widget = getWidget(widgetEffect.getInterfaceWidgetFrame());

				// remove from the active widgets
				widgetEffects.remove(widgetEffect);

				// always restore the widget without the need to check
				// if other effect still change this widget, because
				// on the next apply cycle these effects change the widget
				restoreWidget(widget);
			}

			// check if we should only restore the widget for now, because the marketplace product is inactive
			if (!isActive)
			{
				Widget widget = getWidget(widgetEffect.getInterfaceWidgetFrame());
				restoreWidget(widget);
			}
		}
	}

	private void applyWidgetEffects()
	{
		Iterator widgetEffectIterator = widgetEffects.iterator();

		while (widgetEffectIterator.hasNext())
		{
			WidgetEffect widgetEffect = (WidgetEffect) widgetEffectIterator.next();
			EbsInterfaceWidgetFrame widgetFrame = widgetEffect.getInterfaceWidgetFrame();
			MarketplaceProduct marketplaceProduct = widgetEffect.getMarketplaceProduct();
			boolean isActive = marketplaceProduct.isActive();
			Widget widget = getWidget(widgetFrame);

			// guard: make sure the product is active and the widget is valid
			if (!isActive || widget == null)
			{
				continue;
			}

			registerOriginalWidget(widget);
			applyWidgetFrame(widgetFrame);
		}
	}

	private void restoreWidget(Widget widget)
	{

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

	private void applyWidgetFrame(EbsInterfaceWidgetFrame widgetFrame)
	{
		Widget widget = getWidget(widgetFrame);

		// guard: make sure the widget is valid
		if (widget == null)
		{
			return;
		}

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
}
