package com.twitchliveloadout.marketplace;

import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.StreamerProduct;
import com.twitchliveloadout.marketplace.products.TwitchProduct;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.twitch.eventsub.TwitchEventSubType;
import com.twitchliveloadout.twitch.eventsub.messages.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

@Slf4j
public class MarketplaceMessages {
	public static String formatMessage(String message, MarketplaceProduct marketplaceProduct, MarketplaceEffect marketplaceEffect)
	{
		TwitchTransaction transaction = null;
		TwitchProduct twitchProduct = null;
		StreamerProduct streamerProduct = null;
		BaseMessage eventSubMessage = null;
		HashMap<MarketplaceMessageTemplate, String> templateLookup = new HashMap<>();

		if (marketplaceProduct != null) {
			transaction = marketplaceProduct.getTransaction();
			twitchProduct = marketplaceProduct.getTwitchProduct();
			streamerProduct = marketplaceProduct.getStreamerProduct();
		}

		// add defaults
		templateLookup.put(MarketplaceMessageTemplate.VIEWER_NAME, "viewer");
		templateLookup.put(MarketplaceMessageTemplate.CHANNEL_NAME, "streamer");
		templateLookup.put(MarketplaceMessageTemplate.PRODUCT_NAME, "Random Event");

		if (transaction != null) {
			templateLookup.put(MarketplaceMessageTemplate.VIEWER_NAME, transaction.user_name);
			templateLookup.put(MarketplaceMessageTemplate.CHANNEL_NAME, transaction.broadcaster_name);
			eventSubMessage = transaction.eventSubMessage;
		}

		if (streamerProduct != null) {
			templateLookup.put(MarketplaceMessageTemplate.PRODUCT_NAME, streamerProduct.name);
		}

		if (twitchProduct != null) {

			// make sure things such as '150.0' are displayed as '150'
			Double rawCurrencyAmount = twitchProduct.cost.amount;
			boolean hasDecimalPlaces = (rawCurrencyAmount % 1 != 0);
			String formattedCurrencyAmount = hasDecimalPlaces ? rawCurrencyAmount.toString() : Integer.toString(rawCurrencyAmount.intValue());

			templateLookup.put(MarketplaceMessageTemplate.CURRENCY_AMOUNT, formattedCurrencyAmount);
			templateLookup.put(MarketplaceMessageTemplate.CURRENCY_TYPE, twitchProduct.cost.type);
		}

		if (marketplaceEffect != null) {
			templateLookup.put(MarketplaceMessageTemplate.EFFECT_DURATION, MarketplaceDuration.humanizeDurationRounded(marketplaceEffect.getDuration()));
			templateLookup.put(MarketplaceMessageTemplate.EFFECT_DURATION_LEFT, MarketplaceDuration.humanizeDurationRounded(marketplaceEffect.getDurationLeft()));
		}

		if (marketplaceProduct != null) {
			templateLookup.put(MarketplaceMessageTemplate.PRODUCT_DURATION, MarketplaceDuration.humanizeDurationRounded(marketplaceProduct.getDuration()));
			templateLookup.put(MarketplaceMessageTemplate.PRODUCT_DURATION_LEFT, MarketplaceDuration.humanizeDurationRounded(marketplaceProduct.getDurationLeft()));
		}

		if (eventSubMessage instanceof BaseUserInfo) {
			BaseUserInfo baseUserInfo = (BaseUserInfo) eventSubMessage;
			templateLookup.put(MarketplaceMessageTemplate.VIEWER_NAME, baseUserInfo.user_name);
			templateLookup.put(MarketplaceMessageTemplate.CHANNEL_NAME, baseUserInfo.broadcaster_user_name);
		}

		if (eventSubMessage instanceof ChannelContinueSubscription) {
			ChannelContinueSubscription channelContinueSubscription = (ChannelContinueSubscription) eventSubMessage;
			templateLookup.put(MarketplaceMessageTemplate.SUB_MONTHS, channelContinueSubscription.duration_months.toString());
			templateLookup.put(MarketplaceMessageTemplate.SUB_TOTAL_MONTHS, channelContinueSubscription.cumulative_months.toString());
		}

		if (eventSubMessage instanceof ChannelGiftSubscription) {
			ChannelGiftSubscription channelGiftSubscription = (ChannelGiftSubscription) eventSubMessage;
			templateLookup.put(MarketplaceMessageTemplate.GIFTED_AMOUNT, channelGiftSubscription.total.toString());
			templateLookup.put(MarketplaceMessageTemplate.GIFTED_TOTAL_AMOUNT, channelGiftSubscription.cumulative_total.toString());
		}

		if (eventSubMessage instanceof ChannelRaid) {
			ChannelRaid channelRaid = (ChannelRaid) eventSubMessage;
			templateLookup.put(MarketplaceMessageTemplate.VIEWER_NAME, channelRaid.from_broadcaster_user_name);
			templateLookup.put(MarketplaceMessageTemplate.RAID_VIEWER_AMOUNT, channelRaid.viewers.toString());
			templateLookup.put(MarketplaceMessageTemplate.RAIDER_CHANNEL_NAME, channelRaid.from_broadcaster_user_name);
			templateLookup.put(MarketplaceMessageTemplate.RAIDED_CHANNEL_NAME, channelRaid.to_broadcaster_user_name);
		}

		if (eventSubMessage instanceof BaseCharityCampaignInfo) {
			BaseCharityCampaignInfo baseCharityCampaignInfo = (BaseCharityCampaignInfo) eventSubMessage;
			templateLookup.put(MarketplaceMessageTemplate.CHARITY_NAME, baseCharityCampaignInfo.charity_name);
		}

		if (eventSubMessage instanceof CharityCampaignAmountInfo) {
			CharityCampaignAmountInfo charityCampaignAmountInfo = (CharityCampaignAmountInfo) eventSubMessage;
			templateLookup.put(MarketplaceMessageTemplate.CHARITY_CURRENT_AMOUNT, charityCampaignAmountInfo.current_amount.getCurrencyAmount().toString());
			templateLookup.put(MarketplaceMessageTemplate.CHARITY_TARGET_AMOUNT, charityCampaignAmountInfo.target_amount.getCurrencyAmount().toString());
			templateLookup.put(MarketplaceMessageTemplate.CHARITY_CURRENT_CURRENCY, charityCampaignAmountInfo.current_amount.currency);
			templateLookup.put(MarketplaceMessageTemplate.CHARITY_TARGET_CURRENCY, charityCampaignAmountInfo.target_amount.currency);
		}

		if (eventSubMessage instanceof BaseHypeTrain) {
			BaseHypeTrain baseHypeTrain = (BaseHypeTrain) eventSubMessage;
			templateLookup.put(MarketplaceMessageTemplate.HYPE_TRAIN_TOTAL, baseHypeTrain.total.toString());
			templateLookup.put(MarketplaceMessageTemplate.HYPE_TRAIN_LEVEL, baseHypeTrain.level.toString());
		}

		if (eventSubMessage instanceof BaseHypeTrainWithGoal) {
			BaseHypeTrainWithGoal baseHypeTrainWithGoal = (BaseHypeTrainWithGoal) eventSubMessage;
			templateLookup.put(MarketplaceMessageTemplate.HYPE_TRAIN_GOAL, baseHypeTrainWithGoal.goal.toString());
			templateLookup.put(MarketplaceMessageTemplate.HYPE_TRAIN_PROGRESS, baseHypeTrainWithGoal.progress.toString());
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
		PRODUCT_NAME("productName"),
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
		RAIDER_CHANNEL_NAME("raiderChannelName"),
		RAIDED_CHANNEL_NAME("raidedChannelName"),
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
