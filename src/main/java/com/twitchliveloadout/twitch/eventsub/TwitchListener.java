package com.twitchliveloadout.twitch.eventsub;

public interface TwitchListener {
    void rewardRedeemed(String redeemName, String message);
}
