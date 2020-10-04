package net.runelite.client.plugins.twitchliveloadout;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;

import java.util.*;

@Slf4j
public class Fight {
	private final String actorName;
	private final int actorId;
	private final FightStateManager.ActorType actorType;
	private final int actorCombatLevel;
	private final ArrayList<FightQueuedStatistic> queuedStatistics = new ArrayList();
	private final HashMap<Actor, FightSession> sessions = new HashMap();
	private final ArrayList<FightSession> finishedSessions = new ArrayList();

	private Actor lastActor;
	private FightSession lastSession;

	public Fight(Actor actor, boolean isLocalPlayer)
	{
		this.lastActor = actor;
		this.lastSession = ensureSession(actor);
		this.actorCombatLevel = actor.getCombatLevel();

		// Remove any HTML-like tags from the actor name, this is the case
		// for example with objects getting a <col=00ffff>name</col> tag
		this.actorName = actor.getName().replaceAll("\\<[^>]*>","");

		if (actor instanceof NPC)
		{
			actorId = ((NPC) actor).getId();
			actorType = FightStateManager.ActorType.NPC;
		}
		else if (actor instanceof GameObject)
		{
			actorId = ((GameObject) actor).getId();
			actorType = FightStateManager.ActorType.GAME_OBJECT;
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
	}

	public void queueStatistic(Actor actor, FightStatisticEntry entry, FightStatisticProperty property, int expiryTimeMs)
	{
		log.debug("QUEUED: Adding {} - {} - {} - {}", actor.getName(), entry.getKey(), property.getKey(), expiryTimeMs);
		FightQueuedStatistic queuedStatistic = new FightQueuedStatistic(actor, entry, property, expiryTimeMs);
		queuedStatistics.add(queuedStatistic);
		cleanQueuedStatistics();
	}

	public void registerQueuedStatistics(Actor actor, int hitsplatAmount)
	{
		log.debug("QUEUED: Checking queue, queued size {} - hitsplat {}", queuedStatistics.size(), hitsplatAmount);

		// synchronized list does not seem to be needed here?
		Iterator iterator = queuedStatistics.iterator();
		while (iterator.hasNext())
		{
			FightQueuedStatistic queuedStatistic = (FightQueuedStatistic) iterator.next();
			Actor queuedActor = queuedStatistic.getActor();
			FightStatisticEntry entry = queuedStatistic.getEntry();
			FightStatisticProperty property = queuedStatistic.getProperty();

			log.debug("QUEUED: Attempt register {} - {}", entry.getKey(), property.getKey());

			// Guard: check if this hitsplat is on the right actor
			if (actor != queuedActor)
			{
				log.debug("QUEUED: Skipping because of wrong actor.");
				continue;
			}

			// Will prevent registering twice
			if (!queuedStatistic.isValid())
			{
				log.debug("QUEUED: Skipping because of invalid.");
				continue;
			}

			FightStatistic statistic = ensureStatistic(actor, entry);

			if (property == FightStatisticProperty.MISS_DAMAGES || property == FightStatisticProperty.MISS_COUNTERS)
			{
				log.debug("QUEUED: Register miss");
				statistic.registerMiss(hitsplatAmount);
			}
			else if (property == FightStatisticProperty.HIT_DAMAGES || property == FightStatisticProperty.HIT_COUNTERS)
			{
				log.debug("QUEUED: register hit ");
				statistic.registerHit(hitsplatAmount);
			}

			// Flag to clean up later
			queuedStatistic.register();
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

			log.debug("QUEUED: Remove from queue {}", queuedStatistic.getEntry().getKey());
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
		return getLastUpdate(false);
	}

	public long getLastUpdate(boolean updatedAtInfluencerOnly)
	{
		long maxLastUpdate = 0;

		for (FightSession session : getAllSessions())
		{
			long lastUpdate = session.getLastUpdate(updatedAtInfluencerOnly);

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

		for (FightSession session : getAllSessions())
		{
			totalSession.addInteractingTicks(session.getInteractingTickCounter());
			totalSession.addIdleTicks(session.getIdleTickCounter());

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
		return lastSession;
	}

	public void setLastActor(Actor actor)
	{
		this.lastActor = actor;
		this.lastSession = ensureSession(actor);
	}

	public void finishSession(Actor actor)
	{
		if (!hasSession(actor))
		{
			return;
		}

		FightSession session = getSession(actor);

		session.finish();

		// After finishing a session make sure the session
		// gets its dedicated key so that no new stats are added
		sessions.remove(actor);
		finishedSessions.add(session);
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
		return finishedSessions.size();
	}

	public ArrayList<FightSession> getAllSessions()
	{
		ArrayList<FightSession> allSessions = new ArrayList();

		allSessions.addAll(finishedSessions);
		allSessions.addAll(sessions.values());

		return allSessions;
	}
}
