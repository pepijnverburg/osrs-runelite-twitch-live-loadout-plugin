package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

public class EbsModelPlacement {
	public String locationType = CURRENT_TILE_LOCATION_TYPE;
	public String followType = NONE_FOLLOW_TYPE;
	public ArrayList<EbsCondition> followConditions;
	public String radiusType = OUTWARD_RADIUS_TYPE;
	public Boolean inLineOfSight = false;
	public EbsRandomRange radiusRange = new EbsRandomRange(DEFAULT_MIN_RADIUS, DEFAULT_MAX_RADIUS);
}
