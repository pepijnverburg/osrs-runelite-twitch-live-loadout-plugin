package net.runelite.client.plugins.twitchliveloadout;

import java.time.Instant;

public class FightStatistic {
	private long firstUpdate = 0;
	private long lastUpdate = 0;
	private long hitDamage = 0;
	private long missDamage = 0;
	private long hitCounter = 0;
	private long missCounter = 0;

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
		long candidateFirstUpdate = statistic.getFirstUpdate();
		long candidateLastUpdate = statistic.getLastUpdate();

		hitDamage += statistic.getHitDamage();
		hitCounter += statistic.getHitCounter();
		missDamage += statistic.getMissDamage();
		missCounter += statistic.getMissCounter();

		if ((candidateFirstUpdate != 0 && candidateFirstUpdate < firstUpdate) || firstUpdate == 0)
		{
			firstUpdate = candidateFirstUpdate;
		}

		if ((candidateLastUpdate != 0 && candidateLastUpdate > lastUpdate) || lastUpdate == 0)
		{
			lastUpdate = candidateLastUpdate;
		}
	}

	public void registerUpdate()
	{
		final long now = Instant.now().getEpochSecond();

		if (firstUpdate == 0)
		{
			firstUpdate = now;
		}

		lastUpdate = now;
	}

	public long getDurationSeconds()
	{
		return lastUpdate - firstUpdate;
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

	public long getLastUpdate()
	{
		return lastUpdate;
	}

	public long getFirstUpdate()
	{
		return firstUpdate;
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

		firstUpdate = 0;
		lastUpdate = 0;
	}
}
