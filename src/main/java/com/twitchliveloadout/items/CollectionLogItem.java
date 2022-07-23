package com.twitchliveloadout.items;

import net.runelite.api.widgets.Widget;

public class CollectionLogItem {
	private final int id;
	private final int quantity;

	public CollectionLogItem(Widget itemWidget)
	{
		id = itemWidget.getItemId();
		quantity = itemWidget.getOpacity() == 0 ? itemWidget.getItemQuantity() : 0;
	}

	public int getId()
	{
		return id;
	}

	public int getQuantity()
	{
		return quantity;
	}
}
