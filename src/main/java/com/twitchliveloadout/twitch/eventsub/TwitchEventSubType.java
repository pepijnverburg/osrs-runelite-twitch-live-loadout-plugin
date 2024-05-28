package com.twitchliveloadout.twitch.eventsub;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.twitch.eventsub.messages.*;
import lombok.Getter;

/**
 * Reference: https://dev.twitch.tv/docs/eventsub/eventsub-subscription-types/
 */
@Getter
public enum TwitchEventSubType {
    CHANNEL_POINTS_REDEEM("channel.channel_points_custom_reward_redemption.add", true, 1, ChannelPointsRedeem.class, TwitchLiveLoadoutConfig::channelPointsRedeemEventMessage, TwitchLiveLoadoutConfig::channelPointsRedeemEventMessageEnabled),
    START_SUBSCRIPTION("channel.subscribe",true, 1, ChannelStartSubscription.class, TwitchLiveLoadoutConfig::newSubscriptionEventMessage, TwitchLiveLoadoutConfig::newSubscriptionEventMessageEnabled),
    CONTINUE_SUBSCRIPTION("channel.subscription.message", true, 1, ChannelContinueSubscription.class, TwitchLiveLoadoutConfig::newResubscriptionEventMessage, TwitchLiveLoadoutConfig::newResubscriptionEventMessageEnabled),
    GIFT_SUBSCRIPTION("channel.subscription.gift", true, 1, ChannelGiftSubscription.class, TwitchLiveLoadoutConfig::giftSubscriptionEventMessage, TwitchLiveLoadoutConfig::giftSubscriptionEventMessageEnabled),
    RAID("channel.raid", true, 1, ChannelRaid.class, TwitchLiveLoadoutConfig::raidEventMessage, TwitchLiveLoadoutConfig::raidEventMessageEnabled),
    FOLLOW("channel.follow", true, 2, ChannelFollow.class, TwitchLiveLoadoutConfig::followEventMessage, TwitchLiveLoadoutConfig::followEventMessageEnabled),
    ADD_MODERATOR("channel.moderator.add", true, 1, ChannelAddModerator.class, TwitchLiveLoadoutConfig::addedModMessage, TwitchLiveLoadoutConfig::addedModMessageEnabled),
    REMOVE_MODERATOR("channel.moderator.remove", true, 1, ChannelRemoveModerator.class, TwitchLiveLoadoutConfig::removedModMessage, TwitchLiveLoadoutConfig::removedModMessageEnabled),
    HYPE_TRAIN_BEGIN("channel.hype_train.begin", true, 1, HypeTrainBegin.class, TwitchLiveLoadoutConfig::beginHypeTrainMessage, TwitchLiveLoadoutConfig::beginHypeTrainMessageEnabled),
    HYPE_TRAIN_PROGRESS("channel.hype_train.progress", true, 1, HypeTrainProgress.class, TwitchLiveLoadoutConfig::progressHypeTrainMessage, TwitchLiveLoadoutConfig::progressHypeTrainMessageEnabled),
    HYPE_TRAIN_END("channel.hype_train.end", true, 1, HypeTrainEnd.class, TwitchLiveLoadoutConfig::endHypeTrainMessage, TwitchLiveLoadoutConfig::endHypeTrainMessageEnabled),
    CHARITY_CAMPAIGN_DONATE("channel.charity_campaign.donate", true, 1, CharityCampaignDonate.class, TwitchLiveLoadoutConfig::donateCharityCampaignMessage, TwitchLiveLoadoutConfig::donateCharityCampaignMessageEnabled),
    CHARITY_CAMPAIGN_START("channel.charity_campaign.start", true, 1, CharityCampaignStart.class, TwitchLiveLoadoutConfig::startCharityCampaignMessage, TwitchLiveLoadoutConfig::startCharityCampaignMessageEnabled),
    CHARITY_CAMPAIGN_PROGRESS("channel.charity_campaign.progress", true, 1, CharityCampaignProgress.class, TwitchLiveLoadoutConfig::progressCharityCampaignMessage, TwitchLiveLoadoutConfig::progressCharityCampaignMessageEnabled),
    CHARITY_CAMPAIGN_STOP("channel.charity_campaign.stop", true, 1, CharityCampaignStop.class, TwitchLiveLoadoutConfig::stopCharityCampaignMessage, TwitchLiveLoadoutConfig::stopCharityCampaignMessageEnabled),

    // disabled for now because it requires extension token
    EXTENSION_BITS_TRANSACTION("extension.bits_transaction.create", false, 1, ExtensionBitsTransaction.class, c -> null, c -> false),

    // disabled for now because it requires full moderation access (with the token being able to do moderation)
    BAN("channel.ban", false, 1, ChannelBan.class, c -> null, c -> false),
    UNBAN("channel.unban", false, 1, ChannelUnban.class, c -> null, c -> false),
    ;

    private final String type;
    private final boolean enabled;
    private final int version;
    private final Class messageClass;
    private final StringConfigValueGetter messageGetter;
    private final BooleanConfigValueGetter messageEnabledGetter;

    TwitchEventSubType(String type, boolean enabled, int version, Class messageClass, StringConfigValueGetter messageGetter, BooleanConfigValueGetter messageEnabledGetter)
    {
        this.type = type;
        this.enabled = enabled;
        this.version = version;
        this.messageClass = messageClass;
        this.messageGetter = messageGetter;
        this.messageEnabledGetter = messageEnabledGetter;
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

    public interface StringConfigValueGetter {
        String execute(TwitchLiveLoadoutConfig config);
    }

    public interface BooleanConfigValueGetter {
        Boolean execute(TwitchLiveLoadoutConfig config);
    }
}
