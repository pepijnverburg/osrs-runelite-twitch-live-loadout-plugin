package net.runelite.client.plugins.twitchliveloadout;

import java.time.Instant;

public class FightStatistic {
	private long firstUpdate = 0;
	private long lastUpdate = 0;
	private int hitDamage = 0;
	private int missDamage = 0;
	private int hitCounter = 0;
	private int missCounter = 0;

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

	public long getDuration()
	{
		return lastUpdate - firstUpdate;
	}

	public int getHitDamage()
	{
		return hitDamage;
	}

	public int getMissDamage()
	{
		return missDamage;
	}

	public int getHitCounter()
	{
		return hitCounter;
	}

	public int getMissCounter()
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
