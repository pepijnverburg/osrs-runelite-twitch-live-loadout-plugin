package com.twitchliveloadout.twitch.eventsub;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.products.TwitchProduct;
import com.twitchliveloadout.marketplace.products.TwitchProductCost;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.marketplace.transactions.TwitchTransactionOrigin;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.eventsub.messages.ChannelPointsRedeem;
import com.twitchliveloadout.twitch.eventsub.messages.ChannelPointsReward;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
public class TwitchEventSubListener {
    private final TwitchLiveLoadoutPlugin plugin;
    private final TwitchApi twitchApi;
    private final Gson gson;

    public TwitchEventSubListener(TwitchLiveLoadoutPlugin plugin, TwitchApi twitchApi, Gson gson)
    {
        this.plugin = plugin;
        this.twitchApi = twitchApi;
        this.gson = gson;
    }

    public void onReady(String sessionId)
    {

        // once the socket is ready we can create the subscriptions
        twitchApi.createEventSubSubscription(sessionId, TwitchEventSubType.CHANNEL_POINTS_REDEEM);
    }

    public void onEvent(TwitchEventSubType type, JsonObject payload)
    {
        switch (type) {
            case CHANNEL_POINTS_REDEEM -> {
                ChannelPointsRedeem channelPointsRedeem = gson.fromJson(payload, ChannelPointsRedeem.class);
                handleChannelPointsRedeem(channelPointsRedeem);
            }
        }
    }

    private void handleChannelPointsRedeem(ChannelPointsRedeem redeem)
    {
        ChannelPointsReward reward = redeem.reward;
        TwitchTransaction twitchTransaction = new TwitchTransaction();
        TwitchProduct twitchProduct = new TwitchProduct();
        TwitchProductCost twitchProductCost = new TwitchProductCost();

        twitchProductCost.amount = reward.cost;
        twitchProductCost.type = "channel points";

        twitchProduct.sku = reward.id;
        twitchProduct.domain = "channel_points";
        twitchProduct.cost = twitchProductCost;
        twitchProduct.displayName = reward.title;
        twitchProduct.expiration = "";

        twitchTransaction.id = redeem.id;
        twitchTransaction.timestamp = Instant.now().toString();
        twitchTransaction.broadcaster_id = redeem.broadcaster_user_id;
        twitchTransaction.broadcaster_login = redeem.broadcaster_user_login;
        twitchTransaction.broadcaster_name = redeem.broadcaster_user_name;
        twitchTransaction.user_id = redeem.user_id;
        twitchTransaction.user_login = redeem.user_login;
        twitchTransaction.user_name = redeem.user_name;
        twitchTransaction.product_type = TwitchEventSubType.CHANNEL_POINTS_REDEEM.getType();
        twitchTransaction.product_data = twitchProduct;
        twitchTransaction.handled_at = Instant.now().toString();
        twitchTransaction.origin = TwitchTransactionOrigin.EVENT_SUB;
        twitchTransaction.eventSubType = TwitchEventSubType.CHANNEL_POINTS_REDEEM;

        addTwitchTransaction(twitchTransaction);
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
}
