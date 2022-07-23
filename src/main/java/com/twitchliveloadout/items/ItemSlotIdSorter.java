package com.twitchliveloadout.items;

import java.util.Comparator;

public class ItemSlotIdSorter implements Comparator<PricedItem>
{
	public int compare(PricedItem p1, PricedItem p2)
	{
		long slotId = p1.getSlotId();
		long comparedSlotId = p2.getSlotId();
		boolean isHigherSlotId = slotId > comparedSlotId;

		// check for same slot id
		if (slotId == comparedSlotId)
		{
			return 0;
		}

		// ascending order
		return isHigherSlotId ? 1 : -1;
	}
}
