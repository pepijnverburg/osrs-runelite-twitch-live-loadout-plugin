package net.runelite.client.plugins.twitchliveloadout;

import java.util.Comparator;

public class ItemPriceSorter implements Comparator<PricedItem>
{
	public int compare(PricedItem p1, PricedItem p2)
	{
		long price = p1.getPrice();
		long comparedPrice = p2.getPrice();
		boolean isMoreExpensive = price > comparedPrice;

		// check for equal
		if (price == comparedPrice)
		{
			return 0;
		}

		// descending order, from high to low price
		return isMoreExpensive ? -1 : 1;
	}
}
