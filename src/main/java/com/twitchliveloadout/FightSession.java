package net.runelite.client.plugins.twitchliveloadout;

import net.runelite.api.Actor;

import java.util.HashMap;

public class FightSession {
	private final Actor actor;

	private HashMap<FightStatisticEntry, FightStatistic> statistics = new HashMap();
	private int gameTickCounter = 0;
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

	public void addGameTicks(int amount)
	{
		gameTickCounter += amount;
	}

	public int getGameTickCounter()
	{
		return gameTickCounter;
	}

	public void finish()
	{
		finished = true;
	}

	public boolean isFinished()
	{
		return finished;
	}

	public long getLastUpdate()
	{
		long maxLastUpdate = 0;

		for (FightStatistic statistic : statistics.values())
		{
			long lastUpdate = statistic.getLastUpdate();

			if (lastUpdate > maxLastUpdate)
			{
				maxLastUpdate = lastUpdate;
			}
		}

		return maxLastUpdate;
	}
}
