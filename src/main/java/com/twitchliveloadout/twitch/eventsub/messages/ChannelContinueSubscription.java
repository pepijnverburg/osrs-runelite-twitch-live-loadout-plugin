package com.twitchliveloadout.twitch.eventsub.messages;

public class ChannelContinueSubscription extends BaseUserInfo {
    public String tier;
    public ChannelMessage message;
    public Integer cumulative_months;
    public Integer streak_months;
    public Integer duration_months;
}
