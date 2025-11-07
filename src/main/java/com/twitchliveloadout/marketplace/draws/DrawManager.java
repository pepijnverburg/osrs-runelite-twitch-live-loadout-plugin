package com.twitchliveloadout.marketplace.draws;

import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.MarketplaceEffectManager;
import com.twitchliveloadout.marketplace.products.EbsDrawFrame;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.callback.RenderCallback;

import java.util.Iterator;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.DRAW_EFFECT_MAX_SIZE;

@Slf4j
public class DrawManager extends MarketplaceEffectManager<EbsDrawFrame> implements RenderCallback {
    private final Client client;

    /**
     * Flags of the current state to optimise the draw call without any effect lookup required.
     * These flags are updated when effects are added or removed.
     */
    private boolean anyEffectsActive = false;
    private boolean hideOthers = false;
    private boolean hideOthers2D = false;
    private boolean hideLocalPlayer = false;
    private boolean hideLocalPlayer2D = false;
    private boolean hideNPCs = false;
    private boolean hideNPCs2D = false;
    private boolean hideProjectiles = false;

    public DrawManager(Client client)
    {
        super(DRAW_EFFECT_MAX_SIZE);
        this.client = client;
    }

    @Override
    public boolean addEntity(Renderable renderable, boolean drawingUI)
    {
        if (!anyEffectsActive)
        {
            return true;
        }

        if (renderable instanceof Player)
        {
            Player player = (Player) renderable;
            Player local = client.getLocalPlayer();

            if (player.getName() == null)
            {
                return true;
            }

            // Allow hiding local self in pvp, which is an established meta.
            // It is more advantageous than renderself due to being able to still render local player 2d
            if (player == local)
            {
                return !(drawingUI ? hideLocalPlayer2D : hideLocalPlayer);
            }

            return !(drawingUI ? hideOthers2D : hideOthers);
        }

        if (renderable instanceof NPC)
        {
            return !(drawingUI ? hideNPCs2D : hideNPCs);
        }

        if (renderable instanceof Projectile)
        {
            return !hideProjectiles;
        }

        return true;
    }

    private void updateState() {
        anyEffectsActive = hasAnyEffectsActive();

        // by default reset all the states to false
        // only active effects upon later check can set them to true again
        hideOthers = false;
        hideOthers2D = false;
        hideLocalPlayer = false;
        hideLocalPlayer2D = false;
        hideNPCs = false;
        hideNPCs2D = false;
        hideProjectiles = false;

        // guard: skip checks when nothing is active
        if (!anyEffectsActive) {
            return;
        }

        Iterator<MarketplaceEffect<EbsDrawFrame>> effectIterator = effects.iterator();

        while (effectIterator.hasNext()) {
            MarketplaceEffect<EbsDrawFrame> effect = effectIterator.next();
            EbsDrawFrame frame = effect.getFrame();

            // guard: skip inactive effects
            if (!effect.isActive() || !effect.isApplied())
            {
                continue;
            }

            if (frame.hideOthers) {
                hideOthers = true;
            }

            if (frame.hideOthers2D) {
                hideOthers2D = true;
            }

            if (frame.hideLocalPlayer) {
                hideLocalPlayer = true;
            }

            if (frame.hideLocalPlayer2D) {
                hideLocalPlayer2D = true;
            }

            if (frame.hideNPCs) {
                hideNPCs = true;
            }

            if (frame.hideNPCs2D) {
                hideNPCs2D = true;
            }

            if (frame.hideProjectiles) {
                hideProjectiles = true;
            }
        }
    }

    @Override
    protected void onAddEffect(MarketplaceEffect<EbsDrawFrame> effect) {
        updateState();
    }

    @Override
    protected void onDeleteEffect(MarketplaceEffect<EbsDrawFrame> effect) {
        updateState();
    }

    @Override
    protected void restoreEffect(MarketplaceEffect<EbsDrawFrame> effect) {
        updateState();
    }

    @Override
    protected void applyEffect(MarketplaceEffect<EbsDrawFrame> effect) {
        updateState();
    }
}
