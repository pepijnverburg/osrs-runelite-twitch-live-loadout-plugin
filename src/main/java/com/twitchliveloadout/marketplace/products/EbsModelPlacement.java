package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

public class EbsModelPlacement {
	public String locationType = CURRENT_TILE_LOCATION_TYPE;
	public String followType = NONE_FOLLOW_TYPE;
	public ArrayList<EbsCondition> followConditions;
	public String radiusType = OUTWARD_RADIUS_TYPE;
	public Integer worldPointX;
	public Integer worldPointY;
	public Integer worldPointPlane;
	public Integer deltaPointX;
	public Integer deltaPointY;
	public Boolean inLineOfSight = false;
	public Boolean avoidExistingSpawns = true;
	public Boolean avoidPlayerLocation = true;
	public boolean avoidInvalidOverlay = true;
	public EbsRandomRange radiusRange = new EbsRandomRange(DEFAULT_MIN_RADIUS, DEFAULT_MAX_RADIUS);
	public Integer followRadius;
	public Integer radiusStepSize = 2;
	public String rotationType;
	public EbsRandomRange rotation;
	public EbsTranslation translation;
}
