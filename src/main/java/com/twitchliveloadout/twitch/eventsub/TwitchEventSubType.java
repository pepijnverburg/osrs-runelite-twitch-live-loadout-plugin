package com.twitchliveloadout.twitch.eventsub;

import com.twitchliveloadout.twitch.eventsub.messages.*;
import lombok.Getter;

/**
 * Reference: https://dev.twitch.tv/docs/eventsub/eventsub-subscription-types/
 */
@Getter
public enum TwitchEventSubType {
    CHANNEL_POINTS_REDEEM("channel.channel_points_custom_reward_redemption.add", true, ChannelPointsRedeem.class),
    START_SUBSCRIPTION("channel.subscribe",true, ChannelStartSubscription.class),
    CONTINUE_SUBSCRIPTION("channel.subscription.message", true, ChannelContinueSubscription.class),
    GIFT_SUBSCRIPTION("channel.subscription.gift", true, ChannelGiftSubscription.class),
    RAID("channel.raid", true, ChannelRaid.class),
    FOLLOW("channel.follow", true, 2, ChannelFollow.class),
    ADD_MODERATOR("channel.moderator.add", true, ChannelAddModerator.class),
    REMOVE_MODERATOR("channel.moderator.remove", true, ChannelRemoveModerator.class),
    HYPE_TRAIN_BEGIN("channel.hype_train.begin", true, HypeTrainBegin.class),
    HYPE_TRAIN_PROGRESS("channel.hype_train.progress", true, HypeTrainProgress.class),
    HYPE_TRAIN_END("channel.hype_train.end", true, HypeTrainEnd.class),

    // disabled for now because it requires extension token
    EXTENSION_BITS_TRANSACTION("extension.bits_transaction.create", false, ExtensionBitsTransaction.class),

    // disabled for now because it requires full moderation access (with the token being able to do moderation)
    BAN("channel.ban", false, ChannelBan.class),
    UNBAN("channel.unban", false, ChannelUnban.class),
    ;

    private final String type;
    private final boolean enabled;
    private final int version;
    private final Class messageClass;

    TwitchEventSubType(String type, boolean enabled, int version, Class messageClass)
    {
        this.type = type;
        this.enabled = enabled;
        this.version = version;
        this.messageClass = messageClass;
    }

    TwitchEventSubType(String type, boolean enabled, Class messageClass)
    {
        this.type = type;
        this.enabled = enabled;
        this.version = 1;
        this.messageClass = messageClass;
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
}
