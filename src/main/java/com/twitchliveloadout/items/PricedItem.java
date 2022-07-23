package com.twitchliveloadout.items;

import net.runelite.api.Item;

public class PricedItem
{
	private final Item item;
	private final long price;
	private final int slotId;
	private final int tabId;

	PricedItem(Item item, long price, int slotId, int tabId)
	{
		this.item = item;
		this.price = price;
		this.slotId = slotId;
		this.tabId = tabId;
	}

	public Item getItem() {
		return item;
	}

	public long getPrice() {
		return price;
	}

	public int getSlotId()
	{
		return slotId;
	}

	public int getTabId()
	{
		return tabId;
	}
}
