package com.twitchliveloadout.marketplace.products;

import java.util.ArrayList;

public class EbsInterval {
	public Double chance = 1.0d;
	public Integer delayMs = 0;
	public Integer repeatAmount = -1; // infinite by default
	public Boolean triggerOnStart = false;
	public Integer startDelayMs = 0;
	public ArrayList<EbsCondition> conditions;
}
