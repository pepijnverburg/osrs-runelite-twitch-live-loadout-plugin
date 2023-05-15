package com.twitchliveloadout.marketplace.spawns;

import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.MarketplaceEffectManager;
import com.twitchliveloadout.marketplace.products.EbsModelOverheadFrame;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.OverlayManager;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

@Slf4j
public class SpawnOverheadManager extends MarketplaceEffectManager<EbsModelOverheadFrame> {
	private final OverlayManager overlayManager;
	private final OverheadTextOverlay overheadTextOverlay;

	public SpawnOverheadManager(Client client, OverlayManager overlayManager)
	{
		super(SPAWN_OVERHEAD_EFFECT_MAX_SIZE);

		this.overlayManager = overlayManager;
		this.overheadTextOverlay = new OverheadTextOverlay(client);

		// initialize the overlay
		overlayManager.add(overheadTextOverlay);
	}

	public void onGameTick()
	{
		updateEffects();
	}

	@Override
	protected void applyEffect(MarketplaceEffect<EbsModelOverheadFrame> effect)
	{
		String text = effect.getFrame().text;

		// guard: skip when already applied
		if (effect.isApplied())
		{
			return;
		}

		// add to the overlay when there is a text
		if (text != null && !text.isEmpty())
		{
			overheadTextOverlay.addEffect(effect);
		}
	}

	@Override
	protected void restoreEffect(MarketplaceEffect<EbsModelOverheadFrame> effect)
	{

		// guard: skip when already restored
		if (!effect.isApplied())
		{
			return;
		}

		// always attempt to remove the effect
		overheadTextOverlay.removeEffect(effect);
	}

	@Override
	protected void onAddEffect(MarketplaceEffect<EbsModelOverheadFrame> effect)
	{
		// empty
	}

	@Override
	protected void onDeleteEffect(MarketplaceEffect<EbsModelOverheadFrame> effect)
	{
		// empty
	}

	public void removeOverlay()
	{
		overlayManager.remove(overheadTextOverlay);
	}
}

