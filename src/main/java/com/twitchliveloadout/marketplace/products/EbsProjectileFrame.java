package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

public class EbsProjectileFrame extends EbsEffectFrame {
	public String startLocationType;
	public String endLocationType;
	public Boolean followEndLocation = true;
	public EbsRandomRange startLocationRadiusRange;
	public EbsRandomRange endLocationRadiusRange;
	public ArrayList<EbsSpawnOption> startSpawnOptions;
	public ArrayList<EbsSpawnOption> endSpawnOptions;
	public Boolean inLineOfSight = false;
	public Boolean avoidExistingSpawns = false;
	public Boolean avoidPlayerLocation = false;

	// defaults are based on cannonballs
	public Integer startZ = -170;
	public Integer slope = 2;
	public Integer startHeight = 11;
	public Integer endHeight = 140;
}
