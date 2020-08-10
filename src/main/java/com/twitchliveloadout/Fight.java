package net.runelite.client.plugins.twitchliveloadout;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;

import java.util.HashMap;

@Slf4j
public class Fight {
	private Actor lastActor;
	private final String actorName;
	private final int actorId;
	private final FightStateManager.ActorType actorType;
	private final int actorCombatLevel;
	private int gameTickCounter = 0;
	private int gameTickTotalCounter = 0;
	private int sessionCounter = 0;
	private HashMap<FightStatisticEntry, FightStatistic> statistics = new HashMap();

	public Fight(Actor actor)
	{
		this.lastActor = actor;
		this.actorName = actor.getName();
		this.actorCombatLevel = actor.getCombatLevel();

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

	public Actor getLastActor()
	{
		return lastActor;
	}

	public int getActorId()
	{
		return actorId;
	}

	public String getActorName()
	{
		return actorName;
	}

	public int getActorCombatLevel()
	{
		return actorCombatLevel;
	}

	public void setLastActor(Actor actor)
	{
		this.lastActor = actor;
	}

	public boolean isValid()
	{
		return true;
	}

	public void addGameTick()
	{
		gameTickTotalCounter ++;
		gameTickCounter ++;
	}

	public int getGameTickCounter() {
		return gameTickCounter;
	}

	public int getGameTickTotalCounter()
	{
		return gameTickTotalCounter;
	}

	public int getSessionCounter()
	{
		return sessionCounter;
	}

	public void resetSession()
	{
		gameTickCounter = 0;
		sessionCounter ++;

		for (FightStatistic statistic : statistics.values())
		{
			statistic.resetSession();
		}

		log.error("Resetting the fight session of actor {}, session counter is now on {}", actorName, sessionCounter);
	}

	public void reset()
	{
		for (FightStatistic statistic : statistics.values())
		{
			statistic.reset();
		}

		resetSession();

		sessionCounter = 0;
		gameTickTotalCounter = 0;

		log.debug("Resetting all fight data of actor {}", actorName);
	}
}
