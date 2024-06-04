package com.twitchliveloadout.twitch;

import lombok.Getter;

public enum TwitchVisibilityEntry {
    ALWAYS("normal"),
    HOVER("hover");

    @Getter
    private final String key;

    TwitchVisibilityEntry(String key)
    {
        this.key = key;
    }
}
