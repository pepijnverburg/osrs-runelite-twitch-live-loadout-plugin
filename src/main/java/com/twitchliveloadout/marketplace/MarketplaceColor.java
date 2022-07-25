package com.twitchliveloadout.marketplace;

import lombok.Getter;

import java.awt.Color;

public enum MarketplaceColor {
	PURPLE_COLOR(new Color(145, 70, 255));

	@Getter
	private final Color color;

	MarketplaceColor(Color color)
	{
		this.color = color;
	}
}
