package com.twitchliveloadout.marketplace.notifications;

import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.products.EbsNotification;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;

public class Notification {
	public final MarketplaceProduct marketplaceProduct;
	public final MarketplaceEffect marketplaceEffect;
	public final EbsNotification ebsNotification;

	public Notification(MarketplaceProduct marketplaceProduct, MarketplaceEffect marketplaceEffect, EbsNotification ebsNotification)
	{
		this.marketplaceProduct = marketplaceProduct;
		this.marketplaceEffect = marketplaceEffect;
		this.ebsNotification = ebsNotification;
	}
}
