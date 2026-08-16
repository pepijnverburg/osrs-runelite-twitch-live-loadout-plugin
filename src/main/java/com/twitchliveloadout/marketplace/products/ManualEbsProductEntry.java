package com.twitchliveloadout.marketplace.products;

import com.twitchliveloadout.utilities.GameEventType;

import java.util.ArrayList;
import java.util.Collection;

public class ManualEbsProductEntry
{
	public EbsProduct ebsProduct;
	public ArrayList<String> gameEventTypeIds = new ArrayList<>();

	public ManualEbsProductEntry()
	{
	}

	public ManualEbsProductEntry(EbsProduct ebsProduct, Collection<GameEventType> gameEventTypes)
	{
		this.ebsProduct = ebsProduct;
		setGameEventTypes(gameEventTypes);
	}

	public void setGameEventTypes(Collection<GameEventType> gameEventTypes)
	{
		gameEventTypeIds.clear();

		if (gameEventTypes == null)
		{
			return;
		}

		for (GameEventType gameEventType : gameEventTypes)
		{
			if (gameEventType == null)
			{
				continue;
			}

			gameEventTypeIds.add(gameEventType.getId());
		}
	}

	public ArrayList<GameEventType> getGameEventTypes()
	{
		ArrayList<GameEventType> gameEventTypes = new ArrayList<>();

		for (String gameEventTypeId : gameEventTypeIds)
		{
			GameEventType gameEventType = GameEventType.fromId(gameEventTypeId);

			if (gameEventType == null)
			{
				continue;
			}

			gameEventTypes.add(gameEventType);
		}

		return gameEventTypes;
	}

	public boolean isTriggeredBy(GameEventType gameEventType)
	{
		return gameEventType != null && gameEventTypeIds.contains(gameEventType.getId());
	}

	public String getEbsProductId()
	{
		return ebsProduct != null ? ebsProduct.id : null;
	}

	public boolean isValid()
	{
		return ebsProduct != null && ebsProduct.id != null && ebsProduct.behaviour != null && ebsProduct.name != null;
	}

	public String getTriggerSummary()
	{
		ArrayList<GameEventType> gameEventTypes = getGameEventTypes();
		ArrayList<String> names = new ArrayList<>();

		for (GameEventType gameEventType : gameEventTypes)
		{
			names.add(gameEventType.getName());
		}

		return String.join(", ", names);
	}
}