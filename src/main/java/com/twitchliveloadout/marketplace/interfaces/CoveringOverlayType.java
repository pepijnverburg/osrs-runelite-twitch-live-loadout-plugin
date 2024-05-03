package com.twitchliveloadout.marketplace.interfaces;

import lombok.Getter;

public enum CoveringOverlayType {
	// NOTE: use the root level bottom widgets in the Widget inspector
	FIXED("fixed", 548, 26),
	RESIZED_CLASSIC("resized-classic", 161, 94),
	RESIZED_MODERN("resized-modern", 164, 91),
	;

	@Getter
	private final String name;

	@Getter
	private final int widgetGroupId;

	@Getter
	private final int widgetChildId;

	CoveringOverlayType(String name, int widgetGroupId, int widgetChildId)
	{
		this.name = name;
		this.widgetGroupId = widgetGroupId;
		this.widgetChildId = widgetChildId;
	}
}
