package net.runelite.client.plugins.twitchliveloadout;

import net.runelite.api.Actor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static net.runelite.client.plugins.twitchliveloadout.FightStateManager.*;

public class FightSession {
	private final Fight fight;
	private final Actor actor;

	private HashMap<FightStatisticEntry, FightStatistic> statistics = new HashMap();
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
		return getLastUpdate() - getFirstUpdate() - getIdleDuration();
	}

	public void finish()
	{
		finished = true;
	}

	public boolean isFinished()
	{
		return finished;
	}

	public long getFirstUpdate()
	{
		long minFirstUpdate = 0;

		for (Map.Entry<FightStatisticEntry, FightStatistic> entry : statistics.entrySet())
		{
			final FightStatisticEntry statisticEntry = entry.getKey();
			final FightStatistic statistic = entry.getValue();
			final long firstUpdate = statistic.getFirstUpdate();

			if (!statisticEntry.isDurationInfluencer() || firstUpdate == 0)
			{
				continue;
			}

			if (firstUpdate < minFirstUpdate || minFirstUpdate == 0)
			{
				minFirstUpdate = firstUpdate;
			}
		}

		return minFirstUpdate;
	}

	public long getLastUpdate()
	{
		return getLastUpdate(false);
	}

	public long getLastUpdate(boolean updatedAtInfluencerOnly)
	{
		long maxLastUpdate = 0;

		for (Map.Entry<FightStatisticEntry, FightStatistic> entry : statistics.entrySet())
		{
			final FightStatisticEntry statisticEntry = entry.getKey();
			final FightStatistic statistic = entry.getValue();
			final long lastUpdate = statistic.getLastUpdate();

			if (!statisticEntry.isDurationInfluencer() || lastUpdate == 0)
			{
				continue;
			}

			if (!statisticEntry.isUpdatedAtInfluencer() && updatedAtInfluencerOnly)
			{
				continue;
			}

			if (lastUpdate > maxLastUpdate)
			{
				maxLastUpdate = lastUpdate;
			}
		}

		return maxLastUpdate;
	}
}
