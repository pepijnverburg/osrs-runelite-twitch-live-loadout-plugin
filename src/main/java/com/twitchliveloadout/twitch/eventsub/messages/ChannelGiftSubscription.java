package com.twitchliveloadout.twitch.eventsub.messages;

public class ChannelGiftSubscription extends BaseUserInfo {
    public Integer total;
    public String tier;
    public Integer cumulative_total;
    public Boolean is_anonymous;
}
