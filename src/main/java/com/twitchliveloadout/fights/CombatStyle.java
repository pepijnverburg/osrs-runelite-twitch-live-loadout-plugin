package com.twitchliveloadout.fights;

import lombok.Getter;
import net.runelite.api.Skill;

public enum CombatStyle {
    MELEE("melee"),
    RANGED("ranged"),
    MAGIC("magic"),
    ;

    @Getter
    private final String key;

    CombatStyle(String key)
    {
        this.key = key;
    }
}
