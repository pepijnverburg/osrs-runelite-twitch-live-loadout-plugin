package com.twitchliveloadout.marketplace.animations;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.products.EbsMovementAnimations;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;

import java.util.HashMap;

@Slf4j
public class AnimationManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final Client client;

	@Setter
	@Getter
	private EbsMovementAnimations currentMovementAnimations;

	private final HashMap<ActorAnimation, Integer> originalPlayerMovementAnimations = new HashMap<>();

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
