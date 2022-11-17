package com.twitchliveloadout.marketplace;

import net.runelite.api.JagexColor;
import net.runelite.api.ModelData;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.awt.*;
import java.util.HashMap;

@Slf4j
public class MarketplaceModelUtilities {
	public static void resizeSmall(ModelData model)
	{
		model.scale(50, 50, 50);
	}

	public static void resizeRandomly(ModelData model, int minScale, int maxScale)
	{
		int deltaScale = maxScale - minScale;
		int randomScale = minScale + ((int) (Math.random() * ((float) deltaScale)));

		model.scale(randomScale, randomScale, randomScale);
	}

	public static void recolorAllFaces(ModelData model, Color color)
	{
		recolorAllFaces(model, color, 1.0d);
	}

	public static void recolorAllFaces(ModelData model, Color color, double brightness)
	{
		short[] faceColors = model.getFaceColors();

		for (int faceColorIndex = 0; faceColorIndex < faceColors.length; faceColorIndex++)
		{
			recolorFace(model, faceColorIndex, color, brightness);
		}
	}

	public static void recolorFace(ModelData model, int faceColorIndex, Color color, double brightness)
	{
		short[] faceColors = model.getFaceColors();
		int rgb = color.getRGB();

		if (faceColorIndex < 0 || faceColorIndex >= faceColors.length)
		{
			log.warn("An invalid face color index was requested for a recolor: ", faceColorIndex);
			return;
		}

		short faceColor = faceColors[faceColorIndex];
		model.recolor(faceColor, JagexColor.rgbToHSL(rgb, brightness));
	}
}
