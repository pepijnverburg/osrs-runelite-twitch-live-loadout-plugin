package com.twitchliveloadout;

import com.google.common.collect.ImmutableList;
import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.game.ItemManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ItemStateManager {

	public final static int MAX_BANK_ITEMS = 200;

	private final TwitchState twitchState;
	private final Client client;
	private final ItemManager itemManager;
	private final TwitchLiveLoadoutConfig config;

	private static final List<Varbits> BANK_TAB_VARBITS = ImmutableList.of(
		Varbits.BANK_TAB_ONE_COUNT,
		Varbits.BANK_TAB_TWO_COUNT,
		Varbits.BANK_TAB_THREE_COUNT,
		Varbits.BANK_TAB_FOUR_COUNT,
		Varbits.BANK_TAB_FIVE_COUNT,
		Varbits.BANK_TAB_SIX_COUNT,
		Varbits.BANK_TAB_SEVEN_COUNT,
		Varbits.BANK_TAB_EIGHT_COUNT,
		Varbits.BANK_TAB_NINE_COUNT
	);

	public ItemStateManager(TwitchState twitchState, Client client, ItemManager itemManager, TwitchLiveLoadoutConfig config)
	{
		this.twitchState = twitchState;
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
	}

	public void onItemContainerChanged(ItemContainerChanged event)
	{
		final ItemContainer container = event.getItemContainer();
		final boolean isInventory = isItemContainer(event, InventoryID.INVENTORY);
		final boolean isEquipment = isItemContainer(event, InventoryID.EQUIPMENT);
		final boolean isBank = isItemContainer(event, InventoryID.BANK);

		// guard: block item containers not applicable for the state
		if (!isInventory && !isEquipment && !isBank)
		{
			return;
		}

		final Item[] items = container.getItems();
		long totalPrice = getTotalPrice(items);

		if (isInventory)
		{
			twitchState.setInventoryItems(items, totalPrice);
		}
		else if (isEquipment)
		{
			twitchState.setEquipmentItems(items, totalPrice);
		}
		else if (isBank)
		{
			setSlicedBankItems(items, totalPrice);
		}

		// update the weight for specific container changes
		if (isInventory || isEquipment)
		{
			final int weight = client.getWeight();
			twitchState.setWeight(weight);
		}
	}

	public int[] getBankTabAmounts()
	{
		final int tabAmount = BANK_TAB_VARBITS.size();
		final int[] amounts = new int[tabAmount];

		for (int tabIndex = 0; tabIndex < tabAmount; tabIndex++)
		{
			final int itemAmount = client.getVar(BANK_TAB_VARBITS.get(tabIndex));
			amounts[tabIndex] = itemAmount;
		}

		return amounts;
	}

	public boolean isItemContainer(ItemContainerChanged event, InventoryID containerId)
	{
		final int eventContainerId = event.getContainerId();
		return eventContainerId == containerId.getId();
	}

	public void setSlicedBankItems(Item[] items, long totalPrice)
	{
		final int[] tabAmounts = getBankTabAmounts();
		final List<PricedItem> highestPricedItems = getHighestPricedItems(items, tabAmounts, getMaxBankItemAmount());
		final Item[] selectedItems = new Item[highestPricedItems.size()];
		final int[] selectedTabAmounts = new int[tabAmounts.length];

		// deduce the tab amounts based on the new selected items
		// and convert the list to a plain Item array
		for (int pricedItemIndex = 0; pricedItemIndex < highestPricedItems.size(); pricedItemIndex++)
		{
			PricedItem pricedItem = highestPricedItems.get(pricedItemIndex);
			final Item selectedItem = pricedItem.getItem();
			final int tabId = pricedItem.getTabId();

			selectedItems[pricedItemIndex] = selectedItem;

			if (tabId >= 0) {
				selectedTabAmounts[tabId] += 1;
			}
		}

		twitchState.setBankItems(selectedItems, totalPrice, selectedTabAmounts);
	}

	public List<PricedItem> getPricedItems(Item[] items)
	{
		return getPricedItems(items, new int[0]);
	}

	public List<PricedItem> getPricedItems(Item[] items, int[] tabAmounts)
	{
		final List<PricedItem> pricedItems = new ArrayList();

		for (int slotId = 0; slotId < items.length; slotId++)
		{
			final Item item = items[slotId];
			final int itemId = item.getId();
			final int itemQuantity = item.getQuantity();
			final long itemPrice = ((long) itemManager.getItemPrice(itemId)) * itemQuantity;
			final int tabId = getItemTabId(slotId, tabAmounts);
			final PricedItem pricedItem = new PricedItem(item, itemPrice, slotId, tabId);

			// skip placeholder items to not be synced from the bank
			if (isPlaceholderItem(itemId))
			{
				continue;
			}

			pricedItems.add(pricedItem);
		}

		return pricedItems;
	}

	public boolean isPlaceholderItem(int itemId)
	{
		return itemManager.getItemComposition(itemId).getPlaceholderTemplateId() != -1;
	}

	public int getItemTabId(int slotId, int[] tabAmounts)
	{
		int totalAmount = 0;
		int remainingTabId = -1;

		for (int tabId = 0; tabId < tabAmounts.length; tabId++)
		{
			final int tabAmount = tabAmounts[tabId];
			totalAmount += tabAmount;

			if (slotId < totalAmount) {
				return tabId;
			}
		}

		return remainingTabId;
	}

	public long getTotalPrice(Item[] items)
	{
		long totalPrice = 0;
		final List<PricedItem> pricedItems = getPricedItems(items);

		for (PricedItem pricedItem : pricedItems)
		{
			totalPrice += pricedItem.getPrice();
		}

		return totalPrice;
	}

	public List<PricedItem> getHighestPricedItems(Item[] items, int[] tabAmounts, int maxAmount)
	{
		final List<PricedItem> pricedItems = getPricedItems(items, tabAmounts);
		Collections.sort(pricedItems, new ItemPriceSorter());
		final int itemAmount = pricedItems.size();

		if (maxAmount > itemAmount)
		{
			maxAmount = itemAmount;
		}

		final List<PricedItem> highestPricedItems = pricedItems.subList(0, maxAmount);
		Collections.sort(highestPricedItems, new ItemSlotIdSorter());

		return highestPricedItems;
	}

	public int getMaxBankItemAmount()
	{
		int maxAmount = config.bankItemAmount();

		if (maxAmount > MAX_BANK_ITEMS)
		{
			maxAmount = MAX_BANK_ITEMS;
		}

		if (maxAmount < 0)
		{
			maxAmount = 0;
		}

		return maxAmount;
	}
}
