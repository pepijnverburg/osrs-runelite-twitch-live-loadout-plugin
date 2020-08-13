package net.runelite.client.plugins.twitchliveloadout;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.NPC;

import java.util.*;

@Slf4j
public class Fight {
	private final String actorName;
	private final int actorId;
	private final FightStateManager.ActorType actorType;
	private final int actorCombatLevel;
	private final ArrayList<FightQueuedStatistic> queuedStatistics = new ArrayList();

	private Actor lastActor;

	private int finishedSessionCounter = 0;

	private HashMap<Actor, FightSession> sessions = new HashMap();

	public Fight(Actor actor, boolean isLocalPlayer)
	{
		this.lastActor = actor;
		this.actorName = actor.getName();
		this.actorCombatLevel = actor.getCombatLevel();

		if (actor instanceof NPC)
		{
			actorId = ((NPC) actor).getId();
			actorType = FightStateManager.ActorType.NPC;
		}
		else if (isLocalPlayer)
		{
			actorId = -1;
			actorType = FightStateManager.ActorType.LOCAL_PLAYER;
		}
		else
		{
			actorId = -1;
			actorType = FightStateManager.ActorType.PLAYER;
		}

		ensureSession(actor);
	}

	public void queueStatistic(Actor actor, FightStatisticEntry entry, FightStatisticProperty property, int expiryTimeMs)
	{
		log.error("QUEUED: Adding {} - {} - {} - {}", actor.getName(), entry.getKey(), property.getKey(), expiryTimeMs);
		FightQueuedStatistic queuedStatistic = new FightQueuedStatistic(actor, entry, property, expiryTimeMs);
		queuedStatistics.add(queuedStatistic);
		cleanQueuedStatistics();
	}

	public void registerQueuedStatistics(int hitsplatAmount)
	{
		List list = Collections.synchronizedList(queuedStatistics);
		log.error("QUEUED: Checking queue, queued size {} - hitsplat {}", list.size(), hitsplatAmount);

		synchronized (list)
		{
			Iterator iterator = list.iterator();
			while (iterator.hasNext())
			{
				FightQueuedStatistic queuedStatistic = (FightQueuedStatistic) iterator.next();
				Actor actor = queuedStatistic.getActor();
				FightStatisticEntry entry = queuedStatistic.getEntry();
				FightStatisticProperty property = queuedStatistic.getProperty();
				FightStatistic statistic = ensureStatistic(actor, entry);

				log.error("QUEUED: Attempt register {} - {}", entry.getKey(), property.getKey());

				// Will prevent registering twice
				if (!queuedStatistic.isValid())
				{
					log.error("QUEUED: Skipping because of invalid.");
					continue;
				}

				if (property == FightStatisticProperty.MISS_DAMAGES || property == FightStatisticProperty.MISS_COUNTERS)
				{
					log.error("QUEUED: Register miss");
					statistic.registerMiss(hitsplatAmount);
				}
				else if (property == FightStatisticProperty.HIT_DAMAGES || property == FightStatisticProperty.HIT_COUNTERS)
				{
					log.error("QUEUED: register hit ");
					statistic.registerHit(hitsplatAmount);
				}

				// Flag to clean up later
				queuedStatistic.register();
			}
		}
	}

	private void cleanQueuedStatistics()
	{
		Iterator<FightQueuedStatistic> iterator = queuedStatistics.iterator();

		while (iterator.hasNext()) {
			FightQueuedStatistic queuedStatistic = iterator.next();

			if (queuedStatistic.isValid()) {
				continue;
			}

			log.error("Remove from queue {}", queuedStatistic.getEntry().getKey());
			iterator.remove();
		}
	}

	public FightStatistic ensureStatistic(Actor actor, FightStatisticEntry statisticEntry)
	{

		if (!sessions.containsKey(actor))
		{
			ensureSession(actor);
		}

		FightSession session = sessions.get(actor);

		return session.getStatistic(statisticEntry);
	}

	public boolean hasSession(Actor actor)
	{
		return sessions.containsKey(actor);
	}

	public FightSession getSession(Actor actor)
	{
		return sessions.get(actor);
	}

	public FightSession ensureSession(Actor actor)
	{
		if (sessions.containsKey(actor))
		{
			return sessions.get(actor);
		}

		FightSession session = new FightSession(actor);
		sessions.put(actor, session);

		return session;
	}

	public long getLastUpdate()
	{
		long maxLastUpdate = 0;

		for (FightSession session : sessions.values())
		{
			long lastUpdate = session.getLastUpdate();

			if (lastUpdate > maxLastUpdate)
			{
				maxLastUpdate = lastUpdate;
			}
		}

		return maxLastUpdate;
	}

	public FightSession calculateTotalSession()
	{
		FightSession totalSession = new FightSession(lastActor);

		for (FightSession session : sessions.values())
		{
			totalSession.addGameTicks(session.getGameTickCounter());

			for (FightStatisticEntry statisticEntry : FightStatisticEntry.values())
			{
				FightStatistic totalStatistic = totalSession.getStatistic(statisticEntry);
				FightStatistic statistic = session.getStatistic(statisticEntry);

				totalStatistic.addStatistic(statistic);
			}
		}

		return totalSession;
	}

	public FightSession getLastSession()
	{
		return ensureSession(lastActor);
	}

	public void setLastActor(Actor actor)
	{
		this.lastActor = actor;
	}

	public void finishSession(Actor actor)
	{
		FightSession session = ensureSession(actor);

		session.finish();
		finishedSessionCounter ++;
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

	public int getFinishedSessionCounter()
	{
		return finishedSessionCounter;
	}
}
