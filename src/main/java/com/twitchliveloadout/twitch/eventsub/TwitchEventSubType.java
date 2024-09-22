package com.twitchliveloadout.twitch.eventsub;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.eventsub.messages.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Reference: https://dev.twitch.tv/docs/eventsub/eventsub-subscription-types/
 */
@Getter
@Slf4j
public enum TwitchEventSubType {
    CHANNEL_POINTS_REDEEM("channel.channel_points_custom_reward_redemption.add", "Channel Points Redeem", true, 1, ChannelPointsRedeem.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::channelPointsRedeemEventMessage, (plugin, config, message) -> config.channelPointsRedeemEventMessageEnabled()),
    START_SUBSCRIPTION(
        "channel.subscribe", "Sub", true, 1, ChannelStartSubscription.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::subscribeEventMessage,
        (plugin, config, message) -> {
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
    CONTINUE_SUBSCRIPTION("channel.subscription.message", "Resub", true, 1, ChannelContinueSubscription.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::resubscribeEventMessage, (plugin, config, message) -> config.resubscribeEventMessageEnabled()),
    GIFT_SUBSCRIPTION("channel.subscription.gift", "Gift", true, 1, ChannelGiftSubscription.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::giftSubscriptionEventMessage, (plugin, config, message) -> config.giftSubscriptionEventMessageEnabled()),
    RAID("channel.raid", "Raid", true, 1, ChannelRaid.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::raidEventMessage, (plugin, config, message) -> config.raidEventMessageEnabled()),
    FOLLOW(
    "channel.follow", "Follow", true, 2, ChannelFollow.class,
        (plugin, config, message) -> {
            ChannelFollow followMessage = (ChannelFollow) message;
            String followerId = followMessage.user_id;
            TwitchEventSubListener twitchEventSubListener = plugin.getTwitchEventSubListener();

            // guard: check if this follower has already followed recently
            if (twitchEventSubListener.hasHandledFollowerId(followerId))
            {
                log.info("An EventSub follow event was skipped, because the user has already followed the channel recently, follower username: "+ followMessage.user_name);
                return false;
            }

            twitchEventSubListener.handleFollowerId(followerId);
            return true;
        },
        TwitchLiveLoadoutConfig::followEventMessage, (plugin, config, message) -> config.followEventMessageEnabled()),
    ADD_MODERATOR("channel.moderator.add", "Add Mod", true, 1, ChannelAddModerator.class,  (p, c, m) -> true,TwitchLiveLoadoutConfig::addedModMessage, (plugin, config, message) -> config.addedModMessageEnabled()),
    REMOVE_MODERATOR("channel.moderator.remove", "Remove Mod", true, 1, ChannelRemoveModerator.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::removedModMessage, (plugin, config, message) -> config.removedModMessageEnabled()),
    HYPE_TRAIN_BEGIN("channel.hype_train.begin", "Start Hype Train", true, 1, HypeTrainBegin.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::beginHypeTrainMessage, (plugin, config, message) -> config.beginHypeTrainMessageEnabled()),
    HYPE_TRAIN_PROGRESS("channel.hype_train.progress", "Increase Hype Train", true, 1, HypeTrainProgress.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::progressHypeTrainMessage, (plugin, config, message) -> config.progressHypeTrainMessageEnabled()),
    HYPE_TRAIN_END("channel.hype_train.end", "Stop Hype Train", true, 1, HypeTrainEnd.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::endHypeTrainMessage, (plugin, config, message) -> config.endHypeTrainMessageEnabled()),
    CHARITY_CAMPAIGN_DONATE("channel.charity_campaign.donate", "Charity Donation", true, 1, CharityCampaignDonate.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::donateCharityCampaignMessage, (plugin, config, message) -> config.donateCharityCampaignMessageEnabled()),
    CHARITY_CAMPAIGN_START("channel.charity_campaign.start", "Start Charity Campaign", true, 1, CharityCampaignStart.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::startCharityCampaignMessage, (plugin, config, message) -> config.startCharityCampaignMessageEnabled()),
    CHARITY_CAMPAIGN_PROGRESS("channel.charity_campaign.progress", "Progress Charity Campaign", true, 1, CharityCampaignProgress.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::progressCharityCampaignMessage, (plugin, config, message) -> config.progressCharityCampaignMessageEnabled()),
    CHARITY_CAMPAIGN_STOP("channel.charity_campaign.stop", "Stop Charity Campaign", true, 1, CharityCampaignStop.class, (p, c, m) -> true, TwitchLiveLoadoutConfig::stopCharityCampaignMessage, (plugin, config, message) -> config.stopCharityCampaignMessageEnabled()),

    // disabled for now because it requires extension token
    EXTENSION_BITS_TRANSACTION("extension.bits_transaction.create", "Bits Donation", false, 1, ExtensionBitsTransaction.class, (p, c, m) -> true, c -> null, (p, c, m) -> false),

    // disabled for now because it requires full moderation access, which might a bit too much (with the token being able to do moderation)
    BAN("channel.ban", "Ban Viewer", false, 1, ChannelBan.class, (p, c, m) -> true, c -> null, (p, c, m) -> false),
    UNBAN("channel.unban", "Unban Viewer", false, 1, ChannelUnban.class, (p, c, m) -> true, c -> null, (p, c, m) -> false),
    ;

    private final String type;
    private final String name;
    private final boolean enabled;
    private final int version;
    private final BooleanConfigValueGetter shouldHandleEventGetter;
    private final Class<? extends BaseMessage> messageClass;
    private final StringConfigValueGetter messageGetter;
    private final BooleanConfigValueGetter defaultMessageEnabledGetter;

    TwitchEventSubType(String type, String name, boolean enabled, int version, Class<? extends BaseMessage> messageClass, BooleanConfigValueGetter shouldHandleEventGetter, StringConfigValueGetter messageGetter, BooleanConfigValueGetter defaultMessageEnabledGetter)
    {
        this.type = type;
        this.name = name;
        this.enabled = enabled;
        this.version = version;
        this.shouldHandleEventGetter = shouldHandleEventGetter;
        this.messageClass = messageClass;
        this.messageGetter = messageGetter;
        this.defaultMessageEnabledGetter = defaultMessageEnabledGetter;
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
        Boolean execute(TwitchLiveLoadoutPlugin plugin, TwitchLiveLoadoutConfig config, BaseMessage eventSubMessage);
    }
}
