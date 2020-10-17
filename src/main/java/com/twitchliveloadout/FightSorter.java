package net.runelite.client.plugins.twitchliveloadout;

import java.util.Comparator;

public class FightSorter implements Comparator<Fight>
{
	public int compare(Fight f1, Fight f2)
	{
		long lastUpdate = f1.getLastUpdate(true);
		long comparedLastUpdate = f2.getLastUpdate(true);
		boolean isLater = lastUpdate > comparedLastUpdate;

		// check for equal
		if (lastUpdate == comparedLastUpdate)
		{
			return 0;
		}

		// descending order, from later to earlier price
		return isLater ? -1 : 1;
	}
}
