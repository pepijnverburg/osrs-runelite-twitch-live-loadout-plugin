package com.twitchliveloadout.skills;

import com.twitchliveloadout.twitch.TwitchState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.StatChanged;

@Slf4j
public class SkillStateManager {
	private final TwitchState twitchState;
	private final Client client;

	public SkillStateManager(TwitchState twitchState, Client client)
	{
		this.twitchState = twitchState;
		this.client = client;
	}

	public void updateSkills()
	{
		try {
			final int[] skillExperiences = client.getSkillExperiences();
			final int[] boostedSkillLevels = client.getBoostedSkillLevels();

			twitchState.setSkillExperiences(skillExperiences);
			twitchState.setBoostedSkillLevels(boostedSkillLevels);
		} catch (Exception exception) {
			log.warn("An error occurred when updating skills: ", exception);
		}
	}
}
