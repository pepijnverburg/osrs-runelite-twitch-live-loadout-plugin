package com.twitchliveloadout.twitch.eventsub;

import lombok.Getter;

/**
 * Reference: https://dev.twitch.tv/docs/eventsub/eventsub-subscription-types/
 */
@Getter
public enum TwitchEventSubType {
    EXTENSION_BITS_TRANSACTION("extension.bits_transaction.create"),
    CHANNEL_POINTS_REDEEM("channel.channel_points_custom_reward_redemption.add"),
    NEW_SUBSCRIPTION("channel.subscribe"),
    CONTINUE_SUBSCRIPTION("channel.subscription.message"),
    GIFT_SUBSCRIPTION("channel.subscription.gift"),
    RAID("channel.raid"),
    BAN("channel.ban"),
    FOLLOW("channel.follow"),
    UNBAN("channel.unban"),
    ADD_MODERATOR("channel.moderator.add"),
    REMOVE_MODERATOR("channel.moderator.remove"),
    ;

    private final String type;

    private final int version;

    TwitchEventSubType(String type, int version) {
        this.type = type;
        this.version = version;
    }

    TwitchEventSubType(String type) {
        this.type = type;
        this.version = 1;
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
