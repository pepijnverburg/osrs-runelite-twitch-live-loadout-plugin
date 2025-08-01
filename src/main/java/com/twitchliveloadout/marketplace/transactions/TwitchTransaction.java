package com.twitchliveloadout.marketplace.transactions;

import com.twitchliveloadout.marketplace.products.TwitchProduct;
import com.twitchliveloadout.twitch.eventsub.TwitchEventSubType;
import com.twitchliveloadout.twitch.eventsub.messages.BaseMessage;
import com.twitchliveloadout.utilities.GameEventType;

import java.time.Instant;

public class TwitchTransaction {
	public String id;
	public String timestamp;
	public String broadcaster_id;
	public String broadcaster_login;
	public String broadcaster_name;
	public String user_id;
	public String user_login;
	public String user_name;
	public String product_type;
	public TwitchProduct product_data;
	public String ebs_product_id;
	public String handled_at;
	public final String loaded_at = Instant.now().toString();

	// support for alternative sources and extra information about them
	public TwitchTransactionOrigin origin = TwitchTransactionOrigin.EBS;
	public TwitchEventSubType eventSubType = null;
	public BaseMessage eventSubMessage = null;
	public GameEventType gameEventType = null;

	public boolean isEventSubTransaction()
	{
		return eventSubType != null && eventSubMessage != null;
	}

	public boolean isGameEventTransaction()
	{
		return gameEventType != null;
	}

	public boolean isCurrencyTransaction()
	{
		return !isEventSubTransaction() && product_data != null && product_data.cost != null;
	}

	public boolean isFreeTransaction()
	{
		return TwitchTransactionProductType.FREE.getType().equals(product_type);
	}

	public boolean isTestTransaction()
	{
		return product_type.equals(TwitchTransactionProductType.TEST.getType());
	}

	public boolean isManualTransaction()
	{
		return product_type.equals(TwitchTransactionProductType.MANUAL.getType());
	}
}
