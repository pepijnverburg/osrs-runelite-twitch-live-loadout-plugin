package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.marketplace.products.EbsInterfaceFrame;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import lombok.Getter;

import java.time.Instant;

public class InterfaceEffect<K extends EbsInterfaceFrame> {
	@Getter
	private final MarketplaceProduct marketplaceProduct;
	@Getter
	private final K frame;
	private final Instant expiresAt;

	public InterfaceEffect(MarketplaceProduct marketplaceProduct, K frame, Instant expiresAt)
	{
		this.marketplaceProduct = marketplaceProduct;
		this.frame = frame;
		this.expiresAt = expiresAt;
	}

	public boolean isExpired()
	{
		return Instant.now().isAfter(expiresAt);
	}
}
