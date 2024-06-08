package com.twitchliveloadout.marketplace;

public class MarketplaceConstants {
	public static final boolean TEST_MODE_AVAILABLE = true;
	public static final boolean CHAOS_MODE_AVAILABLE = false;
	public static final boolean FREE_MODE_AVAILABLE = true;
	public static final int EBS_REQUIRED_PRODUCT_VERSION = 1;

	public static final int MAX_MARKETPLACE_PRODUCT_AMOUNT_IN_MEMORY = 50;
	public static final int MAX_TRANSACTION_AMOUNT_IN_MEMORY = 50;
	public static final int TRANSACTION_DELAY_CORRECTION_MS = 1 * 200;
	public static final int TRANSACTION_EXPIRY_CLOCK_TOLERANCE_MS = 30 * 1000;
	public static final int TICK_DURATION_MS = 600;
	public static final int GAME_CYCLE_DURATION_MS = 20; // refer to client.getGameCycle()

	public static final String CURRENT_TILE_LOCATION_TYPE = "current-tile";
	public static final String PREVIOUS_TILE_LOCATION_TYPE = "previous-tile";
	public static final String MODEL_TILE_LOCATION_TYPE = "model-tile";
	public static final String INTERACTING_TILE_LOCATION_TYPE = "interacting-tile";

	public static final String NONE_FOLLOW_TYPE = "none";
	public static final String IN_RADIUS_FOLLOW_TYPE = "in-radius";
	public static final String PREVIOUS_TILE_FOLLOW_TYPE = "behind-player";

	public static final String TRANSLATE_X_AXIS = "x";
	public static final String TRANSLATE_Y_AXIS = "y";
	public static final String TRANSLATE_Z_AXIS = "z";

	public static final double RUNELITE_OBJECT_RADIUS_PER_TILE = 60d;
	public static final double RUNELITE_OBJECT_FULL_ROTATION = 2047d;
	public static final int REGION_SIZE = 64;
	public static final int CHUNK_SIZE = 8;

	public static final int DEFAULT_MIN_RADIUS = 1;
	public static final int DEFAULT_MAX_RADIUS = 15;
	public static final int ABSOLUTE_MIN_RADIUS = 0;
	public static final int ABSOLUTE_MAX_RADIUS = REGION_SIZE;
	public static final String DEFAULT_RADIUS_TYPE = "radius";
	public static final String OUTWARD_RADIUS_TYPE = "outward-radius";
	public static final String NO_RADIUS_TYPE = "no-radius";

	public static final String NO_ROTATION_TYPE = "none";
	public static final String FIXED_ROTATION_TYPE = "fixed";
	public static final String RANDOM_ROTATION_TYPE = "random";
	public static final String PLAYER_ROTATION_TYPE = "player";
	public static final String INTERACTING_ROTATION_TYPE = "interacting";
	public static final String MIRROR_PLAYER_ROTATION_TYPE = "mirror-player";
	public static final String MIRROR_INTERACTING_ROTATION_TYPE = "mirror-interacting";

	public static final int PLAYER_TILE_HISTORY_SIZE = 10;

	public static final int NOTIFICATION_QUEUE_MAX_SIZE = 200;
	public static final int END_NOTIFICATION_GRACE_PERIOD_MS = 7000; // keep it high due to internal delays
	public static final String NONE_NOTIFICATION_MESSAGE_TYPE = "none";
	public static final String CHAT_NOTIFICATION_MESSAGE_TYPE = "chat";
	public static final String OVERHEAD_NOTIFICATION_MESSAGE_TYPE = "overhead";
	public static final String POPUP_NOTIFICATION_MESSAGE_TYPE = "popup";
	public static final String TWITCH_CHAT_NOTIFICATION_MESSAGE_TYPE = "twitch-chat";

	public static final String POPUP_NOTIFICATION_TITLE = "Live Loadout";

	public static final String TILE_MARKER_NOTIFICATION_MESSAGE_TYPE = "tile-marker";

	public static final int WIDGET_EFFECT_MAX_SIZE = 100;
	public static final int MENU_EFFECT_MAX_SIZE = 100;
	public static final int SPAWN_OVERHEAD_EFFECT_MAX_SIZE = 100;
	public static final String DISABLE_MENU_OPTION_TYPE = "disable";
	public static final String DISABLE_INTERFACE_WIDGET_TYPE = "disable";
	public static final String ALTER_INTERFACE_WIDGET_TYPE = "alter";
	public static final String OVERLAY_INTERFACE_WIDGET_TYPE = "overlay";

	// NOTE: keep in sync with front-end!
	public static final String NPC_MENU_ENTITY_TYPE = "npc";
	public static final String ITEM_MENU_ENTITY_TYPE = "item";
	public static final String WIDGET_MENU_ENTITY_TYPE = "widget";
	public static final String PLAYER_MENU_ENTITY_TYPE = "player";
	public static final String OBJECT_MENU_ENTITY_TYPE = "object";

	public static final int MAX_SPAWN_AMOUNT = 500;
	public static final String INDIVIDUAL_SPAWN_POINT_TYPE = "individual";
	public static final String GROUP_SPAWN_POINT_TYPE = "group";
	public static final int MAX_MODEL_SCALE = 500;
	public static final int MIN_MODEL_TRANSLATE = -1024;
	public static final int MAX_MODEL_TRANSLATE = 1024;

	public static final String PRODUCT_STATE_TYPE = "product";
	public static final String OBJECT_STATE_TYPE = "object";

	public static final String STRING_STATE_FORMAT = "string";
	public static final String INTEGER_STATE_FORMAT = "integer";

	public static final String SET_STATE_OPERATION = "set";
	public static final String ADD_STATE_OPERATION = "add";
	public static final String SUBTRACT_STATE_OPERATION = "subtract";
	public static final String MULTIPLY_STATE_OPERATION = "multiply";
	public static final String DIVIDE_STATE_OPERATION = "divide";

	public static final String EQUAL_STATE_COMPARISON = "equal";
	public static final String LARGER_THAN_STATE_COMPARISON = "larger-than";
	public static final String LARGER_EQUAL_THAN_STATE_COMPARISON = "larger-equal-than";
	public static final String SMALLER_THAN_STATE_COMPARISON = "smaller-than";
	public static final String SMALLER_EQUAL_THAN_STATE_COMPARISON = "smaller-equal-than";

	public static final int MOVEMENT_EFFECT_MAX_SIZE = 100;
	public static final int TRANSMOG_EFFECT_MAX_SIZE = 100;

	public static final int CHAT_NOTIFICATION_LOCKED_MS = 1 * 1000;
	public static final int OVERHEAD_NOTIFICATION_PAUSE_MS = 1 * 1000;
	public static final int TILE_MARKER_NOTIFICATION_DURATION_MS = 0 * 1000;

	public static final int GLOBAL_PLAY_SOUND_THROTTLE_MS = 0;
	public static final int UNIQUE_PLAY_SOUND_THROTTLE_MS = 100;

	public static final String EVENT_SUB_DEFAULT_EBS_PRODUCT_ID = "all-notifications";

	public static final int MALE_GENDER_ID = 0;
	public static final int FEMALE_GENDER_ID = 1;
}
