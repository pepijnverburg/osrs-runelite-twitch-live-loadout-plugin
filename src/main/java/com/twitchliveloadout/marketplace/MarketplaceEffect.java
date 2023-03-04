package com.twitchliveloadout.marketplace;

import com.twitchliveloadout.marketplace.products.EbsEffectFrame;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Generic class to handle applying and reverting marketplace effects, such as interface, menu, animation or equipment transmog effects.
 */
public class MarketplaceEffect<K extends EbsEffectFrame> {

	/**
	 * The marketplace product where the effect originates from.
	 */
	@Getter
	private final MarketplaceProduct marketplaceProduct;

	/**
	 * Reference to the original effect properties where at minimum it contains an optional duration.
	 */
	@Getter
	private final K frame;

	/**
	 * The expiry based on either the custom duration or the one of the product.
	 */
	private final Instant expiresAt;

	/**
	 * Boolean to identify whether the effect is currently applied yes or no.
	 * This helps to filter out re-triggering of reverting the effect over and over again
	 * while it is already reverted. With some effects this impacts the user experience if it does otherwise.
	 */
	@Getter
	@Setter
	private boolean isApplied = false;

	public MarketplaceEffect(MarketplaceProduct marketplaceProduct, K frame, Instant expiresAt)
	{
		this.marketplaceProduct = marketplaceProduct;
		this.frame = frame;
		this.expiresAt = expiresAt;
	}

	public boolean isActive()
	{
		return !isExpired() && marketplaceProduct.isActive();
	}

	public boolean isExpired()
	{
		return Instant.now().isAfter(expiresAt) || marketplaceProduct.isExpired();
	}
}
