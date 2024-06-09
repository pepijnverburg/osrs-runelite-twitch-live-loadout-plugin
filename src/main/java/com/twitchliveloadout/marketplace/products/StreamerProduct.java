package com.twitchliveloadout.marketplace.products;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.EXTENSION_BITS_TWITCH_PRODUCT_TYPE;

public class StreamerProduct {
	public String id;
	public String ebsProductId;
	public String twitchProductSku;
	public String twitchProductType = EXTENSION_BITS_TWITCH_PRODUCT_TYPE; // by default bits for backwards compatibility
	public String name;
	public Integer duration = 10; // minimum of 10 seconds as a fallback
	public Integer cooldown = 0; // no cooldown in seconds by default
}
