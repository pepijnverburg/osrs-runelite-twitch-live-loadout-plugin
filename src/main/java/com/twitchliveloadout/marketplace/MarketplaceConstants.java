package com.twitchliveloadout.marketplace;

public class MarketplaceConstants {
	public static final int TRANSACTION_CHECKED_AT_OFFSET_MS = 5000;
	public static final int TRANSACTION_DELAY_CORRECTION_MS = 2000;

	public static final String CURRENT_TILE_LOCATION_TYPE = "current-tile";
	public static final String PREVIOUS_TILE_LOCATION_TYPE = "previous-tile";

	public static final int DEFAULT_RADIUS = 10;
	public static final String DEFAULT_RADIUS_TYPE = "radius";
	public static final String OUTWARD_RADIUS_TYPE = "outward-radius";

	public static final String RANDOM_ROTATION_TYPE = "random";
	public static final String PLAYER_ROTATION_TYPE = "player";
	public static final String INTERACTING_ROTATION_TYPE = "interacting";

	public static final int RUNELITE_OBJECT_FULL_RADIUS = 60;
	public static final double RUNELITE_OBJECT_FULL_ROTATION = 2047d;

	public static final int PLAYER_TILE_HISTORY_SIZE = 10;
}
