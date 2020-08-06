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

	public int getHitDamage()
	{
		return hitDamage;
	}

	public int getMissDamage()
	{
		return missDamage;
	}

	public int getTotalDamage()
	{
		return hitDamage + missDamage;
	}

	public void registerUpdate()
	{
		final long now = Instant.now().getEpochSecond();

		if (firstUpdate == 0) {
			firstUpdate = now;
		}

		lastUpdate = now;
	}

	public boolean hasHadActivity()
	{
		return getLastUpdate() != 0;
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

	public long getEpochSeconds()
	{
		return lastUpdate - firstUpdate;
	}

	public float getEpochDps()
	{
		long seconds = getEpochSeconds();
		int totalDamage = getTotalDamage();

		if (seconds <= 0) {
			return 0;
		}

		return totalDamage / seconds;
	}

	public void reset()
	{
		hitDamage = 0;
		missDamage = 0;
		hitCounter = 0;
		missCounter = 0;
		registerUpdate();
	}
}
