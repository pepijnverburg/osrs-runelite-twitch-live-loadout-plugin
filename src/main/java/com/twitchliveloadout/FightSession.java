package net.runelite.client.plugins.twitchliveloadout;

import net.runelite.api.Actor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static net.runelite.client.plugins.twitchliveloadout.FightStateManager.*;

public class FightSession {
	private final Actor actor;

	private HashMap<FightStatisticEntry, FightStatistic> statistics = new HashMap();
	private long interactingTickCounter = 0;
	private long idleTickCounter = 0;
	private boolean finished = false;

	public FightSession(Actor actor)
	{
		this.actor = actor;

		for (FightStatisticEntry statisticEntry : FightStatisticEntry.values())
		{
			statistics.put(statisticEntry, new FightStatistic());
		}
	}

	public FightStatistic getStatistic(FightStatisticEntry statisticEntry)
	{
		return statistics.get(statisticEntry);
	}

	public void addInteractingTicks(long amount)
	{
		interactingTickCounter += amount;
	}

	public void addIdleTicks(long amount)
	{
		idleTickCounter += amount;
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

	// TODO: the idling time is not yet correctly substracted from the total duration
	public boolean isIdling()
	{
		if (!ENABLE_SESSION_IDLING)
		{
			return false;
		}

		final Instant now = Instant.now();
		final Instant lastUpdate = Instant.ofEpochSecond(getLastUpdate());
		final boolean isIdling = now.isAfter(lastUpdate.plusMillis(SESSION_IDLING_TIME));

		return isIdling;
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
