/*
 * Copyright (c) 2018 Abex
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.twitchliveloadout.ui;

import com.google.gson.JsonObject;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchApi;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ConnectivityPanel extends JPanel
{
	private final static String DEFAULT_TEXT_COLOR = "#ffffff";
	private final static String SUCCESS_TEXT_COLOR = "#00ff00";
	private final static String WARNING_TEXT_COLOR = "#ffa500";
	private final static String ERROR_TEXT_COLOR = "#ff0000";
	private final static int WARNING_BEFORE_EXPIRY = 60 * 5; // in seconds
	private static final ImageIcon ARROW_RIGHT_ICON;
	private static final ImageIcon DISCORD_ICON;
	private static final ImageIcon WIKI_ICON;
	private static final String SETUP_GUIDE_URL = "https://liveloadout.com/#get-started";
	private static final String DISCORD_INVITE_URL = "https://discord.gg/3Fjm5HTFGM";

	private final GridBagConstraints constraints = new GridBagConstraints();
	private final JPanel wrapper = new JPanel(new GridBagLayout());

	private final TextPanel syncingStatusPanel = new TextPanel("Syncing status", "N/A");
	private final TextPanel twitchStatusPanel = new TextPanel("Twitch Status", "N/A");
	private final TextPanel authPanel = new TextPanel("Twitch Token Validity", "N/A");
	private final TextPanel rateLimitPanel = new TextPanel("Twitch API Limit", "N/A");
	private final TextPanel statePanel = new TextPanel("Loadout State Size", "N/A");
	private JPanel actionsContainer = new JPanel();

	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchApi twitchApi;
	private final CanvasListener canvasListener;
	private final TwitchLiveLoadoutConfig config;

	static
	{
		ARROW_RIGHT_ICON = new ImageIcon(ImageUtil.loadImageResource(ConnectivityPanel.class, "/arrow_right.png"));
		DISCORD_ICON = new ImageIcon(ImageUtil.loadImageResource(ConnectivityPanel.class, "/discord_icon.png"));
		WIKI_ICON = new ImageIcon(ImageUtil.loadImageResource(ConnectivityPanel.class, "/wiki_icon.png"));
	}

	public ConnectivityPanel(TwitchLiveLoadoutPlugin plugin, TwitchApi twitchApi, CanvasListener canvasListener, TwitchLiveLoadoutConfig config)
	{
		super(new GridBagLayout());

		this.plugin = plugin;
		this.twitchApi = twitchApi;
		this.canvasListener = canvasListener;
		this.config = config;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		actionsContainer.setBorder(new EmptyBorder(10, 0, 0, 0));
		actionsContainer.setLayout(new GridLayout(0, 1, 0, 10));
		actionsContainer.add(buildLinkPanel(DISCORD_ICON, "Get support on the", "Discord server", () -> {
			LinkBrowser.browse(DISCORD_INVITE_URL);
		}));
		actionsContainer.add(buildLinkPanel(WIKI_ICON, "Go to online", "setup guide", () -> {
			LinkBrowser.browse(SETUP_GUIDE_URL);
		}));

		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		// add all panels
		wrapper.add(actionsContainer);
		constraints.gridy += 2;
		wrapper.add(syncingStatusPanel, constraints);
		constraints.gridy++;
		wrapper.add(twitchStatusPanel, constraints);
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
		String syncingStatusText = "The logged in account is currently syncing to Twitch.";
		String syncingStatusColor = SUCCESS_TEXT_COLOR;

		final int responseCode = twitchApi.getLastResponseCode();
		String twitchStatusText = twitchApi.getLastResponseMessage();
		String twitchStatusColor = SUCCESS_TEXT_COLOR;

		long tokenExpiry = 0;
		String authText = "No valid Twitch Token.";
		String authColor = ERROR_TEXT_COLOR;

		String rateLimitText = "There are "+ rateLimitRemaining +" request points available before hitting the Twitch API rate limit.";
		String rateLimitColor = DEFAULT_TEXT_COLOR;

		// update syncing status text
		if (!config.syncEnabled())
		{
			syncingStatusText = "Syncing is currently disabled in the plugin settings. Nothing is being sent to Twitch.";
			syncingStatusColor = ERROR_TEXT_COLOR;
		}
		else if (!plugin.isLoggedIn())
		{
			syncingStatusText = "This client is currently not logged into an account. Twitch only receives connectivity updates without any loadout information.";
			syncingStatusColor = WARNING_TEXT_COLOR;
		}

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
			twitchStatusText += "<br/><br/>An error occurred wth code: "+ responseCode;
			twitchStatusColor = ERROR_TEXT_COLOR;
		}

		if (stateUsagePercentage >= 100)
		{
			stateText += "<br/><br/>An error occurred: state is too large to send to Twitch. Please disable some synced information via the plugin settings in RuneLite.";
			stateColor = ERROR_TEXT_COLOR;
		}

		if (rateLimitRemaining <= 10)
		{
			rateLimitText += "<br/><br/>Which is almost depleted! Consider having fewer RuneLite clients open at the same time with the plugin active.";
			rateLimitColor = ERROR_TEXT_COLOR;
		}

		syncingStatusPanel.setText(getTextInColor(syncingStatusText, syncingStatusColor));
		twitchStatusPanel.setText(getTextInColor(twitchStatusText, twitchStatusColor));
		authPanel.setText(getTextInColor(authText, authColor));
		rateLimitPanel.setText(getTextInColor(rateLimitText, rateLimitColor));
		statePanel.setText(getTextInColor(stateText, stateColor));
	}

	public String getTextInColor(String text, String color)
	{
		return "<html><body style=\"color: "+ color +";\">"+ text +"</body></html>";
	}

	/**
	 * Builds a link panel with a given icon, text and callable to call.
	 */
	private static JPanel buildLinkPanel(ImageIcon icon, String topText, String bottomText, Runnable callback)
	{
		JPanel container = new JPanel();
		container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		container.setLayout(new BorderLayout());
		container.setBorder(new EmptyBorder(10, 10, 10, 10));

		final Color hoverColor = ColorScheme.DARKER_GRAY_HOVER_COLOR;
		final Color pressedColor = ColorScheme.DARKER_GRAY_COLOR.brighter();

		JLabel iconLabel = new JLabel(icon);
		container.add(iconLabel, BorderLayout.WEST);

		JPanel textContainer = new JPanel();
		textContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		textContainer.setLayout(new GridLayout(2, 1));
		textContainer.setBorder(new EmptyBorder(5, 10, 5, 10));

		container.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				container.setBackground(pressedColor);
				textContainer.setBackground(pressedColor);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				callback.run();
				container.setBackground(hoverColor);
				textContainer.setBackground(hoverColor);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				container.setBackground(hoverColor);
				textContainer.setBackground(hoverColor);
				container.setCursor(new Cursor(Cursor.HAND_CURSOR));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				container.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				textContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				container.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			}
		});

		JLabel topLine = new JLabel(topText);
		topLine.setForeground(Color.WHITE);
		topLine.setFont(FontManager.getRunescapeSmallFont());

		JLabel bottomLine = new JLabel(bottomText);
		bottomLine.setForeground(Color.WHITE);
		bottomLine.setFont(FontManager.getRunescapeSmallFont());

		textContainer.add(topLine);
		textContainer.add(bottomLine);

		container.add(textContainer, BorderLayout.CENTER);

		JLabel arrowLabel = new JLabel(ARROW_RIGHT_ICON);
		container.add(arrowLabel, BorderLayout.EAST);

		return container;
	}
}
