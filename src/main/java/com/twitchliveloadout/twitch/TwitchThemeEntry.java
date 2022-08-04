package com.twitchliveloadout.twitch;

import lombok.Getter;

public enum TwitchThemeEntry {
	LIGHT("light"),
	DARK("dark");

	@Getter
	private final String key;

	TwitchThemeEntry(String key)
	{
		this.key = key;
	}
}
