package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

public class EbsSpawn {
	public ArrayList<EbsModelSet> modelSetOptions;
	public Boolean spawnerEnabled;
	public EbsModelSet spawner;
	public EbsRandomRange spawnerDurationMs;
	public EbsRandomRange spawnAmount;
	public EbsModelPlacement modelPlacement;
	public EbsMovementAnimations movementAnimations;
	public ArrayList<EbsVisualEffect> hideVisualEffects;
	public ArrayList<EbsVisualEffect> showVisualEffects;
	public ArrayList<ArrayList<EbsVisualEffect>> randomVisualEffectsOptions;
	public EbsInterval randomVisualEffectsInterval;
	public EbsRandomRange durationMs;
}
