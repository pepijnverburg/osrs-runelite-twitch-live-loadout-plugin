package com.twitchliveloadout.twitch.eventsub;

import com.google.gson.JsonObject;

public interface SocketListener {
    public void onReady();
    public void onMessage(String message, JsonObject dataObject);
}
