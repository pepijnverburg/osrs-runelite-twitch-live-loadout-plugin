package com.twitchliveloadout.twitch.eventsub.messages;

import com.google.gson.annotations.SerializedName;

public abstract class WithAuth implements INeedAuth {
    @SerializedName("auth_token")
    public String authToken;

    @Override
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
}
