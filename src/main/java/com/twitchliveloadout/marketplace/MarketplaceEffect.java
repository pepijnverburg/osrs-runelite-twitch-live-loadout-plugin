package com.twitchliveloadout.marketplace;

import com.twitchliveloadout.marketplace.products.EbsEffectFrame;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.spawns.SpawnedObject;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
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
	 * Optional spawned object this effect is applicable for.
	 */
	@Nullable
	@Getter
	private final SpawnedObject spawnedObject;

	/**
	 * The start time based on instancing of this class.
	 */
	private final Instant startedAt;

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

	public MarketplaceEffect(MarketplaceProduct marketplaceProduct, K frame, SpawnedObject spawnedObject, Instant expiresAt)
	{
		this.marketplaceProduct = marketplaceProduct;
		this.frame = frame;
		this.spawnedObject = spawnedObject;
		this.startedAt = Instant.now();
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

	/**
	 * Calculate how long in milliseconds this effect is going to be active
	 */
	public Duration getDuration()
	{
		return Duration.between(startedAt, expiresAt);
	}

	/**
	 * Calculate the amount of time in milliseconds this effect is still active
	 */
	public Duration getDurationLeft()
	{
		Instant now = Instant.now();

		return Duration.between(now, expiresAt);
	}
}
