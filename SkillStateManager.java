package net.runelite.client.plugins.twitchliveloadout;

import net.runelite.api.Client;
import net.runelite.api.events.StatChanged;

public class SkillStateManager {
	private final TwitchState twitchState;
	private final Client client;

	public SkillStateManager(TwitchState twitchState, Client client)
	{
		this.twitchState = twitchState;
		this.client = client;
	}

	public void onStatChanged(StatChanged event)
	{
		final int[] skillExperiences = client.getSkillExperiences();
		final int[] boostedSkillLevels = client.getBoostedSkillLevels();

		twitchState.setSkillExperiences(skillExperiences);
		twitchState.setBoostedSkillLevels(boostedSkillLevels);
	}
}
