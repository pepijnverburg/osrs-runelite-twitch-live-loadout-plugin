package com.twitchliveloadout.fights;

import net.runelite.api.Skill;

import static com.twitchliveloadout.fights.FightStateManager.*;

public enum FightGraphic {
    ICE_BARRAGE(369, Skill.MAGIC, NO_SKILL, MULTI_ANCIENT_ANIMATION_ID, false, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
    ICE_BLITZ(367, Skill.MAGIC, NO_SKILL, SINGLE_ANCIENT_ANIMATION_ID, true, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
    ICE_BURST(363, Skill.MAGIC, NO_SKILL, MULTI_ANCIENT_ANIMATION_ID, false, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),
    ICE_RUSH(361, Skill.MAGIC, NO_SKILL, SINGLE_ANCIENT_ANIMATION_ID, true, FightStatisticEntry.FREEZE, FightStatisticProperty.HIT_DAMAGES),

    BLOOD_BARRAGE(377, Skill.MAGIC, NO_SKILL, MULTI_ANCIENT_ANIMATION_ID, false, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
    BLOOD_BLITZ(375, Skill.MAGIC, NO_SKILL, SINGLE_ANCIENT_ANIMATION_ID, true, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
    BLOOD_BURST(376, Skill.MAGIC, NO_SKILL, MULTI_ANCIENT_ANIMATION_ID, false, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),
    BLOOD_RUSH(373, Skill.MAGIC, NO_SKILL, SINGLE_ANCIENT_ANIMATION_ID, true, FightStatisticEntry.BLOOD_HEAL, FightStatisticProperty.HIT_DAMAGES),

    ENTANGLE(179, Skill.MAGIC, NO_SKILL, ENTANGLE_ANIMATION_ID, true, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_DAMAGES),
    SNARE(180, Skill.MAGIC, NO_SKILL, ENTANGLE_ANIMATION_ID, true, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_DAMAGES),
    BIND(181, Skill.MAGIC, Skill.HITPOINTS, ENTANGLE_ANIMATION_ID, true, FightStatisticEntry.ENTANGLE, FightStatisticProperty.HIT_COUNTERS), // no hitsplat

    // Note that with the interaction required boolean to true splashes on multi-target enemies will not register (e.g. while barraging).
    // However, this is needed because otherwise splashes from other actors have a very high change to trigger false positives.
    // No invalid skill as multi-target spells can both hit and splash on enemies in the same attack.
    SPLASH(85, Skill.MAGIC, NO_SKILL, NO_ANIMATION_ID, true, FightStatisticEntry.SPELL, FightStatisticProperty.MISS_COUNTERS); // no hitsplat

    private final int graphicId;
    private final Skill requiredSkill;
    private final Skill invalidSkill;
    private final int animationId;
    private final boolean interactionRequired;
    private final FightStatisticEntry entry;
    private final FightStatisticProperty property;

    FightGraphic(int graphicId, Skill requiredSkill, Skill invalidSkill, int animationId, boolean interactionRequired, FightStatisticEntry entry, FightStatisticProperty property) {
        this.graphicId = graphicId;
        this.requiredSkill = requiredSkill;
        this.invalidSkill = invalidSkill;
        this.animationId = animationId;
        this.interactionRequired = interactionRequired;
        this.entry = entry;
        this.property = property;
    }

    public int getGraphicId()
    {
        return graphicId;
    }

    public Skill getRequiredSkill()
    {
        return requiredSkill;
    }

    public Skill getInvalidSkill()
    {
        return invalidSkill;
    }

    public int getAnimationId()
    {
        return animationId;
    }

    public boolean isInteractionRequired()
    {
        return interactionRequired;
    }

    public FightStatisticEntry getEntry()
    {
        return entry;
    }

    public FightStatisticProperty getProperty()
    {
        return property;
    }
}
