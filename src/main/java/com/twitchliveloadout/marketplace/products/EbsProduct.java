package com.twitchliveloadout.marketplace.products;

import java.time.Instant;

public class EbsProduct {
	public String id;
	public Integer version = 1;
	public Boolean enabled = true;
	public Boolean dangerous = false;
	public String category;
	public String name;
	public String description;
	public Integer fixedDurationMs;
	public EbsBehaviour behaviour;
	public final String loaded_at = Instant.now().toString();
}


