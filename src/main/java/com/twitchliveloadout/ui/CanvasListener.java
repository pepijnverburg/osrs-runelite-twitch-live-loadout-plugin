package com.twitchliveloadout.ui;

import lombok.Getter;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.time.Duration;
import java.time.Instant;

public class CanvasListener implements FocusListener {

	@Getter
	private boolean inFocus = false;
	private Instant lastInFocusAt = null;

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
}
