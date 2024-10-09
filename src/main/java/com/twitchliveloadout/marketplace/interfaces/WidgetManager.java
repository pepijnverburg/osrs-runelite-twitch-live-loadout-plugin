package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.LambdaIterator;
import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.MarketplaceEffectManager;
import com.twitchliveloadout.marketplace.products.EbsInterfaceWidgetFrame;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class WidgetManager extends MarketplaceEffectManager<EbsInterfaceWidgetFrame> {
	private final TwitchLiveLoadoutPlugin plugin;
	private final Client client;

	/**
	 * Track all the covering overlays that were initialized to show dark / snow / other overlay effects.
	 * This is a hashmap to support multiple client settings such as resized and fixed, which require different widgets
	 */
	private final ConcurrentHashMap<Widget, Widget> coveringOverlays = new ConcurrentHashMap<>();

	/**
	 * Separate, centralized store of the original widget states because it is possible that multiple active products
	 * change one widget at the same time. This prevents that original widget states are states changed by other products
	 * and with that make sure the true original state is being tracked. There is still a risk other game state changes are
	 * missed because of this and the state might be out of sync. Because of this we need to make sure to only adjust
	 * widgets that don't have this.
	 */
	private final ConcurrentHashMap<Widget, OriginalWidget> originalWidgets = new ConcurrentHashMap<>();

	public WidgetManager(TwitchLiveLoadoutPlugin plugin, Client client)
	{
		super(WIDGET_EFFECT_MAX_SIZE);

		this.plugin = plugin;
		this.client = client;
	}

	public void onGameTick()
	{
		ensureCoveringOverlays();
	}

	public void hideCoveringOverlays()
	{
		LambdaIterator.handleAllValues(coveringOverlays, (coveringOverlay) -> {
			coveringOverlay.setHidden(true);
		});
	}

	private void ensureCoveringOverlays()
	{
		plugin.runOnClientThread(() -> {
			boolean isResized = client.isResized();

			if (isResized) {
				ensureCoveringOverlay(CoveringOverlayType.RESIZED_CLASSIC);
				ensureCoveringOverlay(CoveringOverlayType.RESIZED_MODERN);
			} else {
				ensureCoveringOverlay(CoveringOverlayType.FIXED);
			}
		});
	}

	private void ensureCoveringOverlay(CoveringOverlayType overlayType)
	{
		plugin.runOnClientThread(() -> {
			final Widget parent = client.getWidget(overlayType.getWidgetGroupId(), overlayType.getWidgetChildId());

			if (parent == null) {
				return;
			}

			// get the overlay based on the parent instance because the parent
			// change across logins, this makes sure we always instantiate a new one
			Widget coveringOverlay = coveringOverlays.get(parent);

			// guard: check if already existing
			if (coveringOverlay != null)
			{

				// initialize the overlay in case it was set to hidden after shutdown
				if (coveringOverlay.isHidden())
				{
					initializeCoveringOverlay(coveringOverlay);
				}
				return;
			}

			// create child widget
			coveringOverlay = parent.createChild(WidgetType.RECTANGLE);
			coveringOverlays.put(parent, coveringOverlay);

			initializeCoveringOverlay(coveringOverlay);
		});
	}

	private void initializeCoveringOverlay(Widget coveringOverlay)
	{

		// guard: make sure overlay is valid
		if (coveringOverlay == null)
		{
			return;
		}

		// set to default properties
		coveringOverlay.setHidden(false);
		coveringOverlay.setFilled(true);
		coveringOverlay.setType(3);
		coveringOverlay.setOpacity(255);
		coveringOverlay.setWidthMode(1);
		coveringOverlay.setHeightMode(1);
		coveringOverlay.setXPositionMode(1);
		coveringOverlay.setYPositionMode(1);
		coveringOverlay.setModelType(1);
		coveringOverlay.setModelId(-1);
		coveringOverlay.setAnimationId(-1);
		coveringOverlay.setModelZoom(1);
		coveringOverlay.revalidate();
	}

	@Override
	protected void applyEffect(MarketplaceEffect<EbsInterfaceWidgetFrame> effect)
	{
		EbsInterfaceWidgetFrame widgetFrame = effect.getFrame();
		ArrayList<Widget> widgets = getWidgets(widgetFrame);

		// guard: make sure this widget is known and valid
		if (widgets.size() <= 0)
		{
			return;
		}

		// guard: disable overlay widget effects when dangerous effects are not allowed
		if (OVERLAY_INTERFACE_WIDGET_TYPE.equals(widgetFrame.effectType) && !plugin.canPerformDangerousEffects())
		{
			return;
		}

		LambdaIterator.handleAll(widgets, (widget) -> {

			// always make sure a potential original is stored
			registerOriginalWidget(widget);
			String effectType = widgetFrame.effectType;
			Integer widgetType = widgetFrame.widgetType;
			Integer contentType = widgetFrame.contentType;
			String text = widgetFrame.text;
			Integer textColor = widgetFrame.textColor;
			Integer opacity = widgetFrame.opacity;
			Integer itemId = widgetFrame.itemId;
			Integer itemQuantity = widgetFrame.itemQuantity;
			String name = widgetFrame.name;
			Integer spriteId = widgetFrame.spriteId;
			Integer modelId = widgetFrame.modelId;
			Integer modelZoom = widgetFrame.modelZoom;
			Integer animationId = widgetFrame.animationId;

			plugin.runOnClientThread(() -> {
				// only disable widgets dangerous effects are allowed
				if (DISABLE_INTERFACE_WIDGET_TYPE.equals(effectType) && plugin.canPerformDangerousEffects())
				{
					widget.setHidden(true);
					return;
				}

				if (widgetType != null)
				{
					widget.setType(widgetType);
				}

				if (contentType != null)
				{
					widget.setContentType(contentType);
				}

				if (text != null)
				{
					widget.setText(text);
				}

				if (textColor != null)
				{
					widget.setTextColor(textColor);
				}

				if (opacity != null)
				{
					widget.setOpacity(opacity);
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

				if (modelId != null)
				{
					widget.setModelId(modelId);
				}

				if (modelZoom != null)
				{
					widget.setModelZoom(modelZoom);
				}

				if (animationId != null)
				{
					widget.setAnimationId(animationId);
				}
			});
		});
	}

	@Override
	protected void restoreEffect(MarketplaceEffect<EbsInterfaceWidgetFrame> effect)
	{
		EbsInterfaceWidgetFrame widgetFrame = effect.getFrame();
		ArrayList<Widget> widgets = getWidgets(widgetFrame);

		LambdaIterator.handleAll(widgets, (widget) -> {

			// guard: make sure this widget is known and valid
			if (widget == null || !originalWidgets.containsKey(widget))
			{
				return;
			}

			OriginalWidget originalWidget = originalWidgets.get(widget);

			// restore the properties of the widget
			widget.setHidden(originalWidget.getHidden());
			widget.setType(originalWidget.getType());
			widget.setContentType(originalWidget.getContentType());
			widget.setText(originalWidget.getText());
			widget.setTextColor(originalWidget.getTextColor());
			widget.setOpacity(originalWidget.getOpacity());
			widget.setItemId(originalWidget.getItemId());
			widget.setItemQuantity(originalWidget.getItemQuantity());
			widget.setName(originalWidget.getName());
			widget.setSpriteId(originalWidget.getSpriteId());
			widget.setModelId(originalWidget.getModelId());
			widget.setModelZoom(originalWidget.getModelZoom());
			widget.setAnimationId(originalWidget.getAnimationId());
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

	private ArrayList<Widget> getWidgets(EbsInterfaceWidgetFrame widgetFrame)
	{
		final ArrayList<Widget> widgets = new ArrayList<>();
		final String effectType = widgetFrame.effectType;

		if (OVERLAY_INTERFACE_WIDGET_TYPE.equals(effectType)) {

			// get all widgets for all screens because when restoring an effect
			// this should be done for ALL covering overlay widgets otherwise
			// one might keep on going on a certain effect when switching resizing mode
			widgets.addAll(coveringOverlays.values());
		} else {
			final Integer parentId = widgetFrame.parentId;
			final Integer childId = widgetFrame.childId;
			final Integer listIndex = widgetFrame.listIndex;
			Widget widget = getWidget(parentId, childId, listIndex);

			if (widget != null) {
				widgets.add(widget);
			}
		}

		return widgets;
	}

	private Widget getWidget(Integer parentId, Integer childId, Integer listIndex)
	{
		try {

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

			if (widget == null)
			{
				return null;
			}

			return widget.getChild(listIndex);
		} catch (Exception exception) {
			plugin.logSupport("Could not get a widget by widget frame, due to the following error:", exception);
		}

		return null;
	}

	@Override
	protected void onAddEffect(MarketplaceEffect<EbsInterfaceWidgetFrame> effect)
	{
		// empty
	}

	@Override
	protected void onDeleteEffect(MarketplaceEffect<EbsInterfaceWidgetFrame> effect)
	{
		// empty
	}
}
