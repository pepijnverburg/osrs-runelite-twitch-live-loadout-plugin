package com.twitchliveloadout.marketplace.animations;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.products.EbsMovementAnimations;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class AnimationManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final Client client;

	@Setter
	@Getter
	private EbsMovementAnimations currentMovementAnimations;

	private final ConcurrentHashMap<ActorAnimation, Integer> originalPlayerMovementAnimations = new ConcurrentHashMap<>();

	private Instant animationLockedUntil;
	private Instant graphicLockedUntil;

	public AnimationManager(TwitchLiveLoadoutPlugin plugin, Client client)
	{
		this.plugin = plugin;
		this.client = client;
	}

	public void updateEffectAnimations()
	{
		Player player = client.getLocalPlayer();

		// guard: when no effect is active skip
		if (currentMovementAnimations == null)
		{
			return;
		}

		plugin.runOnClientThread(() -> {
			for (ActorAnimation animation : ActorAnimation.values())
			{
				final Integer animationId = getCurrentMovementAnimation(animation);

				// fallback to the original when no animation is found
				if (animationId == null || animationId < 0)
				{
					return;
				}

				animation.setAnimation(player, animationId);
			}
		});
	}

	public void recordOriginalAnimations()
	{
		originalPlayerMovementAnimations.clear();
		Player player = client.getLocalPlayer();

		for (ActorAnimation animation : ActorAnimation.values())
		{
			originalPlayerMovementAnimations.put(animation, animation.getAnimation(player));
		}
	}

	public void setPlayerGraphic(int graphicId, int graphicHeight, long delayMs, long durationMs)
	{
		handleLockedPlayerEffect(
			delayMs,
			durationMs,
			graphicLockedUntil,
			() -> {
				graphicLockedUntil = Instant.now().plusMillis(delayMs + durationMs);
			},
			(player) -> {
				player.setSpotAnimFrame(0);
				player.setGraphic(graphicId);
				player.setGraphicHeight(graphicHeight);
			}
		);
	}

	public void resetPlayerGraphic(int delayMs)
	{
		handleLocalPlayer((player) -> {
			plugin.scheduleOnClientThread(() -> {
				player.setGraphic(-1);
				player.setGraphicHeight(0);
			}, delayMs);
		});
	}

	public void setPlayerAnimation(int animationId, long delayMs, long durationMs)
	{
		handleLockedPlayerEffect(
			delayMs,
			durationMs,
			animationLockedUntil,
			() -> {
				animationLockedUntil = Instant.now().plusMillis(delayMs + durationMs);
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

	private void handleLockedPlayerEffect(long delayMs, long durationMs, Instant lockedUntil, MarketplaceManager.EmptyHandler updateLockHandler, MarketplaceManager.PlayerHandler playerHandler)
	{
		handleLocalPlayer((player) -> {
			boolean isLocked = (lockedUntil != null && Instant.now().isBefore(lockedUntil));

			// guard: skip the animation request if we are not yet done with animating the previous one
			if (isLocked)
			{
				return;
			}

			updateLockHandler.execute();
			plugin.scheduleOnClientThread(() -> {
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

	public void revertAnimations()
	{
		Player player = client.getLocalPlayer();

		// reset current animations
		currentMovementAnimations = null;

		// update to originals
		plugin.runOnClientThread(() -> {
			for (ActorAnimation animation : ActorAnimation.values())
			{

				// guard: make sure the animation is known
				if (!originalPlayerMovementAnimations.containsKey(animation))
				{
					continue;
				}

				int originalAnimationId = originalPlayerMovementAnimations.get(animation);
				animation.setAnimation(player, originalAnimationId);
			}
		});
	}

	public boolean isCurrentMovementAnimations(EbsMovementAnimations movementAnimations)
	{
		if (movementAnimations == null)
		{
			return false;
		}

		return movementAnimations.equals(currentMovementAnimations);
	}

	public int getCurrentMovementAnimation(ActorAnimation animation)
	{
		int animationId = -1;
		int fallbackAnimationId = -1;

		if (currentMovementAnimations == null)
		{
			return animationId;
		}

		switch(animation)
		{
			case RUN:
				animationId = currentMovementAnimations.run;
				fallbackAnimationId = currentMovementAnimations.walk;
			 	break;
			case IDLE:
				animationId = currentMovementAnimations.idle;
				break;
			case IDLE_ROTATE_LEFT:
				animationId = currentMovementAnimations.idleRotateLeft;
				fallbackAnimationId = currentMovementAnimations.idle;
				break;
			case IDLE_ROTATE_RIGHT:
				animationId = currentMovementAnimations.idleRotateRight;
				fallbackAnimationId = currentMovementAnimations.idle;
				break;
			case WALK:
				animationId = currentMovementAnimations.walk;
				break;
			case WALK_ROTATE_180:
				animationId = currentMovementAnimations.walkRotate180;
				fallbackAnimationId = currentMovementAnimations.walk;
				break;
			case WALK_ROTATE_LEFT:
				animationId = currentMovementAnimations.walkRotateLeft;
				fallbackAnimationId = currentMovementAnimations.walk;
				break;
			case WALK_ROTATE_RIGHT:
				animationId = currentMovementAnimations.walkRotateRight;
				fallbackAnimationId = currentMovementAnimations.walk;
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
