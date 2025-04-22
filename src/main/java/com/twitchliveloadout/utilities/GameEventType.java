package com.twitchliveloadout.utilities;

import lombok.Getter;

@Getter
public enum GameEventType {
    LOGIN("login", "Login", "Just logged in and already trouble?"),
    ;

    private final String id;

    private final String name;
    private final String message;

    GameEventType(String id, String name, String message)
    {
        this.id = id;
        this.name = name;
        this.message = message;
    }
}
