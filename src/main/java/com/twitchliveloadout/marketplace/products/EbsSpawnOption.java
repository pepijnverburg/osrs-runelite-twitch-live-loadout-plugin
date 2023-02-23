package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

public class EbsSpawnOption {
	public Double chance;
	public ArrayList<EbsCondition> conditions;
	public EbsRandomRange spawnAmount;
	public EbsRandomRange spawnDelayMs;
	public ArrayList<EbsSpawn> spawns;
}
