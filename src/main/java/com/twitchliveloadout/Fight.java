package net.runelite.client.plugins.twitchliveloadout;

import net.runelite.api.Actor;
import net.runelite.api.NPC;

import java.util.HashMap;

import static net.runelite.client.plugins.twitchliveloadout.FightStateManager.ACTOR_NPC_TYPE;
import static net.runelite.client.plugins.twitchliveloadout.FightStateManager.ACTOR_PLAYER_TYPE;

public class Fight {
	private final Actor actor;
	private int gameTickCounter = 0;
	private HashMap<FightStatisticEntry, FightStatistic> statistics = new HashMap();

	public Fight(Actor actor)
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

	public boolean hasHadActivity()
	{
		for (FightStatistic statistic : statistics.values())
		{
			if (statistic.hasHadActivity())
			{
				return true;
			}
		}

		return false;
	}

	public Actor getActor()
	{
		return actor;
	}

	public String getActorType()
	{
		if (actor instanceof NPC)
		{
			return ACTOR_NPC_TYPE;
		}

		return ACTOR_PLAYER_TYPE;
	}

	public int getActorId()
	{
		if (actor instanceof NPC)
		{
			return ((NPC) actor).getId();
		}

		return -1;
	}

	public boolean isValid()
	{
		return true;
	}

	public void addGameTick()
	{
		gameTickCounter ++;
	}

	public int getGameTickCounter() {
		return gameTickCounter;
	}

	public void reset()
	{
		gameTickCounter = 0;
	}
}
