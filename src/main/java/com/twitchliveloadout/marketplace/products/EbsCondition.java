package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.CHUNK_SIZE;

public class EbsCondition {
	public Integer varbitId = -1;
	public Integer varbitValue = -1;
	public Integer minTimeMs = 0;
	public Integer maxTimeMs = Integer.MAX_VALUE;
	public Double minTimePercentage = 0d;
	public Double maxTimePercentage = 1d;
	public Integer maxSpawnsInView = -1;
	public Integer maxSpawnsInViewRadius = CHUNK_SIZE;
	public Integer minSpawnsInView = -1;
	public Integer minSpawnsInViewRadius = CHUNK_SIZE;
	public Integer spawnInViewRadius = -1;
	public ArrayList<EbsCondition> or;
	public ArrayList<EbsCondition> and;
}
