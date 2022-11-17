package com.twitchliveloadout.marketplace.products;

import com.twitchliveloadout.marketplace.MarketplaceModel;
import com.twitchliveloadout.marketplace.MarketplaceProduct;

public class GoldenGnomeProduct extends MarketplaceProduct
{
	public void onInitializeProduct()
	{
		setMarketplaceModels(new MarketplaceModel[][] {{
			new MarketplaceModel(32303),
		}});
	}
}
