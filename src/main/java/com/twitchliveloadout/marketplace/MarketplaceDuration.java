package com.twitchliveloadout.marketplace;

import java.time.Duration;

public class MarketplaceDuration {
	public static String humanizeDurationMs(long ms)
	{
		long seconds = ms / 1000;
		return humanizeDuration(Duration.ofSeconds(seconds));
	}

	public static String humanizeDurationRounded(Duration duration)
	{
		int nano = duration.getNano();
		Duration roundedDuration = Duration.ofSeconds(duration.getSeconds() + (nano > 1000 ? 1 : 0));

		return humanizeDuration(roundedDuration);
	}

	public static String humanizeDuration(Duration duration)
	{

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
