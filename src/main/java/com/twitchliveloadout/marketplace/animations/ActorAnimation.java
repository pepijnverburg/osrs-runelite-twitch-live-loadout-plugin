package com.twitchliveloadout.marketplace.animations;

import lombok.Getter;
import net.runelite.api.Actor;

public enum ActorAnimation
{
	IDLE(Actor::getIdlePoseAnimation, Actor::setIdlePoseAnimation),
	IDLE_ROTATE_LEFT(Actor::getIdleRotateLeft, Actor::setIdleRotateLeft),
	IDLE_ROTATE_RIGHT(Actor::getIdleRotateRight, Actor::setIdleRotateRight),
	WALK(Actor::getWalkAnimation, Actor::setWalkAnimation),
	WALK_ROTATE_180(Actor::getWalkRotate180, Actor::setWalkRotate180),
	WALK_ROTATE_LEFT(Actor::getWalkRotateLeft, Actor::setWalkRotateLeft),
	WALK_ROTATE_RIGHT(Actor::getWalkRotateRight, Actor::setWalkRotateRight),
	RUN(Actor::getRunAnimation, Actor::setRunAnimation),
	;

	interface AnimationGetter
	{
		int getAnimation(Actor actor);
	}

	interface AnimationSetter
	{
		void setAnimation(Actor actor, int animationId);
	}

	@Getter
	private final AnimationGetter animationGetter;
	private final AnimationSetter animationSetter;

	ActorAnimation(AnimationGetter animationGetter, AnimationSetter animationSetter)
	{
		this.animationGetter = animationGetter;
		this.animationSetter = animationSetter;
	}

	public int getAnimation(Actor actor)
	{
		return animationGetter.getAnimation(actor);
	}

	public void setAnimation(Actor actor, int animationId)
	{
		animationSetter.setAnimation(actor, animationId);
	}
}
