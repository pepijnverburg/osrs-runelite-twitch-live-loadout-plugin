package com.twitchliveloadout.marketplace.transactions;

import com.twitchliveloadout.marketplace.products.TwitchProduct;

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
}
