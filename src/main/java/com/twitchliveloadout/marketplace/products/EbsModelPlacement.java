package com.twitchliveloadout.marketplace.products;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

public class EbsModelPlacement {
	public String locationType = CURRENT_TILE_LOCATION_TYPE;
	public String followType = NONE_FOLLOW_TYPE;
	public String radiusType = OUTWARD_RADIUS_TYPE;
	public EbsRandomRange radiusRange = new EbsRandomRange(0, DEFAULT_RADIUS);
}
