package com.twitchliveloadout.marketplace.products;

import com.twitchliveloadout.marketplace.*;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import net.runelite.api.ModelData;

public class GroundSpawningPortal extends MarketplaceProduct
{
	public GroundSpawningPortal(MarketplaceManager manager, TwitchTransaction transaction, EbsProduct ebsProduct, StreamerProduct streamerProduct) {
		super(manager, transaction, ebsProduct, streamerProduct);
	}

	public void onInitializeProduct()
	{
//		setMarketplaceModels(new MarketplaceModel[][] {{
//			new MarketplaceModel(42302, 9040)
//		}});
	}

	public void onInitializeModel(ModelData model, int modelId)
	{
//		MarketplaceModelUtilities.recolorAllFaces(model, MarketplaceModelConstants.ModelColors.PURPLE);
	}
}
