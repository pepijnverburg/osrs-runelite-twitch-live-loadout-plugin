package com.twitchliveloadout.ui;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import lombok.Getter;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.time.Duration;
import java.time.Instant;

public class CanvasListener implements FocusListener {

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
		enableFocus();
	}

	@Override
	public void focusLost(FocusEvent event)
	{
		disableFocus();
	}

	public void enableFocus()
	{
		if (inFocus)
		{
			return;
		}

		inFocus = true;
		lastInFocusAt = Instant.now();
	}

	public void disableFocus()
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
}
