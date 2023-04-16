package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

public class EbsSpawn {
	public ArrayList<EbsModelSet> modelSetOptions;
	public EbsRandomRange spawnAmount;
	public EbsModelPlacement modelPlacement = new EbsModelPlacement();
	public EbsModelPlacement modelRespawnPlacement;
	public EbsMovementFrame movementAnimations;
	public ArrayList<EbsEffect> hideEffects;
	public ArrayList<EbsEffect> showEffects;
	public ArrayList<ArrayList<EbsEffect>> randomEffectsOptions;
	public EbsInterval randomEffectsInterval;
	public EbsRandomRange durationMs;
}
