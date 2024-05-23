package com.twitchliveloadout.fights;

public enum ActorType {
    NPC("npc", "npc"),
    PLAYER("player", "player"),
    GAME_OBJECT("gameObject", "gameObject"),
    LOCAL_PLAYER("localPlayer", "self");

    private final String key;
    private final String name;

    ActorType(String key, String name) {
        this.key = key;
        this.name = name;
    }

    public String getKey()
    {
        return key;
    }

    public String getName()
    {
        return name;
    }
}
