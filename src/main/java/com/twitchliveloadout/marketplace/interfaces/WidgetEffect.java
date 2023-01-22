package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.marketplace.products.EbsInterfaceWidgetFrame;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import lombok.Getter;

import java.time.Instant;

public class WidgetEffect {
	@Getter
	private final MarketplaceProduct marketplaceProduct;
	@Getter
	private final EbsInterfaceWidgetFrame interfaceWidgetFrame;
	private final Instant expiresAt;

	public WidgetEffect(MarketplaceProduct marketplaceProduct, EbsInterfaceWidgetFrame interfaceWidgetFrame, Instant expiresAt)
	{
		this.marketplaceProduct = marketplaceProduct;
		this.interfaceWidgetFrame = interfaceWidgetFrame;
		this.expiresAt = expiresAt;
	}

	public boolean isExpired()
	{
		return Instant.now().isAfter(expiresAt);
	}
}
