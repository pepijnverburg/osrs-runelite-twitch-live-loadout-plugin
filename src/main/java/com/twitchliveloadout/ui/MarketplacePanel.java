package com.twitchliveloadout.ui;

import com.google.gson.JsonObject;
import com.twitchliveloadout.TwitchApi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class MarketplacePanel extends JPanel
{
	private final static String DEFAULT_TEXT_COLOR = "#ffffff";
	private final static int WARNING_BEFORE_EXPIRY = 60 * 5; // in seconds

	private final GridBagConstraints constraints = new GridBagConstraints();
	private final JPanel wrapper = new JPanel(new GridBagLayout());

	private final TextPanel statusPanel = new TextPanel("Current Status", "N/A");
	private final TextPanel authPanel = new TextPanel("Twitch Token Validity", "N/A");

	private final TwitchApi twitchApi;

	public MarketplacePanel(TwitchApi twitchApi)
	{
		super(new GridBagLayout());

		this.twitchApi = twitchApi;
	}

	public void rebuild()
	{

	}
}
