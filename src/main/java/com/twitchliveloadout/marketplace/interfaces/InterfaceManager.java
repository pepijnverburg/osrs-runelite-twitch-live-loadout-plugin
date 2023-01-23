package com.twitchliveloadout.marketplace.interfaces;

import com.twitchliveloadout.marketplace.products.EbsInterfaceFrame;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public abstract class InterfaceManager<FrameType extends EbsInterfaceFrame> {

	/**
	 * The maximum amount of effects allowed to be active at once.
	 */
	private final int maxEffectAmount;

	/**
	 * Tracking of all the effects by any widget frames from any product. They are stored here and not in the
	 * marketplace products because they can also originate from periodic effects, where the marketplace products
	 * are unaware of and have no way of tracking.
	 */
	protected final CopyOnWriteArrayList<InterfaceEffect<FrameType>> effects = new CopyOnWriteArrayList();

	public InterfaceManager(int maxEffectAmount)
	{
		this.maxEffectAmount = maxEffectAmount;
	}

	public void onGameTick()
	{
		cleanEffects();
		applyEffects();
	}

	public void addEffect(MarketplaceProduct product, FrameType frame)
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
		Instant expiresAt = Instant.now().plusMillis(product.getExpiresInMs());

		if (durationMs != null && durationMs >= 0)
		{
			expiresAt = Instant.now().plusMillis(durationMs);
		}

		log.info("Adding new interface effect from EBS product ID: "+ product.getEbsProduct().id);

		// register the new effect
		InterfaceEffect effect = new InterfaceEffect(product, frame, expiresAt);
		effects.add(effect);
		onAddEffect(effect);
	}

	private void cleanEffects()
	{
		Iterator effectIterator = effects.iterator();

		while (effectIterator.hasNext())
		{
			InterfaceEffect effect = (InterfaceEffect) effectIterator.next();
			MarketplaceProduct marketplaceProduct = effect.getMarketplaceProduct();
			boolean isExpired = effect.isExpired() || marketplaceProduct.isExpired();
			boolean isActive = marketplaceProduct.isActive();

			// check if we should remove this active widget frame
			if (isExpired)
			{

				// remove from the active widgets
				effects.remove(effect);
				onDeleteEffect(effect);

				// always restore the widget without the need to check
				// if other effect still change this widget, because
				// on the next apply cycle these effects change the widget
				restoreEffect(effect);
			}

			// check if we should only restore the widget for now, because the marketplace product is inactive
			if (!isActive)
			{
				restoreEffect(effect);
			}
		}
	}

	private void applyEffects()
	{
		Iterator effectIterator = effects.iterator();

		while (effectIterator.hasNext())
		{
			InterfaceEffect effect = (InterfaceEffect) effectIterator.next();
			MarketplaceProduct marketplaceProduct = effect.getMarketplaceProduct();
			boolean isActive = marketplaceProduct.isActive();

			// guard: make sure the product is active
			if (!isActive)
			{
				continue;
			}

			applyEffect(effect);
		}
	}

	protected abstract void onAddEffect(InterfaceEffect effect);
	protected abstract void onDeleteEffect(InterfaceEffect effect);
	protected abstract void restoreEffect(InterfaceEffect effect);
	protected abstract void applyEffect(InterfaceEffect effect);
}
