package com.twitchliveloadout.ui;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import lombok.Getter;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.time.Duration;
import java.time.Instant;

public class CanvasListener implements FocusListener {

	private static final long MAX_IN_FOCUS_DURATION_MS = 30 * 1000; // ms

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
		inFocus = true;
		lastInFocusAt = Instant.now();
	}

	@Override
	public void focusLost(FocusEvent event)
	{
		inFocus = false;
	}

	public long getInFocusDurationMs()
	{

		// guard: check if this window was ever in focus
		if (lastInFocusAt == null)
		{
			return 0;
		}

		final Instant now = Instant.now();
		final long duration = Duration.between(lastInFocusAt, now).toMillis();

		return duration;
	}

	public boolean isInFocusLongEnough()
	{
		final long focusDurationMs = getInFocusDurationMs();
		long minFocusDurationMs = config.minWindowFocusTime() * 1000;

		if (minFocusDurationMs > MAX_IN_FOCUS_DURATION_MS)
		{
			minFocusDurationMs = MAX_IN_FOCUS_DURATION_MS;
		}

		return focusDurationMs >= minFocusDurationMs;
	}
}
