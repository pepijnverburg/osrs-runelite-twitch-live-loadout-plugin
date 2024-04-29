package com.twitchliveloadout.twitch.eventsub;

import com.google.gson.JsonObject;

public interface TwitchEventSubListener {
    public void onReady(String sessionId);
    public void onEvent(String eventType, JsonObject eventPayload);
}
