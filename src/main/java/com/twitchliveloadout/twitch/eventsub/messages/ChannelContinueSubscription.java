package com.twitchliveloadout.twitch.eventsub.messages;

public class ChannelContinueSubscription extends BaseUserInfo {
    public String tier;
    public ChannelMessage message;
    public int cumulative_months;
    public int streak_months;
    public int duration_months;
}
