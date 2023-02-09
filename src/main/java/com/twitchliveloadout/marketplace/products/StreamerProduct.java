package com.twitchliveloadout.marketplace.products;

public class StreamerProduct {
	public String id;
	public String ebsProductId;
	public String twitchProductSku;
	public String name;
	public Integer duration = 10; // minimum of 10 seconds as a fallback
	public Integer cooldown = 0; // no cooldown in seconds by default
}
