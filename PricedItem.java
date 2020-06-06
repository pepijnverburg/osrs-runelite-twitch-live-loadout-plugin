package net.runelite.client.plugins.twitchstreamer;

import net.runelite.api.Item;

public class PricedItem implements Comparable
{
	private final Item item;
	private final long price;

	PricedItem(Item item, long price)
	{
		this.item = item;
		this.price = price;
	}

	@Override
	public int compareTo(Object comparedObject) {
		PricedItem comparedPricedItem = ((PricedItem) comparedObject);
		long comparedPrice = comparedPricedItem.price;
		boolean isMoreExpensive = this.price > comparedPrice;

		// check for equal
		if (this.price == comparedPrice)
		{
			return 0;
		}

		// descending order, from high to low price
		return isMoreExpensive ? -1 : 1;
	}

	public Item getItem() {
		return item;
	}

	public long getPrice() {
		return price;
	}
}
