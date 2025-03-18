package com.twitchliveloadout.items;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchState;
import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.game.ItemManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ItemStateManager {

	public final static int MAX_BANK_ITEMS = 5000;
	public final static int LOOTING_BAG_CONTAINER_ID = 516;

	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;
	private final Client client;
	private final ItemManager itemManager;
	private final TwitchLiveLoadoutConfig config;

	private static final List<Integer> BANK_TAB_VARBITS = Arrays.asList(
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

	public ItemStateManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client, ItemManager itemManager, TwitchLiveLoadoutConfig config)
	{
		this.plugin = plugin;
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
		final boolean isGroupStorage = isItemContainer(event, InventoryID.GROUP_STORAGE);
		final boolean isLootingBag = isItemContainer(event, LOOTING_BAG_CONTAINER_ID);

		// guard: block item containers that are not applicable for the state
		if (!isInventory && !isEquipment && !isBank && !isLootingBag && !isGroupStorage)
		{
			return;
		}

		final Item[] items = container.getItems();
		long totalPrice = getTotalPrice(items);

		if (config.inventoryEnabled() && isInventory)
		{
			twitchState.setInventoryItems(items, totalPrice);
		}
		else if (config.equipmentEnabled() && isEquipment)
		{
			twitchState.setEquipmentItems(items, totalPrice);
		}
		else if (config.bankEnabled() && isBank)
		{
			setSlicedBankItems(items, totalPrice);
		}
		else if (config.lootingBagEnabled() && isLootingBag)
		{
			twitchState.setLootingBagItems(items, totalPrice);
		}
		else if (config.groupStorageEnabled() && isGroupStorage)
		{
			twitchState.setGroupStorageItems(items);
			twitchState.setGroupStoragePrice(totalPrice);
		}

		// update the weight for specific container changes
		// NOTE: looting bag does not add weight
		if (config.weightEnabled() && (isInventory || isEquipment))
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
			final int itemAmount = client.getVarbitValue(BANK_TAB_VARBITS.get(tabIndex));
			amounts[tabIndex] = itemAmount;
		}

		return amounts;
	}

	public boolean isItemContainer(ItemContainerChanged event, InventoryID container)
	{
		return isItemContainer(event, container.getId());
	}

	public boolean isItemContainer(ItemContainerChanged event, int containerId)
	{
		final int eventContainerId = event.getContainerId();
		return eventContainerId == containerId;
	}

	public void setSlicedBankItems(Item[] items, long totalPrice)
	{
		final int[] tabAmounts = getBankTabAmounts();
		final int maxItemAmount = getMaxBankItemAmount();
		final List<PricedItem> highestPricedItems = getHighestPricedItems(items, tabAmounts, maxItemAmount);
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

		twitchState.setBankItems(selectedItems, selectedTabAmounts);
		twitchState.setBankItemsPrice(totalPrice);
	}

	public CopyOnWriteArrayList<PricedItem> getPricedItems(Item[] items)
	{
		return getPricedItems(items, new int[0]);
	}

	public CopyOnWriteArrayList<PricedItem> getPricedItems(Item[] items, int[] tabAmounts)
	{
		final CopyOnWriteArrayList<PricedItem> pricedItems = new CopyOnWriteArrayList<>();

		for (int slotId = 0; slotId < items.length; slotId++)
		{
			Item item = items[slotId];
			int itemId = item.getId();
			int itemQuantity = item.getQuantity();

			// translate placeholder IDs to their actual items
			if (isPlaceholderItem(itemId))
			{
				itemId = itemManager.getItemComposition(itemId).getPlaceholderId();
				itemQuantity = 0;
				item = new Item(itemId, itemQuantity);
			}

			final long itemPrice = ((long) itemManager.getItemPrice(itemId)) * itemQuantity;
			final int tabId = getItemTabId(slotId, tabAmounts);
			final PricedItem pricedItem = new PricedItem(item, itemPrice, slotId, tabId);

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
		final CopyOnWriteArrayList<PricedItem> pricedItems = getPricedItems(items);

		for (PricedItem pricedItem : pricedItems)
		{
			totalPrice += pricedItem.getPrice();
		}

		return totalPrice;
	}

	public List<PricedItem> getHighestPricedItems(Item[] items, int[] tabAmounts, int maxAmount)
	{
		final CopyOnWriteArrayList<PricedItem> pricedItems = getPricedItems(items, tabAmounts);
		pricedItems.sort(new ItemPriceSorter());
		final int itemAmount = pricedItems.size();

		if (maxAmount > itemAmount)
		{
			maxAmount = itemAmount;
		}

		final List<PricedItem> highestPricedItems = pricedItems.subList(0, maxAmount);
		highestPricedItems.sort(new ItemSlotIdSorter());

		return highestPricedItems;
	}

	public int getMaxBankItemAmount()
	{
		int maxAmount = config.bankItemsAmount();

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
