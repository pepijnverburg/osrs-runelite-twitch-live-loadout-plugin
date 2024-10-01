package com.twitchliveloadout.twitch.eventsub;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.products.TwitchProduct;
import com.twitchliveloadout.marketplace.products.TwitchProductCost;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.marketplace.transactions.TwitchTransactionOrigin;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.eventsub.messages.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.twitchliveloadout.TwitchLiveLoadoutConfig.EVENT_SUB_HANDLED_FOLLOWER_IDS;

@Slf4j
public class TwitchEventSubListener {
    private final TwitchLiveLoadoutPlugin plugin;
    private final TwitchLiveLoadoutConfig config;
    private final TwitchApi twitchApi;
    private final Gson gson;

    /**
     * List of all handled follow events this session, identified by follower ID as it's very easy to duplicate the event.
     * This is the dedup mechanism, which can only be done client-side.
     */
    private final CopyOnWriteArrayList<String> handledFollowerIds = new CopyOnWriteArrayList<>();

    /**
     * List of all the active Twitch EventSub types
     */
    private final CopyOnWriteArrayList<TwitchEventSubType> activeSubscriptionTypes = new CopyOnWriteArrayList<>();

    public TwitchEventSubListener(TwitchLiveLoadoutPlugin plugin, TwitchLiveLoadoutConfig config, TwitchApi twitchApi, Gson gson)
    {
        this.plugin = plugin;
        this.config = config;
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
        BaseMessage message = gson.fromJson(payload, type.getMessageClass());
        TwitchTransaction twitchTransaction = createTransactionFromEventMessage(messageId, type, message);

        // guard: check whether the settings for handling events are disabled
        if (!config.marketplaceChannelEventsEnabled())
        {
            log.info("Skipped an EventSub event because it is disabled globally, type: "+ type.getName());
            return;
        }

        // guard: check whether this event in particular should be handled
        if (!type.getShouldHandleEventGetter().execute(plugin, config, message))
        {
            log.info("Skipped an EventSub event through a specific message check, type: "+ type.getName());
            return;
        }

        // handle types that need to extend the twitch transaction in any way
        switch (type) {
            case CHANNEL_POINTS_REDEEM:
                expandTransactionForChannelPointsRedeem(twitchTransaction, (ChannelPointsRedeem) message);
                break;
            case CHARITY_CAMPAIGN_DONATE:
                expandTransactionForCharityCampaignDonation(twitchTransaction, (CharityCampaignDonate) message);
                break;
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
        twitchProductCost.amount = reward.cost.doubleValue();
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
        twitchProductCost.amount = amount.getCurrencyAmount();
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

    private <T extends BaseMessage> TwitchTransaction createTransactionFromEventMessage(String messageId, TwitchEventSubType eventType, T message)
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
        twitchTransaction.eventSubMessage = message;

        // add user info when available
        if (message instanceof BaseUserInfo)
        {
            BaseUserInfo baseUserInfo = (BaseUserInfo) message;
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

    public void handleFollowerId(String followerId)
    {
        handledFollowerIds.add(followerId);

        // TODO: consider making this persistent to prevent people unfollowing + following upon each RL session.
        // currently viewers can trigger a valid follow event each fresh RL session.
        // plugin.setConfiguration(EVENT_SUB_HANDLED_FOLLOWER_IDS);
    }

    public boolean hasHandledFollowerId(String followerId)
    {
        return handledFollowerIds.contains(followerId);
    }
}
