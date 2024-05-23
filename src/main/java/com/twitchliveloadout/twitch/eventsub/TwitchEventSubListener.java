package com.twitchliveloadout.twitch.eventsub;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.products.StreamerProduct;
import com.twitchliveloadout.marketplace.products.TwitchProduct;
import com.twitchliveloadout.marketplace.products.TwitchProductCost;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.marketplace.transactions.TwitchTransactionOrigin;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.eventsub.messages.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class TwitchEventSubListener {
    private final TwitchLiveLoadoutPlugin plugin;
    private final TwitchApi twitchApi;
    private final Gson gson;

    /**
     * List of all the active Twitch EventSub types
     */
    private CopyOnWriteArrayList<TwitchEventSubType> activeSubscriptionTypes = new CopyOnWriteArrayList<>();

    public TwitchEventSubListener(TwitchLiveLoadoutPlugin plugin, TwitchApi twitchApi, Gson gson)
    {
        this.plugin = plugin;
        this.twitchApi = twitchApi;
        this.gson = gson;
    }

    public void onReady(String sessionId)
    {

        // once the socket is ready we can create the subscriptions
        for (TwitchEventSubType type : TwitchEventSubType.values())
        {

            // guard: skip when it is not enabled
            if (!type.isEnabled())
            {
                continue;
            }

            twitchApi.createEventSubSubscription(
                sessionId,
                type,
                (response) -> {
                    activeSubscriptionTypes.add(type);
                },
                (error) -> {
                    activeSubscriptionTypes.remove(type);
                }
            );
        }
    }

    public void onEvent(String messageId, TwitchEventSubType type, JsonObject payload)
    {
        Object message = gson.fromJson(payload, type.getMessageClass());
        TwitchTransaction twitchTransaction = createTransactionFromEventMessage(messageId, type, message);

        // handle types that need to extend the twitch transaction in any way
        switch (type) {
            case CHANNEL_POINTS_REDEEM -> {
                expandTransactionForChannelPointsRedeem(twitchTransaction, (ChannelPointsRedeem) message);
            }
            case CHARITY_CAMPAIGN_DONATE -> {
                expandTransactionForCharityCampaignDonation(twitchTransaction, (CharityCampaignDonate) message);
            }
        }

        addTwitchTransaction(twitchTransaction);
    }

    private void expandTransactionForChannelPointsRedeem(TwitchTransaction twitchTransaction, ChannelPointsRedeem redeem)
    {
        ChannelPointsReward reward = redeem.reward;
        TwitchProduct twitchProduct = twitchTransaction.product_data;
        TwitchProductCost twitchProductCost = twitchProduct.cost;

        // overrides specific for channel points redeem
        twitchTransaction.id = redeem.id;
        twitchProductCost.amount = reward.cost;
        twitchProductCost.type = "channel points";
        twitchProduct.sku = reward.id;
        twitchProduct.displayName = reward.title;
    }

    private void expandTransactionForCharityCampaignDonation(TwitchTransaction twitchTransaction, CharityCampaignDonate donation)
    {
        CharityCampaignAmount amount = donation.amount;
        String charityName = donation.charity_name;
        TwitchProduct twitchProduct = twitchTransaction.product_data;
        TwitchProductCost twitchProductCost = twitchProduct.cost;

        // overrides specific for charity donations
        twitchTransaction.id = donation.id;
        twitchProductCost.amount = amount.value;
        twitchProductCost.type = amount.currency;
        twitchProduct.sku = donation.id;
        twitchProduct.displayName = "Donation to "+ charityName;
    }

    private void addTwitchTransaction(TwitchTransaction transaction)
    {
        MarketplaceManager marketplaceManager = plugin.getMarketplaceManager();

        // guard: check whether the parameters are valid to handle the transaction
        if (marketplaceManager == null || transaction == null)
        {
            return;
        }

        plugin.logSupport("Adding a new Twitch transaction based on an EventSub event, transaction ID: "+ transaction.id);
        marketplaceManager.handleCustomTransaction(transaction);
    }

    private <T> TwitchTransaction createTransactionFromEventMessage(String messageId, TwitchEventSubType eventType, T message)
    {
        String nowString = Instant.now().toString();
        TwitchTransaction twitchTransaction = new TwitchTransaction();
        TwitchProduct twitchProduct = new TwitchProduct();
        TwitchProductCost twitchProductCost = new TwitchProductCost();

        twitchTransaction.id = messageId;
        twitchTransaction.timestamp = nowString;

        twitchProduct.sku = eventType.getType();
        twitchProduct.cost = twitchProductCost;
        twitchTransaction.product_data = twitchProduct;
        twitchTransaction.product_type = eventType.getType();

        twitchTransaction.handled_at = Instant.now().toString();
        twitchTransaction.origin = TwitchTransactionOrigin.EVENT_SUB;
        twitchTransaction.eventSubType = eventType;

        // add user info when available
        if (message instanceof BaseUserInfo baseUserInfo)
        {
            twitchTransaction.broadcaster_id = baseUserInfo.broadcaster_user_id;
            twitchTransaction.broadcaster_login = baseUserInfo.broadcaster_user_login;
            twitchTransaction.broadcaster_name = baseUserInfo.broadcaster_user_name;
            twitchTransaction.user_id = baseUserInfo.user_id;
            twitchTransaction.user_login = baseUserInfo.user_login;
            twitchTransaction.user_name = baseUserInfo.user_name;
        }

        return twitchTransaction;
    }

    public void revokeActiveSubscriptionType(TwitchEventSubType type)
    {
        activeSubscriptionTypes.remove(type);
    }

    public void clearActiveSubscriptionTypes()
    {
        activeSubscriptionTypes.clear();
    }
}
