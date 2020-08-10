package net.runelite.client.plugins.twitchliveloadout;

import java.time.Instant;

public class FightStatistic {
	private long firstUpdate = 0;
	private long lastUpdate = 0;
	private long lastSessionStart = 0;

	/**
	 * Total counters and damages across sessions.
	 */
	private int hitDamageTotal = 0;
	private int missDamageTotal = 0;
	private int hitCounterTotal = 0;
	private int missCounterTotal = 0;

	/**
	 * Counters and damages for latest session.
	 * A session can be for example one kill.
	 */
	private int hitDamage = 0;
	private int missDamage = 0;
	private int hitCounter = 0;
	private int missCounter = 0;

	public void registerHit(int damage)
	{
		hitDamage += damage;
		hitCounter ++;
		hitDamageTotal += damage;
		hitCounterTotal ++;
		registerUpdate();
	}

	public void registerMiss(int damage)
	{
		missDamage += damage;
		missCounter ++;
		missDamageTotal += damage;
		missCounterTotal ++;
	}

	public void registerUpdate()
	{
		final long now = Instant.now().getEpochSecond();

		if (firstUpdate == 0)
		{
			firstUpdate = now;
		}

		if (lastSessionStart == 0)
		{
			lastSessionStart = now;
		}

		lastUpdate = now;
	}

	public boolean hasHadActivity()
	{
		return getLastUpdate() != 0;
	}

	public int getHitDamageTotal()
	{
		return hitDamageTotal;
	}

	public int getHitCounterTotal()
	{
		return hitCounterTotal;
	}

	public int getMissCounterTotal()
	{
		return missCounterTotal;
	}

	public int getMissDamageTotal()
	{
		return missDamageTotal;
	}

	public long getTotalDuration()
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

	public long getDuration()
	{
		return lastUpdate - lastSessionStart;
	}

	public void resetSession()
	{
		hitDamage = 0;
		missDamage = 0;
		hitCounter = 0;
		missCounter = 0;

		lastSessionStart = Instant.now().getEpochSecond();
		registerUpdate();
	}

	public void reset()
	{
		hitDamageTotal = 0;
		missDamageTotal = 0;
		hitCounterTotal = 0;
		missCounterTotal = 0;

		firstUpdate = 0;
		resetSession();
		registerUpdate();
	}
}
