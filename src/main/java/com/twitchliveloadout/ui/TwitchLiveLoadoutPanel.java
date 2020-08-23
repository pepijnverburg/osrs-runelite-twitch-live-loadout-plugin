package net.runelite.client.plugins.twitchliveloadout.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import net.runelite.client.plugins.twitchliveloadout.Fight;
import net.runelite.client.plugins.twitchliveloadout.FightStateManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;

public class TwitchLiveLoadoutPanel extends PluginPanel
{
	private final JLabel title = new JLabel();
	private final PluginErrorPanel noFightsPanel = new PluginErrorPanel();
	private final JPanel fightsView = new JPanel(new GridBagLayout());

	private final FightStateManager fightStateManager;

	public TwitchLiveLoadoutPanel(FightStateManager fightStateManager)
	{
		this.fightStateManager = fightStateManager;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		JPanel northPanel = new JPanel(new BorderLayout());
		northPanel.setBorder(new EmptyBorder(1, 0, 10, 0));

		title.setText("Combat Statistics");
		title.setForeground(Color.WHITE);

		northPanel.add(title, BorderLayout.WEST);

		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		fightsView.setBackground(ColorScheme.DARK_GRAY_COLOR);

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		noFightsPanel.setContent("Combat Statistics", "Start combat to show fights.");
		noFightsPanel.setVisible(false);

		fightsView.add(noFightsPanel, constraints);
		constraints.gridy++;

		centerPanel.add(fightsView, BorderLayout.CENTER);

		add(northPanel, BorderLayout.NORTH);
		add(centerPanel, BorderLayout.CENTER);
	}

	public void rebuild()
	{
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		fightsView.removeAll();

		for (final Fight fight : fightStateManager.getFights().values())
		{
			fightsView.add(new FightPanel(fight), constraints);
			constraints.gridy++;

			fightsView.add(Box.createRigidArea(new Dimension(0, 10)), constraints);
			constraints.gridy++;
		}

		boolean empty = constraints.gridy == 0;
		noFightsPanel.setVisible(empty);
		title.setVisible(!empty);

		fightsView.add(noFightsPanel, constraints);
		constraints.gridy++;

		fightsView.add(noFightsPanel, constraints);
		constraints.gridy++;

		repaint();
		revalidate();
	}
}
