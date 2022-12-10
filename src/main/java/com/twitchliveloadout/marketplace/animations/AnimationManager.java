package com.twitchliveloadout.marketplace.animations;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;

import java.util.HashMap;

@Slf4j
public class AnimationManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final Client client;

	private final HashMap<ActorAnimation, Integer> naturalPlayerPoseAnimations = new HashMap<>();

	public AnimationManager(TwitchLiveLoadoutPlugin plugin, Client client)
	{
		this.plugin = plugin;
		this.client = client;
	}

	public void updateAnimations()
	{

		// guardK skip when not logged in
		if (!plugin.isLoggedIn())
		{
			return;
		}

		Player player = client.getLocalPlayer();

		for (ActorAnimation animation : ActorAnimation.values())
		{
//			Integer animationId = currentAnimationSet.getAnimation(animation.getType());
//			if (animationId == null) animationId = naturalPlayerPoseAnimations.get(animation.ordinal());
//			animation.setAnimation(player, animationId);
		}
	}

	public void recordNaturalPlayerPoseAnimations()
	{
		naturalPlayerPoseAnimations.clear();
		Player player = client.getLocalPlayer();

		for (ActorAnimation animation : ActorAnimation.values())
		{
			naturalPlayerPoseAnimations.put(animation, animation.getAnimation(player));
		}
	}
}
