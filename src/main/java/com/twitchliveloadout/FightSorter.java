package com.twitchliveloadout;

import java.time.Instant;
import java.util.Comparator;

public class FightSorter implements Comparator<Fight>
{
	public int compare(Fight f1, Fight f2)
	{
		Instant lastUpdate = f1.getLastUpdate(true);
		Instant comparedLastUpdate = f2.getLastUpdate(true);
		long lastUpdateEpoch = (lastUpdate == null ? 0 : lastUpdate.getEpochSecond());
		long comparedLastUpdateEpoch = (comparedLastUpdate == null ? 0 : comparedLastUpdate.getEpochSecond());
		boolean isLater = lastUpdateEpoch > comparedLastUpdateEpoch;

		// check for equal
		if (lastUpdateEpoch == comparedLastUpdateEpoch)
		{
			return 0;
		}

		// descending order, from later to earlier price
		return isLater ? -1 : 1;
	}
}
