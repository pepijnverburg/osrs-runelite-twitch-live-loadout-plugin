package com.twitchliveloadout;

import net.runelite.api.Actor;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static com.twitchliveloadout.FightStateManager.*;

public class FightSession {
	private final Fight fight;
	private final Actor actor;

	private ConcurrentHashMap<FightStatisticEntry, FightStatistic> statistics = new ConcurrentHashMap();
	private long interactingTickCounter = 0;
	private long idleTickCounter = 0;
	private long idleQueuedTickCounter = 0;
	private boolean finished = false;

	public FightSession(Fight fight, Actor actor)
	{
		this.fight = fight;
		this.actor = actor;

		for (FightStatisticEntry statisticEntry : FightStatisticEntry.values())
		{
			statistics.put(statisticEntry, new FightStatistic(this));
		}
	}

	public FightStatistic getStatistic(FightStatisticEntry statisticEntry)
	{
		return statistics.get(statisticEntry);
	}

	public void handleStatisticUpdate()
	{
		registerQueuedIdleTicks();
		fight.handleStatisticUpdate();
	}

	public void addInteractingTicks(long amount)
	{
		interactingTickCounter += amount;
	}

	public void addIdleTicks(long amount)
	{
		idleTickCounter += amount;
	}

	public void queueIdleTicks(long amount)
	{
		idleQueuedTickCounter += amount;
	}

	public void registerQueuedIdleTicks()
	{
		idleTickCounter += idleQueuedTickCounter;
		idleQueuedTickCounter = 0;
	}

	public long getInteractingTickCounter()
	{
		return interactingTickCounter;
	}

	public long getIdleTickCounter()
	{
		return idleTickCounter;
	}

	public long getIdleDuration()
	{
		return (long) (idleTickCounter * GAME_TICK_DURATION);
	}

	public long getDurationSeconds()
	{
		Instant lastUpdate = getLastUpdate();
		Instant firstUpdate = getFirstUpdate();

		if (lastUpdate == null || firstUpdate == null)
		{
			return 0;
		}

		return getLastUpdate().getEpochSecond() - getFirstUpdate().getEpochSecond() - getIdleDuration();
	}

	public void finish()
	{
		finished = true;
	}

	public boolean isFinished()
	{
		return finished;
	}

	public Instant getFirstUpdate()
	{
		Instant minFirstUpdate = null;

		for (Map.Entry<FightStatisticEntry, FightStatistic> entry : statistics.entrySet())
		{
			final FightStatisticEntry statisticEntry = entry.getKey();
			final FightStatistic statistic = entry.getValue();
			final Instant firstUpdate = statistic.getFirstUpdate();

			if (!statisticEntry.isDurationInfluencer() || firstUpdate == null)
			{
				continue;
			}

			if (minFirstUpdate == null || firstUpdate.isBefore(minFirstUpdate))
			{
				minFirstUpdate = firstUpdate;
			}
		}

		return minFirstUpdate;
	}

	public Instant getLastUpdate()
	{
		return getLastUpdate(false);
	}

	public Instant getLastUpdate(boolean updatedAtInfluencerOnly)
	{
		Instant maxLastUpdate = null;

		for (Map.Entry<FightStatisticEntry, FightStatistic> entry : statistics.entrySet())
		{
			final FightStatisticEntry statisticEntry = entry.getKey();
			final FightStatistic statistic = entry.getValue();
			final Instant lastUpdate = statistic.getLastUpdate();

			if (!statisticEntry.isDurationInfluencer() || lastUpdate == null)
			{
				continue;
			}

			if (!statisticEntry.isUpdatedAtInfluencer() && updatedAtInfluencerOnly)
			{
				continue;
			}

			if (maxLastUpdate == null || lastUpdate.isAfter(maxLastUpdate))
			{
				maxLastUpdate = lastUpdate;
			}
		}

		return maxLastUpdate;
	}
}
