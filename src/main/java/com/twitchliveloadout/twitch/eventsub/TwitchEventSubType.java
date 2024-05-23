package com.twitchliveloadout.twitch.eventsub;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.twitch.eventsub.messages.*;
import lombok.Getter;

/**
 * Reference: https://dev.twitch.tv/docs/eventsub/eventsub-subscription-types/
 */
@Getter
public enum TwitchEventSubType {
    CHANNEL_POINTS_REDEEM("channel.channel_points_custom_reward_redemption.add", true, ChannelPointsRedeem.class, TwitchLiveLoadoutConfig::channelPointsRedeemEventMessage),
    START_SUBSCRIPTION("channel.subscribe",true, ChannelStartSubscription.class, TwitchLiveLoadoutConfig::newSubscriptionEventMessage),
    CONTINUE_SUBSCRIPTION("channel.subscription.message", true, ChannelContinueSubscription.class, TwitchLiveLoadoutConfig::newResubscriptionEventMessage),
    GIFT_SUBSCRIPTION("channel.subscription.gift", true, ChannelGiftSubscription.class, TwitchLiveLoadoutConfig::giftSubscriptionEventMessage),
    RAID("channel.raid", true, ChannelRaid.class, TwitchLiveLoadoutConfig::raidEventMessage),
    FOLLOW("channel.follow", true, 2, ChannelFollow.class, TwitchLiveLoadoutConfig::followEventMessage),
    ADD_MODERATOR("channel.moderator.add", true, ChannelAddModerator.class, TwitchLiveLoadoutConfig::addedModMessage),
    REMOVE_MODERATOR("channel.moderator.remove", true, ChannelRemoveModerator.class, TwitchLiveLoadoutConfig::removedModMessage),
    HYPE_TRAIN_BEGIN("channel.hype_train.begin", true, HypeTrainBegin.class, TwitchLiveLoadoutConfig::beginHypeTrainMessage),
    HYPE_TRAIN_PROGRESS("channel.hype_train.progress", true, HypeTrainProgress.class, TwitchLiveLoadoutConfig::progressHypeTrainMessage),
    HYPE_TRAIN_END("channel.hype_train.end", true, HypeTrainEnd.class, TwitchLiveLoadoutConfig::endHypeTrainMessage),
    CHARITY_CAMPAIGN_DONATE("channel.charity_campaign.donate", true, CharityCampaignDonate.class, TwitchLiveLoadoutConfig::donateCharityCampaignMessage),
    CHARITY_CAMPAIGN_START("channel.charity_campaign.start", true, CharityCampaignStart.class, TwitchLiveLoadoutConfig::startCharityCampaignMessage),
    CHARITY_CAMPAIGN_PROGRESS("channel.charity_campaign.progress", true, CharityCampaignProgress.class, TwitchLiveLoadoutConfig::progressCharityCampaignMessage),
    CHARITY_CAMPAIGN_STOP("channel.charity_campaign.stop", true, CharityCampaignStop.class, TwitchLiveLoadoutConfig::stopCharityCampaignMessage),

    // disabled for now because it requires extension token
    EXTENSION_BITS_TRANSACTION("extension.bits_transaction.create", false, ExtensionBitsTransaction.class, c -> null),

    // disabled for now because it requires full moderation access (with the token being able to do moderation)
    BAN("channel.ban", false, ChannelBan.class, c -> null),
    UNBAN("channel.unban", false, ChannelUnban.class, c -> null),
    ;

    private final String type;
    private final boolean enabled;
    private final int version;
    private final Class messageClass;
    private final ConfigValueGetter configValueGetter;

    TwitchEventSubType(String type, boolean enabled, int version, Class messageClass, ConfigValueGetter configValueGetter)
    {
        this.type = type;
        this.enabled = enabled;
        this.version = version;
        this.messageClass = messageClass;
        this.configValueGetter = configValueGetter;
    }

    TwitchEventSubType(String type, boolean enabled, Class messageClass, ConfigValueGetter configValueGetter)
    {
        this.type = type;
        this.enabled = enabled;
        this.version = 1;
        this.messageClass = messageClass;
        this.configValueGetter = configValueGetter;
    }

    public static TwitchEventSubType getByType(String rawType)
    {
        for (TwitchEventSubType type : TwitchEventSubType.values())
        {
          if (type.getType().equals(rawType))
          {
              return type;
          }
        }

        return null;
    }

    public interface ConfigValueGetter {
        String execute(TwitchLiveLoadoutConfig config);
    }
}
