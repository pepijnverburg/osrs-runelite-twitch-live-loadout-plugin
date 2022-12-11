package com.twitchliveloadout.twitch;

import lombok.Getter;

public enum TwitchPubSubTargetType {
	BROADCAST("broadcast"),
	GLOBAL("global"),
	;

	@Getter
	private final String target;

	TwitchPubSubTargetType(String target) {
		this.target = target;
	}
}
