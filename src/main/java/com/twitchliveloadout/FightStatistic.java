package net.runelite.client.plugins.twitchliveloadout;

import java.time.Instant;

public class FightStatistic {
	private final FightSession session;

	private Instant firstUpdate;
	private Instant lastUpdate;
	private long hitDamage = 0;
	private long missDamage = 0;
	private long hitCounter = 0;
	private long missCounter = 0;

	public FightStatistic(FightSession session)
	{
		this.session = session;
	}

	public void registerHit(int damage)
	{
		hitDamage += damage;
		hitCounter ++;
		registerUpdate();
	}

	public void registerMiss(int damage)
	{
		missDamage += damage;
		missCounter ++;
		registerUpdate();
	}

	public void addStatistic(FightStatistic statistic)
	{
		Instant candidateFirstUpdate = statistic.getFirstUpdate();
		Instant candidateLastUpdate = statistic.getLastUpdate();

		hitDamage += statistic.getHitDamage();
		hitCounter += statistic.getHitCounter();
		missDamage += statistic.getMissDamage();
		missCounter += statistic.getMissCounter();

		if (firstUpdate == null || (candidateFirstUpdate != null && candidateFirstUpdate.isBefore(firstUpdate)))
		{
			firstUpdate = candidateFirstUpdate;
		}

		if (lastUpdate == null || (candidateLastUpdate != null && candidateLastUpdate.isAfter(lastUpdate)))
		{
			lastUpdate = candidateLastUpdate;
		}
	}

	public void registerUpdate()
	{
		final Instant now = Instant.now();

		if (firstUpdate == null)
		{
			firstUpdate = now;
		}

		lastUpdate = now;
		session.handleStatisticUpdate();
	}

	public long getDurationSeconds()
	{
		Instant lastUpdate = getLastUpdate();
		Instant firstUpdate = getFirstUpdate();

		if (lastUpdate == null || firstUpdate == null)
		{
			return 0;
		}

		return getLastUpdate().getEpochSecond() - getFirstUpdate().getEpochSecond();
	}

	public long getHitDamage()
	{
		return hitDamage;
	}

	public long getMissDamage()
	{
		return missDamage;
	}

	public long getHitCounter()
	{
		return hitCounter;
	}

	public long getMissCounter()
	{
		return missCounter;
	}

	public Instant getLastUpdate()
	{
		return lastUpdate;
	}

	public Instant getFirstUpdate()
	{
		return firstUpdate;
	}

	public boolean isEverUpdated()
	{
		return lastUpdate != null || firstUpdate != null;
	}

	public long getValueByProperty(FightStatisticProperty property)
	{
		final String key = property.getKey();

		if (key.equals(FightStatisticProperty.HIT_DAMAGES.getKey()))
		{
			return getHitDamage();
		}
		else if (key.equals(FightStatisticProperty.HIT_COUNTERS.getKey()))
		{
			return getHitCounter();
		}
		else if (key.equals(FightStatisticProperty.MISS_DAMAGES.getKey()))
		{
			return getMissDamage();
		}
		else if (key.equals(FightStatisticProperty.MISS_COUNTERS.getKey()))
		{
			return getMissCounter();
		}
		else if (key.equals(FightStatisticProperty.DURATION_SECONDS.getKey()))
		{
			return getDurationSeconds();
		}

		return 0;
	}

	public void reset()
	{
		hitDamage = 0;
		missDamage = 0;
		hitCounter = 0;
		missCounter = 0;

		firstUpdate = null;
		lastUpdate = null;
	}
}
