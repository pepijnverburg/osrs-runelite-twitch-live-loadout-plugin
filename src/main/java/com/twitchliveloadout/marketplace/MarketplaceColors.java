package com.twitchliveloadout.marketplace;

import net.runelite.api.JagexColor;

import java.awt.*;

public class MarketplaceColors {

	public static int getColorHsl(Integer colorHex)
	{
		Color color = getColorByHex(colorHex);
		short colorHsl = JagexColor.rgbToHSL(color.getRGB(), 1.0d);

		return colorHsl;
	}

	public static Color getColorByHex(Integer colorHex)
	{
		int r = (colorHex & 0xFF0000) >> 16;
		int g = (colorHex & 0xFF00) >> 8;
		int b = (colorHex & 0xFF);

		return new Color(r, g, b);
	}
}
