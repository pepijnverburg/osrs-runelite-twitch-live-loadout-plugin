package com.twitchliveloadout.marketplace;

import com.twitchliveloadout.marketplace.products.EbsEffectFrame;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.spawns.SpawnedObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;

import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public abstract class MarketplaceEffectManager<FrameType extends EbsEffectFrame> {

	/**
	 * The maximum amount of effects allowed to be active at once.
	 */
	private final int maxEffectAmount;

	/**
	 * Tracking of all the effects by any widget frames from any product. They are stored here and not in the
	 * marketplace products because they can also originate from periodic effects, where the marketplace products
	 * are unaware of and have no way of tracking.
	 */
	protected final CopyOnWriteArrayList<MarketplaceEffect<FrameType>> effects = new CopyOnWriteArrayList<>();

	public MarketplaceEffectManager(int maxEffectAmount)
	{
		this.maxEffectAmount = maxEffectAmount;
	}

	public void updateEffects()
	{
		cleanInactiveEffects();
		applyActiveEffects();
	}

	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		GameState newGameState = gameStateChanged.getGameState();

		// apply all effects to handle teleports and such triggering a loading screen
		if (newGameState == GameState.LOGGED_IN)
		{
			applyActiveEffects();
		}
	}

	public void addEffect(MarketplaceProduct product, FrameType frame, SpawnedObject spawnedObject)
	{

		// guard: make sure the maximum is not exceeded for security and performance reasons
		if (effects.size() >= maxEffectAmount)
		{
			return;
		}

		// guard: make sure the widget frame is valid
		if (product == null || frame == null)
		{
			return;
		}

		// by default the expiry time is linked to the product, but can be overridden with a custom duration
		Integer durationMs = frame.durationMs;
		Double durationPercentage = frame.durationPercentage;
		long productExpiresInMs = product.getExpiresInMs();
		Instant expiresAt = Instant.now().plusMillis(productExpiresInMs);

		// override duration if there is a custom duration
		if (durationMs != null && durationMs >= 0)
		{
			expiresAt = Instant.now().plusMillis(durationMs);
		}
		else if (durationPercentage != null && durationPercentage >= 0)
		{
			long durationMsFromPercentage = (long) (((double) productExpiresInMs) * durationPercentage);
			expiresAt = Instant.now().plusMillis(durationMsFromPercentage);
		}

		// register the new effect
		MarketplaceEffect<FrameType> effect = new MarketplaceEffect<FrameType>(product, frame, spawnedObject, expiresAt);
		effects.add(effect);
		onAddEffect(effect);
	}

	protected void cleanInactiveEffects()
	{
		cleanEffects(false);
	}

	public void forceCleanAllEffects()
	{
		cleanEffects(true);
	}

	private void cleanEffects(boolean forceStop)
	{
		Iterator<MarketplaceEffect<FrameType>> effectIterator = effects.iterator();

		while (effectIterator.hasNext())
		{
			MarketplaceEffect<FrameType> effect = effectIterator.next();
			MarketplaceProduct marketplaceProduct = effect.getMarketplaceProduct();
			boolean isExpired = effect.isExpired() || marketplaceProduct.isExpired();
			boolean isActive = marketplaceProduct.isActive();
			boolean isPaused = marketplaceProduct.isPaused();
			boolean isApplied = effect.isApplied();

			// check if we should remove this effect
			if (isExpired || forceStop)
			{

				// remove from the current effects
				effects.remove(effect);
				onDeleteEffect(effect);

				// always restore the effect
				restoreEffect(effect);
				effect.setApplied(false);
			}

			// check if we should only restore the effect for now, because the marketplace product is inactive
			// we also don't restore when the effect is not applied anymore and the product is paused
			else if (!isActive && (isApplied || !isPaused))
			{
				restoreEffect(effect);
				effect.setApplied(false);
			}
		}
	}

	protected void applyActiveEffects()
	{
		Iterator<MarketplaceEffect<FrameType>> effectIterator = effects.iterator();

		while (effectIterator.hasNext())
		{
			MarketplaceEffect<FrameType> effect = effectIterator.next();
			MarketplaceProduct marketplaceProduct = effect.getMarketplaceProduct();
			boolean isActive = marketplaceProduct.isActive();

			// guard: make sure the product is active
			if (!isActive)
			{
				continue;
			}

			applyEffect(effect);
			effect.setApplied(true);
		}
	}

	protected abstract void onAddEffect(MarketplaceEffect<FrameType> effect);
	protected abstract void onDeleteEffect(MarketplaceEffect<FrameType> effect);
	protected abstract void restoreEffect(MarketplaceEffect<FrameType> effect);
	protected abstract void applyEffect(MarketplaceEffect<FrameType> effect);
}
