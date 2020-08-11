package net.runelite.client.plugins.twitchliveloadout;

import net.runelite.api.Actor;

import java.util.HashMap;
import java.util.Map;

public class FightSession {
	private final Actor actor;

	private HashMap<FightStatisticEntry, FightStatistic> statistics = new HashMap();
	private long gameTickCounter = 0;
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

	public void addGameTicks(long amount)
	{
		gameTickCounter += amount;
	}

	public long getGameTickCounter()
	{
		return gameTickCounter;
	}

	public long getDurationSeconds()
	{
		return getLastUpdate() - getFirstUpdate();
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

			if (lastUpdate > maxLastUpdate)
			{
				maxLastUpdate = lastUpdate;
			}
		}

		return maxLastUpdate;
	}
}
