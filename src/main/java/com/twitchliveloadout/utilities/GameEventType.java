package com.twitchliveloadout.utilities;

import lombok.Getter;

@Getter
public enum GameEventType {
    LOGIN("login", "Login", "Just logged in and already getting a '{productName}'?"),
    LEVEL_UP("level-up", "Level Up", "A level up along with a {productName}!"),
    BOSS_KILL("boss-kill", "Boss Kill", null),
    RAID_COMPLETION("raid-completion", "Raid Completion", null),
    PET_DROP("pet-drop", "Pet Drop", "A pet drop along with a {productName}!"),
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
