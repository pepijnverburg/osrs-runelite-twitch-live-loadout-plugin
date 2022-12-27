package com.twitchliveloadout.marketplace.notifications;

import com.twitchliveloadout.marketplace.products.EbsNotification;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;

public class Notification {
	public final MarketplaceProduct marketplaceProduct;
	public final EbsNotification ebsNotification;

	public Notification(MarketplaceProduct marketplaceProduct, EbsNotification ebsNotification)
	{
		this.marketplaceProduct = marketplaceProduct;
		this.ebsNotification = ebsNotification;
	}
}
