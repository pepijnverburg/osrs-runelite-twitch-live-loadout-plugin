package com.twitchliveloadout.twitch.eventsub.messages;

public class ChannelStartSubscription extends BaseUserInfo {
    public String tier;
    public Boolean is_gift; // always false?
}
