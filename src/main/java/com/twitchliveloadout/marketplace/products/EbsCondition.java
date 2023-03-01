package com.twitchliveloadout.marketplace.products;

import net.runelite.api.Constants;

public class EbsCondition {
	public Integer varbitId = -1;
	public Integer varbitValue = -1;
	public Integer minTimeMs = 0;
	public Integer maxTimeMs = Integer.MAX_VALUE;
	public Double minTimePercentage = 0d;
	public Double maxTimePercentage = 1d;
	public Integer maxSpawnsInView = -1;
	public Integer maxSpawnsInViewRadius = Constants.CHUNK_SIZE;
	public Integer minSpawnsInView = -1;
	public Integer minSpawnsInViewRadius = Constants.CHUNK_SIZE;
	public Integer spawnInViewRadius = -1;
}
