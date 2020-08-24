package net.runelite.client.plugins.twitchliveloadout.ui;

import net.runelite.client.plugins.twitchliveloadout.Fight;
import net.runelite.client.plugins.twitchliveloadout.FightStateManager;
import net.runelite.client.plugins.twitchliveloadout.TwitchLiveLoadoutPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

public class CombatPanel extends JPanel
{
	private static final String FIGHT_LIST_PANEL = "FIGHT_LIST_PANEL";
	private static final String ERROR_PANEL = "ERROR_PANEL";

	private final GridBagConstraints constraints = new GridBagConstraints();
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel wrapper = new JPanel(cardLayout);

	private final JPanel fightListPanel = new JPanel(new GridBagLayout());
	private final JPanel fightListWrapper = new JPanel(new BorderLayout());

	private final PluginErrorPanel errorPanel = new PluginErrorPanel();
	private final JPanel errorWrapper = new JPanel(new BorderLayout());

	private final FightStateManager fightStateManager;

	public CombatPanel(FightStateManager fightStateManager)
	{
		super(new GridBagLayout());

		this.fightStateManager = fightStateManager;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		fightListPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		fightListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		fightListWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		fightListWrapper.add(fightListPanel, BorderLayout.NORTH);

		errorPanel.setBorder(new EmptyBorder(50, 20, 20, 20));
		errorPanel.setContent("No fights available", "Start combat with an enemy to manage the fights.");

		errorWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		errorWrapper.add(errorPanel, BorderLayout.NORTH);

		wrapper.add(fightListWrapper, FIGHT_LIST_PANEL);
		wrapper.add(errorWrapper, ERROR_PANEL);
		add(wrapper, BorderLayout.CENTER);
	}

	public void rebuild()
	{
		rebuildFightList();
	}

	public void rebuildFightList()
	{
		fightListPanel.removeAll();

		// guard: check if there are no fights to show
		if (fightStateManager.getFights().size() <= 0)
		{
			cardLayout.show(wrapper, ERROR_PANEL);
			return;
		}
	}
}
