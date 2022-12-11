package com.twitchliveloadout.marketplace.products;

import com.twitchliveloadout.marketplace.*;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;

public class GoldenGnomeProduct extends MarketplaceProduct
{
	public GoldenGnomeProduct(MarketplaceManager manager, TwitchTransaction transaction, EbsProduct ebsProduct, StreamerProduct streamerProduct) {
		super(manager, transaction, ebsProduct, streamerProduct);
	}

	public void onInitializeProduct()
	{

	}
}
