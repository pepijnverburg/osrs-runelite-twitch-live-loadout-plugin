package com.twitchliveloadout.marketplace;

import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.TwitchProduct;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.twitch.eventsub.TwitchEventSubType;

public class MarketplaceMessages {
	public static String formatMessage(String message, MarketplaceProduct marketplaceProduct, MarketplaceEffect marketplaceEffect)
	{
		TwitchTransaction transaction = (marketplaceProduct != null ? marketplaceProduct.getTransaction() : null);
		TwitchProduct twitchProduct = (marketplaceProduct != null ? marketplaceProduct.getTwitchProduct() : null);
		TwitchEventSubType eventSubType = null;
		String viewerName = "viewer";
		String channelName = "broadcaster";
		String currencyAmount = "";
		String currencyType = "";
		String productDuration = "";
		String productDurationLeft = "";
		String effectDuration = "";
		String effectDurationLeft = "";

		if (transaction != null)
		{
			viewerName = transaction.user_name;
			channelName = transaction.broadcaster_name;
			eventSubType = transaction.eventSubType;
		}

		if (twitchProduct != null)
		{
			currencyAmount = twitchProduct.cost.amount.toString();
			currencyType = twitchProduct.cost.type;
		}

		if (marketplaceEffect != null)
		{
			effectDuration = MarketplaceDuration.humanizeDurationRounded(marketplaceEffect.getDuration());
			effectDurationLeft = MarketplaceDuration.humanizeDurationRounded(marketplaceEffect.getDurationLeft());
		}

		if (marketplaceProduct != null)
		{
			productDuration = MarketplaceDuration.humanizeDurationRounded(marketplaceProduct.getDuration());
			productDurationLeft = MarketplaceDuration.humanizeDurationRounded(marketplaceProduct.getDurationLeft());
		}

		message = message.replaceAll("\\{viewerName\\}", viewerName);
		message = message.replaceAll("\\{channelName\\}", channelName);
		message = message.replaceAll("\\{currencyAmount\\}", currencyAmount);
		message = message.replaceAll("\\{currencyType\\}", currencyType);
		message = message.replaceAll("\\{productDuration\\}", productDuration);
		message = message.replaceAll("\\{productDurationLeft\\}", productDurationLeft);
		message = message.replaceAll("\\{effectDuration\\}", effectDuration);
		message = message.replaceAll("\\{effectDurationLeft\\}", effectDurationLeft);
		message = message.trim();

		return message;
	}
}
