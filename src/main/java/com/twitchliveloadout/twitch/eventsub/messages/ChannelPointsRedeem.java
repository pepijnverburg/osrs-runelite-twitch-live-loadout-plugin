package com.twitchliveloadout.twitch.eventsub.messages;

public class ChannelPointsRedeem extends BaseUserInfo {
    public String user_input;
    public String status;
    public ChannelPointsReward reward;
    public String redeemed_at;
}
