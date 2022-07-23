package com.twitchliveloadout.ui;

import com.google.gson.JsonObject;
import com.twitchliveloadout.twitch.TwitchApi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ConnectivityPanel extends JPanel
{
	private final static String DEFAULT_TEXT_COLOR = "#ffffff";
	private final static String ERROR_TEXT_COLOR = "#ff0000";
	private final static int WARNING_BEFORE_EXPIRY = 60 * 5; // in seconds

	private final GridBagConstraints constraints = new GridBagConstraints();
	private final JPanel wrapper = new JPanel(new GridBagLayout());

	private final TextPanel statusPanel = new TextPanel("Current Status", "N/A");
	private final TextPanel authPanel = new TextPanel("Twitch Token Validity", "N/A");
	private final TextPanel rateLimitPanel = new TextPanel("Twitch API Limit", "N/A");
	private final TextPanel statePanel = new TextPanel("Loadout State Size", "N/A");

	private final TwitchApi twitchApi;

	public ConnectivityPanel(TwitchApi twitchApi)
	{
		super(new GridBagLayout());

		this.twitchApi = twitchApi;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		// add titles
		wrapper.add(statusPanel, constraints);
		constraints.gridy++;
		wrapper.add(authPanel, constraints);
		constraints.gridy++;
		wrapper.add(rateLimitPanel, constraints);
		constraints.gridy++;
		wrapper.add(statePanel, constraints);
		constraints.gridy++;

		add(wrapper, BorderLayout.NORTH);
	}

	public void rebuild()
	{
		final long unixTimestamp = System.currentTimeMillis() / 1000L;
		final int rateLimitRemaining = twitchApi.getLastRateLimitRemaining();

		String statusText = twitchApi.getLastResponseMessage();
		final int responseCode = twitchApi.getLastResponseCode();
		String statusColor = DEFAULT_TEXT_COLOR;

		String authText = "No valid Twitch Token.";
		String authColor = ERROR_TEXT_COLOR;
		long tokenExpiry = 0;

		String rateLimitText = "There are "+ rateLimitRemaining +" requests available before hitting the Twitch API rate limit.";
		String rateLimitColor = DEFAULT_TEXT_COLOR;

		try {
			final JsonObject decodedToken = twitchApi.getDecodedToken();
			tokenExpiry = decodedToken.get("exp").getAsLong();
			final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMMM YYYY, HH:mm");
			final String tokenExpiryFormatted = Instant.ofEpochSecond(tokenExpiry).atZone(ZoneId.systemDefault()).format(formatter);
			final long secondsUntilExpired = tokenExpiry - unixTimestamp;

			// check if the token is still valid
			if (secondsUntilExpired > 0)
			{
				authText = "Twitch Token will expire at: <br/>"+ tokenExpiryFormatted;
				authColor = (secondsUntilExpired > WARNING_BEFORE_EXPIRY ? DEFAULT_TEXT_COLOR : ERROR_TEXT_COLOR);
			}
			else
			{
				authText = "Token has expired at: <br/>"+ tokenExpiryFormatted;
			}
		} catch (Exception exception) {
			// empty, ignore any errors
		}

		String state = twitchApi.getLastCompressedState();
		byte[] stateBytes = state.getBytes();
		float stateUsagePercentage = ((float) stateBytes.length) / ((float) TwitchApi.MAX_PAYLOAD_SIZE) * 100;
		String stateText = String.format("%.2f", stateUsagePercentage) +"% used of Twitch storage.";
		String stateColor = DEFAULT_TEXT_COLOR;

		if (twitchApi.isErrorResponseCode(responseCode))
		{
			statusText += "<br/><br/>An error occurred wth code: "+ responseCode;
			statusColor = ERROR_TEXT_COLOR;
		}

		if (stateUsagePercentage >= 100)
		{
			stateText += "<br/><br/>An error occurred: state is too large to send to Twitch. Please disable some synced information via the plugin settings in RuneLite.";
			stateColor = ERROR_TEXT_COLOR;
		}

		if (rateLimitRemaining <= 10)
		{
			rateLimitText += "<br/><br/>Which is almost exceeded! This should not happen. Please contact the plugin maintainer via GitHub or support@osrs-tools.com.";
			rateLimitColor = ERROR_TEXT_COLOR;
		}

		statusPanel.setText(getTextInColor(statusText, statusColor));
		authPanel.setText(getTextInColor(authText, authColor));
		rateLimitPanel.setText(getTextInColor(rateLimitText, rateLimitColor));
		statePanel.setText(getTextInColor(stateText, stateColor));
	}

	public String getTextInColor(String text, String color)
	{
		return "<html><body style=\"color: "+ color +";\">"+ text +"</body></html>";
	}
}
