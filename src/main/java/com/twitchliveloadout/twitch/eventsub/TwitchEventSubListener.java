package com.twitchliveloadout.twitch.eventsub;

import com.google.gson.JsonObject;

public interface TwitchEventSubListener {
    public void onReady();
    public void onEvent(String eventType, JsonObject eventPayload);
}
