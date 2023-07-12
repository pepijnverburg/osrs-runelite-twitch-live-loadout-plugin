package com.twitchliveloadout.marketplace.products;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

public class EbsStateFrame {
	public String type = PRODUCT_STATE_TYPE;
	public String format = STRING_STATE_FORMAT;
	public String operation = SET_STATE_OPERATION;
	public String key;
	public String value = null;
	public Integer delayMs = 0;
}
