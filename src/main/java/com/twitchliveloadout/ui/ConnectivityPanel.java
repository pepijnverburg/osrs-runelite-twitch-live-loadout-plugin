package net.runelite.client.plugins.twitchliveloadout.ui;

import net.runelite.client.plugins.twitchliveloadout.TwitchApi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ConnectivityPanel extends JPanel
{
	private final static String DEFAULT_TEXT_COLOR = "#ffffff";
	private final static String ERROR_TEXT_COLOR = "#ff0000";

	private final GridBagConstraints constraints = new GridBagConstraints();
	private final JPanel wrapper = new JPanel(new GridBagLayout());

	private final TextPanel statusPanel = new TextPanel("Current Status", "N/A");
	private final TextPanel authPanel = new TextPanel("Extension Token Validity", "N/A");
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
//		wrapper.add(authPanel, constraints);
//		constraints.gridy++;
		wrapper.add(statePanel, constraints);
		constraints.gridy++;

		add(wrapper, BorderLayout.NORTH);
	}

	public void rebuild()
	{
		String statusText = twitchApi.getLastResponseMessage();
		int responseCode = twitchApi.getLastResponseCode();
		String statusColor = DEFAULT_TEXT_COLOR;

		String state = twitchApi.getLastCompressedState();
		byte[] stateBytes = state.getBytes();
		float stateUsagePercentage = ((float) stateBytes.length) / ((float) TwitchApi.MAX_PAYLOAD_SIZE) * 100;
		String stateText = String.format("%.2f", stateUsagePercentage) +"% used of Twitch state.";
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

		statusPanel.setText(getTextInColor(statusText, statusColor));
		statePanel.setText(getTextInColor(stateText, stateColor));
	}

	public String getTextInColor(String text, String color)
	{
		return "<html><body style=\"color: "+ color +";\">"+ text +"</body></html>";
	}
}
