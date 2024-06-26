package com.twitchliveloadout.twitch.eventsub;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.twitch.eventsub.messages.*;
import lombok.Getter;

/**
 * Reference: https://dev.twitch.tv/docs/eventsub/eventsub-subscription-types/
 */
@Getter
public enum TwitchEventSubType {
    CHANNEL_POINTS_REDEEM("channel.channel_points_custom_reward_redemption.add", "Channel Points Redeem", true, 1, ChannelPointsRedeem.class, TwitchLiveLoadoutConfig::channelPointsRedeemEventMessage, (config, message) -> config.channelPointsRedeemEventMessageEnabled()),
    START_SUBSCRIPTION(
        "channel.subscribe", "Sub", true, 1, ChannelStartSubscription.class, TwitchLiveLoadoutConfig::subscribeEventMessage,
        (config, message) -> {
            boolean isMessageEnabled = config.subscribeEventMessageEnabled();

            // guard: also check whether the sub should be shown when it was gifted
            if (message instanceof ChannelStartSubscription channelStartSubscription) {
                boolean isGifted = channelStartSubscription.is_gift;
                boolean shouldShowMessageOnGifted = config.subscribeEventMessageOnGiftEnabled();

                return isMessageEnabled && (!isGifted || shouldShowMessageOnGifted);
            }

            return isMessageEnabled;
        }
    ),
    CONTINUE_SUBSCRIPTION("channel.subscription.message", "Resub", true, 1, ChannelContinueSubscription.class, TwitchLiveLoadoutConfig::resubscribeEventMessage, (config, message) -> config.resubscribeEventMessageEnabled()),
    GIFT_SUBSCRIPTION("channel.subscription.gift", "Gift", true, 1, ChannelGiftSubscription.class, TwitchLiveLoadoutConfig::giftSubscriptionEventMessage, (config, message) -> config.giftSubscriptionEventMessageEnabled()),
    RAID("channel.raid", "Raid", true, 1, ChannelRaid.class, TwitchLiveLoadoutConfig::raidEventMessage, (config, message) -> config.raidEventMessageEnabled()),
    FOLLOW("channel.follow", "Follow", true, 2, ChannelFollow.class, TwitchLiveLoadoutConfig::followEventMessage, (config, message) -> config.followEventMessageEnabled()),
    ADD_MODERATOR("channel.moderator.add", "Add Mod", true, 1, ChannelAddModerator.class, TwitchLiveLoadoutConfig::addedModMessage, (config, message) -> config.addedModMessageEnabled()),
    REMOVE_MODERATOR("channel.moderator.remove", "Remove Mod", true, 1, ChannelRemoveModerator.class, TwitchLiveLoadoutConfig::removedModMessage, (config, message) -> config.removedModMessageEnabled()),
    HYPE_TRAIN_BEGIN("channel.hype_train.begin", "Start Hype Train", true, 1, HypeTrainBegin.class, TwitchLiveLoadoutConfig::beginHypeTrainMessage, (config, message) -> config.beginHypeTrainMessageEnabled()),
    HYPE_TRAIN_PROGRESS("channel.hype_train.progress", "Increase Hype Train", true, 1, HypeTrainProgress.class, TwitchLiveLoadoutConfig::progressHypeTrainMessage, (config, message) -> config.progressHypeTrainMessageEnabled()),
    HYPE_TRAIN_END("channel.hype_train.end", "Stop Hype Train", true, 1, HypeTrainEnd.class, TwitchLiveLoadoutConfig::endHypeTrainMessage, (config, message) -> config.endHypeTrainMessageEnabled()),
    CHARITY_CAMPAIGN_DONATE("channel.charity_campaign.donate", "Charity Donation", true, 1, CharityCampaignDonate.class, TwitchLiveLoadoutConfig::donateCharityCampaignMessage, (config, message) -> config.donateCharityCampaignMessageEnabled()),
    CHARITY_CAMPAIGN_START("channel.charity_campaign.start", "Start Charity Campaign", true, 1, CharityCampaignStart.class, TwitchLiveLoadoutConfig::startCharityCampaignMessage, (config, message) -> config.startCharityCampaignMessageEnabled()),
    CHARITY_CAMPAIGN_PROGRESS("channel.charity_campaign.progress", "Progress Charity Campaign", true, 1, CharityCampaignProgress.class, TwitchLiveLoadoutConfig::progressCharityCampaignMessage, (config, message) -> config.progressCharityCampaignMessageEnabled()),
    CHARITY_CAMPAIGN_STOP("channel.charity_campaign.stop", "Stop Charity Campaign", true, 1, CharityCampaignStop.class, TwitchLiveLoadoutConfig::stopCharityCampaignMessage, (config, message) -> config.stopCharityCampaignMessageEnabled()),

    // disabled for now because it requires extension token
    EXTENSION_BITS_TRANSACTION("extension.bits_transaction.create", "Bits Donation", false, 1, ExtensionBitsTransaction.class, c -> null, (c, m) -> false),

    // disabled for now because it requires full moderation access (with the token being able to do moderation)
    BAN("channel.ban", "Ban Viewer", false, 1, ChannelBan.class, c -> null, (c, m) -> false),
    UNBAN("channel.unban", "Unban Viewer", false, 1, ChannelUnban.class, c -> null, (c, m) -> false),
    ;

    private final String type;
    private final String name;
    private final boolean enabled;
    private final int version;
    private final Class<? extends BaseMessage> messageClass;
    private final StringConfigValueGetter messageGetter;
    private final BooleanConfigValueGetter messageEnabledGetter;

    TwitchEventSubType(String type, String name, boolean enabled, int version, Class<? extends BaseMessage> messageClass, StringConfigValueGetter messageGetter, BooleanConfigValueGetter messageEnabledGetter)
    {
        this.type = type;
        this.name = name;
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
        Boolean execute(TwitchLiveLoadoutConfig config, BaseMessage eventSubMessage);
    }
}
