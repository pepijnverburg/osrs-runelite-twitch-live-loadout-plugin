package com.twitchliveloadout.fights;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;

import java.time.Instant;

@Slf4j
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

		log.debug("Queued statistic time until expiry: {}", (expiredAt.toEpochMilli() - now.toEpochMilli()));

		return !isRegistered && !now.isAfter(expiredAt);
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
