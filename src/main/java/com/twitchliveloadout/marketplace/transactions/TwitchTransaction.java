package com.twitchliveloadout.marketplace.transactions;

import com.twitchliveloadout.marketplace.products.TwitchProduct;
import com.twitchliveloadout.twitch.eventsub.TwitchEventSubType;

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
	public String override_ebs_product_id;
	public String handled_at;
	public final String loaded_at = Instant.now().toString();

	public TwitchTransactionOrigin origin = TwitchTransactionOrigin.EBS;
	public TwitchEventSubType eventSubType = null;
}
