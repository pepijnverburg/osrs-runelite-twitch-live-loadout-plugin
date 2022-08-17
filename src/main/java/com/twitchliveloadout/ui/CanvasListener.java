package com.twitchliveloadout.ui;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import lombok.Getter;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.time.Duration;
import java.time.Instant;

public class CanvasListener implements FocusListener {

	private static final long MAX_MIN_FOCUS_DURATION_MS = 30 * 1000; // ms

	private final TwitchLiveLoadoutConfig config;

	@Getter
	private boolean inFocus = false;
	private Instant lastInFocusAt = null;

	public CanvasListener(TwitchLiveLoadoutConfig config)
	{
		this.config = config;
	}

	@Override
	public void focusGained(FocusEvent event)
	{
		if (inFocus)
		{
			return;
		}

		inFocus = true;
		lastInFocusAt = Instant.now();
	}

	@Override
	public void focusLost(FocusEvent event)
	{
		if (!inFocus)
		{
			return;
		}

		inFocus = false;
	}

	public long getInFocusDurationMs()
	{

		// guard: check if this window is in focus and was ever in focus
		if (!inFocus || lastInFocusAt == null)
		{
			return 0;
		}

		final Instant now = Instant.now();
		final long duration = Duration.between(lastInFocusAt, now).toMillis();

		return duration;
	}

	public boolean isInFocusLongEnough()
	{

		// guard: check if the check is enabled
		if (!config.minWidowFocusTimeEnabled())
		{
			return true;
		}

		final long focusDurationMs = getInFocusDurationMs();
		long minFocusDurationMs = config.minWindowFocusTime() * 1000;

		if (minFocusDurationMs > MAX_MIN_FOCUS_DURATION_MS)
		{
			minFocusDurationMs = MAX_MIN_FOCUS_DURATION_MS;
		}

		return focusDurationMs >= minFocusDurationMs;
	}
}
