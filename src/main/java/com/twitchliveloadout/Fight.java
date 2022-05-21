package com.twitchliveloadout;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;

import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.twitchliveloadout.FightStateManager.MAX_FINISHED_FIGHT_SESSION_AMOUNT;

@Slf4j
public class Fight {
	private final String actorName;
	private final int actorId;
	private final FightStateManager.ActorType actorType;
	private final int actorCombatLevel;

	private final CopyOnWriteArrayList<FightQueuedStatistic> queuedStatistics = new CopyOnWriteArrayList();

	private final ConcurrentHashMap<Actor, FightSession> sessions = new ConcurrentHashMap();
	private final CopyOnWriteArrayList<FightSession> finishedSessions = new CopyOnWriteArrayList();

	// The session counter should not match the finished session list size,
	// because some finished sessions are not kills / deaths, separate counter for this.
	// For example when a fight expires due to time it should not register a kill / death.
	private int sessionCounter = 0;

	private long idleTickCounter = 0;
	private long idleQueuedTickCounter = 0;

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
		log.debug("Adding queued statistic: {} - {} - {} - {}", actor.getName(), entry.getKey(), property.getKey(), expiryTimeMs);
		FightQueuedStatistic queuedStatistic = new FightQueuedStatistic(actor, entry, property, expiryTimeMs);
		queuedStatistics.add(queuedStatistic);
		cleanQueuedStatistics();
	}

	public void registerQueuedStatistics(Actor actor, int hitsplatAmount)
	{
		log.debug("Checking queue statistics, queued size {} for hitsplat {}", queuedStatistics.size(), hitsplatAmount);

		// synchronized list does not seem to be needed here?
		Iterator iterator = queuedStatistics.iterator();
		while (iterator.hasNext())
		{
			FightQueuedStatistic queuedStatistic = (FightQueuedStatistic) iterator.next();
			Actor queuedActor = queuedStatistic.getActor();
			FightStatisticEntry entry = queuedStatistic.getEntry();
			FightStatisticProperty property = queuedStatistic.getProperty();

			log.debug("Attempt register queued statistic {} - {}", entry.getKey(), property.getKey());

			// Guard: check if this hitsplat is on the right actor
			if (actor != queuedActor)
			{
				log.debug("Skipping queued statistic because of wrong actor.");
				continue;
			}

			// Will prevent registering twice
			if (!queuedStatistic.isValid())
			{
				log.debug("Skipping queued statistic because of invalid.");
				continue;
			}

			FightStatistic statistic = ensureStatistic(actor, entry);

			if (property == FightStatisticProperty.MISS_DAMAGES || property == FightStatisticProperty.MISS_COUNTERS)
			{
				log.debug("Register queued statistic miss: {}", hitsplatAmount);
				statistic.registerMiss(hitsplatAmount);
			}
			else if (property == FightStatisticProperty.HIT_DAMAGES || property == FightStatisticProperty.HIT_COUNTERS)
			{
				log.debug("Register queued statistic hit: {}", hitsplatAmount);
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

			log.debug("Remove queued statistic {}", queuedStatistic.getEntry().getKey());
			queuedStatistics.remove(queuedStatistic);
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

	public void handleStatisticUpdate()
	{
		registerQueuedIdleTicks();
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

	public long getIdleTickCounter()
	{
		return idleTickCounter;
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

		FightSession session = new FightSession(this);
		sessions.put(actor, session);

		return session;
	}

	public Instant getLastUpdate()
	{
		return getLastUpdate(false);
	}

	public Instant getLastUpdate(boolean updatedAtInfluencerOnly)
	{
		Instant maxLastUpdate = null;

		for (FightSession session : getAllSessions())
		{
			Instant lastUpdate = session.getLastUpdate(updatedAtInfluencerOnly);

			if (lastUpdate == null)
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

	public FightSession calculateTotalSession()
	{
		FightSession totalSession = new FightSession(this);
		totalSession.addIdleTicks(idleTickCounter);

		for (FightSession session : getAllSessions())
		{
			totalSession.addInteractingTicks(session.getInteractingTickCounter());

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

	public boolean isIdling(CopyOnWriteArrayList<String> actorNames)
	{
		for (String actorName : actorNames)
		{

			// Not idling when the actor can be found in the surroundings
			// for some boss fights this would not work (e.g. The Gauntlet)
			// because often you are seeing the boss while prepping.
			// We accept these inconsistencies as it weighs out situations where
			// you are not interacting/attacking the boss due to a certain phase and
			// then we don't want idling to kick in to skew your DPS (e.g. certain Olm phases)
			if (this.actorName.equals(actorName))
			{
				return false;
			}
		}

		return true;
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

		if (finishedSessions.size() > MAX_FINISHED_FIGHT_SESSION_AMOUNT)
		{
			log.debug("Removing a finished session due to maximum amount reached...");
			finishedSessions.remove(0);
		}
	}

	public void increaseSessionCounter()
	{
		sessionCounter++;
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

	public int getSessionCounter()
	{
		return sessionCounter;
	}

	public Collection<FightSession> getOngoingSessions()
	{
		return sessions.values();
	}

	public CopyOnWriteArrayList<FightSession> getAllSessions()
	{
		CopyOnWriteArrayList<FightSession> allSessions = new CopyOnWriteArrayList();

		allSessions.addAll(finishedSessions);
		allSessions.addAll(sessions.values());

		return allSessions;
	}
}
