package com.twitchliveloadout.marketplace;

import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.TwitchProduct;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.twitch.eventsub.TwitchEventSubType;
import com.twitchliveloadout.twitch.eventsub.messages.*;
import lombok.Getter;

import java.util.HashMap;

public class MarketplaceMessages {
	public static String formatMessage(String message, MarketplaceProduct marketplaceProduct, MarketplaceEffect marketplaceEffect)
	{
		TwitchTransaction transaction = (marketplaceProduct != null ? marketplaceProduct.getTransaction() : null);
		TwitchProduct twitchProduct = (marketplaceProduct != null ? marketplaceProduct.getTwitchProduct() : null);
		TwitchEventSubType eventSubType = null;
		BaseMessage eventSubMessage = null;
		HashMap<MarketplaceMessageTemplate, String> templateLookup = new HashMap<>();

		// add defaults
		templateLookup.put(MarketplaceMessageTemplate.VIEWER_NAME, "viewer");
		templateLookup.put(MarketplaceMessageTemplate.CHANNEL_NAME, "streamer");

		if (transaction != null)
		{
			templateLookup.put(MarketplaceMessageTemplate.VIEWER_NAME, transaction.user_name);
			templateLookup.put(MarketplaceMessageTemplate.CHANNEL_NAME, transaction.broadcaster_name);
			eventSubType = transaction.eventSubType;
			eventSubMessage = transaction.eventSubMessage;
		}

		if (twitchProduct != null)
		{
			templateLookup.put(MarketplaceMessageTemplate.CURRENCY_AMOUNT, twitchProduct.cost.amount.toString());
			templateLookup.put(MarketplaceMessageTemplate.CURRENCY_TYPE, twitchProduct.cost.type);
		}

		if (marketplaceEffect != null)
		{
			templateLookup.put(MarketplaceMessageTemplate.EFFECT_DURATION, MarketplaceDuration.humanizeDurationRounded(marketplaceEffect.getDuration()));
			templateLookup.put(MarketplaceMessageTemplate.EFFECT_DURATION_LEFT, MarketplaceDuration.humanizeDurationRounded(marketplaceEffect.getDurationLeft()));
		}

		if (marketplaceProduct != null)
		{
			templateLookup.put(MarketplaceMessageTemplate.PRODUCT_DURATION, MarketplaceDuration.humanizeDurationRounded(marketplaceProduct.getDuration()));
			templateLookup.put(MarketplaceMessageTemplate.PRODUCT_DURATION_LEFT, MarketplaceDuration.humanizeDurationRounded(marketplaceProduct.getDurationLeft()));
		}

		if (eventSubMessage instanceof ChannelContinueSubscription channelContinueSubscription)
		{
			templateLookup.put(MarketplaceMessageTemplate.SUB_MONTHS, channelContinueSubscription.duration_months.toString());
			templateLookup.put(MarketplaceMessageTemplate.SUB_TOTAL_MONTHS, channelContinueSubscription.cumulative_months.toString());
		}

		if (eventSubMessage instanceof ChannelGiftSubscription channelGiftSubscription)
		{
			templateLookup.put(MarketplaceMessageTemplate.GIFTED_AMOUNT, channelGiftSubscription.total.toString());
			templateLookup.put(MarketplaceMessageTemplate.GIFTED_TOTAL_AMOUNT, channelGiftSubscription.cumulative_total.toString());
		}

		if (eventSubMessage instanceof ChannelRaid channelRaid)
		{
			templateLookup.put(MarketplaceMessageTemplate.RAID_VIEWER_AMOUNT, channelRaid.viewers.toString());
			templateLookup.put(MarketplaceMessageTemplate.VIEWER_NAME, channelRaid.from_broadcaster_user_name);
		}

		if (eventSubMessage instanceof BaseCharityCampaignInfo baseCharityCampaignInfo)
		{
			templateLookup.put(MarketplaceMessageTemplate.CHARITY_NAME, baseCharityCampaignInfo.charity_name);
		}

		if (eventSubMessage instanceof CharityCampaignAmountInfo charityCampaignAmountInfo)
		{
			templateLookup.put(MarketplaceMessageTemplate.CHARITY_CURRENT_AMOUNT, charityCampaignAmountInfo.current_amount.getCurrencyAmount().toString());
			templateLookup.put(MarketplaceMessageTemplate.CHARITY_TARGET_AMOUNT, charityCampaignAmountInfo.target_amount.getCurrencyAmount().toString());
			templateLookup.put(MarketplaceMessageTemplate.CHARITY_CURRENT_CURRENCY, charityCampaignAmountInfo.current_amount.currency);
			templateLookup.put(MarketplaceMessageTemplate.CHARITY_TARGET_CURRENCY, charityCampaignAmountInfo.target_amount.currency);
		}

		if (eventSubMessage instanceof BaseHypeTrain baseHypeTrain)
		{
			templateLookup.put(MarketplaceMessageTemplate.HYPE_TRAIN_GOAL, baseHypeTrain.goal.toString());
			templateLookup.put(MarketplaceMessageTemplate.HYPE_TRAIN_TOTAL, baseHypeTrain.total.toString());
			templateLookup.put(MarketplaceMessageTemplate.HYPE_TRAIN_LEVEL, baseHypeTrain.level.toString());
			templateLookup.put(MarketplaceMessageTemplate.HYPE_TRAIN_PROGRESS, baseHypeTrain.progress.toString());
		}

		// replace all the known template strings with their respective values
		for (MarketplaceMessageTemplate template : MarketplaceMessageTemplate.values())
		{
			String newContent = templateLookup.get(template);

			// when no value is set from the message or transaction default to an empty string
			// this ensures the removal of the template string
			// NOTE: we don't want to remove unknown template strings because we want the streamer to see it doesn't exist!
			if (newContent == null)
			{
				newContent = "";
			}

			message = message.replaceAll("\\{" + template.name + "\\}", newContent);
		}

		message = message.trim();

		return message;
	}

	@Getter
	public enum MarketplaceMessageTemplate {
		VIEWER_NAME("viewerName"),
		CHANNEL_NAME("channelName"),
		CURRENCY_AMOUNT("currencyAmount"),
		CURRENCY_TYPE("currencyType"),
		PRODUCT_DURATION("productDuration"),
		PRODUCT_DURATION_LEFT("productDurationLeft"),
		EFFECT_DURATION("effectDuration"),
		EFFECT_DURATION_LEFT("effectDurationLeft"),
		SUB_MONTHS("subMonths"),
		SUB_TOTAL_MONTHS("subTotalMonths"),
		GIFTED_AMOUNT("giftedAmount"),
		GIFTED_TOTAL_AMOUNT("giftedTotalAmount"),
		RAID_VIEWER_AMOUNT("raidViewerAmount"),
		CHARITY_NAME("charityName"),
		CHARITY_CURRENT_AMOUNT("charityCurrentAmount"),
		CHARITY_CURRENT_CURRENCY("charityCurrentCurrency"),
		CHARITY_TARGET_AMOUNT("charityTargetAmount"),
		CHARITY_TARGET_CURRENCY("charityTargetCurrency"),
		HYPE_TRAIN_LEVEL("hypeTrainLevel"),
		HYPE_TRAIN_TOTAL("hypeTrainTotal"),
		HYPE_TRAIN_PROGRESS("hypeTrainProgress"),
		HYPE_TRAIN_GOAL("hypeTrainGoal"),
		;

		private final String name;

		MarketplaceMessageTemplate(String name)
		{
			this.name = name;
		}
	}
}
