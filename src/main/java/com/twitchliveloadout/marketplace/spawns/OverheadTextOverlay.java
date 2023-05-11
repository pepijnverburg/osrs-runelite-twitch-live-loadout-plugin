package com.twitchliveloadout.marketplace.spawns;

import com.twitchliveloadout.marketplace.MarketplaceColors;
import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.products.EbsModelOverheadFrame;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

import java.awt.*;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class OverheadTextOverlay extends Overlay {
	private final Client client;
	private final CopyOnWriteArrayList<MarketplaceEffect<EbsModelOverheadFrame>> activeEffects = new CopyOnWriteArrayList<>();
	private final Color DEFAULT_TEXT_COLOR = Color.YELLOW;

	public OverheadTextOverlay(Client client)
	{
		this.client = client;

		setMovable(false);
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.HIGHEST);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Iterator<MarketplaceEffect<EbsModelOverheadFrame>> effectIterator = activeEffects.iterator();

		while (effectIterator.hasNext())
		{
			MarketplaceEffect<EbsModelOverheadFrame> effect = effectIterator.next();
			EbsModelOverheadFrame frame = effect.getFrame();
			String text = frame.text;
			Integer textHeight = frame.textHeight;
			Integer textColorHex = frame.textColorHex;
			SpawnedObject spawnedObject = effect.getSpawnedObject();

			// guard: make sure text and location are valid
			if (text == null || text.isEmpty() || textHeight == null || textColorHex == null || spawnedObject == null)
			{
				continue;
			}

			LocalPoint localPoint = spawnedObject.getSpawnPoint().getLocalPoint(client);
			Point textLocation = Perspective.localToCanvas(client, localPoint, client.getPlane(), textHeight);
			Color textColor = MarketplaceColors.getColorByHex(textColorHex);

			// guard: ensure a valid location for the text rendering
			if (textLocation == null)
			{
				continue;
			}

			Font chatFont = FontManager.getRunescapeBoldFont();
			FontMetrics metrics = graphics.getFontMetrics(chatFont);
			Point centeredTextLocation = new Point(textLocation.getX() - (metrics.stringWidth(text) >>> 1), textLocation.getY());

			// guard: ensure a valid centered text location
			if (centeredTextLocation == null)
			{
				continue;
			}

			graphics.setFont(chatFont);
			OverlayUtil.renderTextLocation(graphics, centeredTextLocation, text, textColor);
		}

		return null;
	}

	public void addEffect(MarketplaceEffect<EbsModelOverheadFrame> effect)
	{
		activeEffects.add(effect);
	}

	public void removeEffect(MarketplaceEffect<EbsModelOverheadFrame> effect)
	{
		activeEffects.remove(effect);
	}
}
