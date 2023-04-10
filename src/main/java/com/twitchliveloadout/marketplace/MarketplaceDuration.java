package com.twitchliveloadout.marketplace;

import java.time.Duration;

public class MarketplaceDuration {
	public static String humanizeDuration(Duration duration) {

		// guard: make sure the duration is valid
		if (duration == null)
		{
			return "0s";
		}

		return duration.toString()
			.substring(2)
			.replaceAll("(\\d[HMS])(?!$)", "$1 ")
			.toLowerCase();
	}
}
