package com.twitchliveloadout.marketplace.products;

import com.twitchliveloadout.marketplace.MarketplaceModel;
import com.twitchliveloadout.marketplace.MarketplaceModelConstants;
import com.twitchliveloadout.marketplace.MarketplaceModelUtilities;
import com.twitchliveloadout.marketplace.MarketplaceProduct;
import net.runelite.api.ModelData;

public class GroundSpawningPortal extends MarketplaceProduct
{
	@Override
	public void onInitializeProduct()
	{
		setMarketplaceModels(new MarketplaceModel[][] {{
			new MarketplaceModel(42302, 9040)
		}});
	}

	@Override
	public void onInitializeModel(ModelData model, int modelId)
	{
		MarketplaceModelUtilities.recolorAllFaces(model, MarketplaceModelConstants.ModelColors.PURPLE);
	}
}
