package com.twitchliveloadout.twitch.eventsub.messages;

public class ChannelBan extends ModeratorUserInfo {
    public String reason;
    public String banned_at;
    public String ends_at;
    public Boolean is_permanent;
}
