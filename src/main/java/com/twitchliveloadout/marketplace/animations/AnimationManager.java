package com.twitchliveloadout.marketplace.animations;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceEffect;
import com.twitchliveloadout.marketplace.MarketplaceEffectManager;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.products.EbsMovementFrame;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ActorSpotAnim;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PlayerChanged;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.MOVEMENT_EFFECT_MAX_SIZE;

@Slf4j
public class AnimationManager extends MarketplaceEffectManager<EbsMovementFrame> {
	private final TwitchLiveLoadoutPlugin plugin;
	private final Client client;

	private final ConcurrentHashMap<ActorAnimation, Integer> originalMovementAnimations = new ConcurrentHashMap<>();
	private Instant animationLockedUntil;
	private Instant graphicLockedUntil;

	public AnimationManager(TwitchLiveLoadoutPlugin plugin, Client client)
	{
		super(MOVEMENT_EFFECT_MAX_SIZE);

		this.plugin = plugin;
		this.client = client;
	}

	public void onGameTick()
	{
		cleanInactiveEffects();
	}

	public void onPlayerChanged(PlayerChanged playerChanged)
	{
		Player player = playerChanged.getPlayer();
		Player localPlayer = client.getLocalPlayer();
		boolean isLocalPlayer = (localPlayer == player);

		// guard: for now we only support local players
		if (!isLocalPlayer)
		{
			return;
		}

		recordOriginalMovementAnimations();
		applyActiveEffects();
	}

	@Override
	protected void applyEffect(MarketplaceEffect<EbsMovementFrame> effect)
	{
		Player player = client.getLocalPlayer();
		EbsMovementFrame movementFrame = effect.getFrame();

		plugin.runOnClientThread(() -> {
			for (ActorAnimation animation : ActorAnimation.values())
			{
				final Integer animationId = getCurrentMovementAnimation(animation, movementFrame);

				// fallback to the original when no animation is found
				if (animationId == null || animationId < 0)
				{
					return;
				}

				animation.setAnimation(player, animationId);
			}
		});
	}

	@Override
	protected void restoreEffect(MarketplaceEffect<EbsMovementFrame> effect)
	{
		Player player = client.getLocalPlayer();

		// update to originals
		plugin.runOnClientThread(() -> {
			for (ActorAnimation animation : ActorAnimation.values())
			{

				// guard: make sure the animation is known
				if (!originalMovementAnimations.containsKey(animation))
				{
					continue;
				}

				int originalAnimationId = originalMovementAnimations.get(animation);
				animation.setAnimation(player, originalAnimationId);
			}
		});

		// after the original is restored there might be another one right up in the effect queue
		applyActiveEffects();
	}

	@Override
	protected void onAddEffect(MarketplaceEffect<EbsMovementFrame> effect)
	{
		// update immediately when effect is added
		// because this manager is not updating periodically, but event based
		applyActiveEffects();

		// check whether we should record the original movements for the first time
		// this is needed because it is possible a onPlayerChanged event was not yet triggered after logging in
		if (originalMovementAnimations.size() <= 0)
		{
			recordOriginalMovementAnimations();
		}
	}

	@Override
	protected void onDeleteEffect(MarketplaceEffect<EbsMovementFrame> effect)
	{
		// empty
	}

	private void recordOriginalMovementAnimations()
	{
		Player player = client.getLocalPlayer();

		originalMovementAnimations.clear();
		for (ActorAnimation animation : ActorAnimation.values())
		{
			originalMovementAnimations.put(animation, animation.getAnimation(player));
		}
	}

	public void setPlayerGraphic(int graphicKey, int graphicId, int graphicHeight, long delayMs, long durationMs)
	{
		handleLockedPlayerEffect(
			delayMs,
			durationMs,
			() -> graphicLockedUntil,
			() -> {
				// locking the player graphic is not needed anymore because multiple graphics (spot anims)
				// can be spawned on the player at once now! consider removing this completely with a next update
				// graphicLockedUntil = Instant.now().plusMillis(durationMs);
			},
			(player) -> {
				player.createSpotAnim(graphicKey, graphicId, graphicHeight, 0);
			}
		);
	}

	public void resetPlayerGraphic(int graphicKey, int delayMs)
	{
		handleLocalPlayer((player) -> {
			plugin.scheduleOnClientThread(() -> {
				player.removeSpotAnim(graphicKey);
			}, delayMs);
		});
	}

	public void setPlayerAnimation(int animationId, long delayMs, long durationMs)
	{
		handleLockedPlayerEffect(
			delayMs,
			durationMs,
			() -> animationLockedUntil,
			() -> {
				animationLockedUntil = Instant.now().plusMillis(durationMs);
			},
			(player) -> {
				player.setAnimationFrame(0);
				player.setAnimation(animationId);
			}
		);
	}

	public void resetPlayerAnimation(int delayMs)
	{
		handleLocalPlayer((player) -> {
			plugin.scheduleOnClientThread(() -> {
				player.setAnimation(-1);
				player.setAnimationFrame(0);
			}, delayMs);
		});
	}

	private void handleLockedPlayerEffect(long delayMs, long durationMs, MarketplaceManager.GetTimeHandler getLockedUntil, MarketplaceManager.EmptyHandler updateLockHandler, MarketplaceManager.PlayerHandler playerHandler)
	{
		handleLocalPlayer((player) -> {
			plugin.scheduleOnClientThread(() -> {
				Instant lockedUntil = getLockedUntil.execute();
				boolean isLocked = (lockedUntil != null && Instant.now().isBefore(lockedUntil));

				// guard: skip the request if we are not yet done with animating the previous one
				if (isLocked)
				{
					return;
				}

				updateLockHandler.execute();
				playerHandler.execute(player);
			}, delayMs);
		});
	}

	private void handleLocalPlayer(MarketplaceManager.PlayerHandler playerHandler)
	{
		Player player = client.getLocalPlayer();

		// guard: make sure the player is valid
		if (player == null)
		{
			return;
		}

		playerHandler.execute(player);
	}

	public int getCurrentMovementAnimation(ActorAnimation animation, EbsMovementFrame movementFrame)
	{
		int animationId = -1;
		int fallbackAnimationId = -1;

		if (movementFrame == null)
		{
			return animationId;
		}

		switch(animation)
		{
			case RUN:
				animationId = movementFrame.run;
				fallbackAnimationId = movementFrame.walk;
			 	break;
			case IDLE:
				animationId = movementFrame.idle;
				break;
			case IDLE_ROTATE_LEFT:
				animationId = movementFrame.idleRotateLeft;
				fallbackAnimationId = movementFrame.idle;
				break;
			case IDLE_ROTATE_RIGHT:
				animationId = movementFrame.idleRotateRight;
				fallbackAnimationId = movementFrame.idle;
				break;
			case WALK:
				animationId = movementFrame.walk;
				break;
			case WALK_ROTATE_180:
				animationId = movementFrame.walkRotate180;
				fallbackAnimationId = movementFrame.walk;
				break;
			case WALK_ROTATE_LEFT:
				animationId = movementFrame.walkRotateLeft;
				fallbackAnimationId = movementFrame.walk;
				break;
			case WALK_ROTATE_RIGHT:
				animationId = movementFrame.walkRotateRight;
				fallbackAnimationId = movementFrame.walk;
				break;
		}

		// guard: check if we need to use the fallback
		if (animationId < 0 && fallbackAnimationId >= 0)
		{
			return fallbackAnimationId;
		}

		return animationId;
	}
}
