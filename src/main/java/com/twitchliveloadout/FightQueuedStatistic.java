package net.runelite.client.plugins.twitchliveloadout;

import net.runelite.api.Actor;

import java.time.Instant;

public class FightQueuedStatistic {
	private final Instant createdAt;
	private final Actor actor;
	private final FightStatisticEntry entry;
	private final FightStatisticProperty property;
	private final int expiryTimeMs;
	private boolean isRegistered = false;

	FightQueuedStatistic(Actor actor, FightStatisticEntry entry, FightStatisticProperty property, int expiryTimeMs)
	{
		this.createdAt = Instant.now();
		this.actor = actor;
		this.entry = entry;
		this.property = property;
		this.expiryTimeMs = expiryTimeMs;
	}

	public boolean isValid()
	{
		Instant now = Instant.now();
		Instant expiredAt = createdAt.plusMillis(expiryTimeMs);

		return !isRegistered && expiredAt.isAfter(now);
	}

	public void register()
	{
		isRegistered = true;
	}

	public Actor getActor()
	{
		return actor;
	}

	public FightStatisticEntry getEntry()
	{
		return entry;
	}

	public FightStatisticProperty getProperty()
	{
		return property;
	}
}
