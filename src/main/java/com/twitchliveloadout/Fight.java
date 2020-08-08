package net.runelite.client.plugins.twitchliveloadout;

import net.runelite.api.Actor;
import net.runelite.api.NPC;

import java.util.HashMap;

public class Fight {
	private final String actorName;
	private final int actorId;
	private final FightStateManager.ActorType actorType;
	private int gameTickCounter = 0;
	private HashMap<FightStatisticEntry, FightStatistic> statistics = new HashMap();

	public Fight(Actor actor)
	{
		this.actorName = actor.getName();

		if (actor instanceof NPC)
		{
			actorId = ((NPC) actor).getId();
			actorType = FightStateManager.ActorType.NPC;
		} else {
			actorId = -1;
			actorType = FightStateManager.ActorType.PLAYER;
		}

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

	public FightStateManager.ActorType getActorType()
	{
		return actorType;
	}

	public int getActorId()
	{
		return actorId;
	}

	public String getActorName()
	{
		return actorName;
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
