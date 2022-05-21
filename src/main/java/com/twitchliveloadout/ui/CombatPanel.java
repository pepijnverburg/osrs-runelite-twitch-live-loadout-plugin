package com.twitchliveloadout.ui;

import lombok.extern.slf4j.Slf4j;
import com.twitchliveloadout.Fight;
import com.twitchliveloadout.FightSorter;
import com.twitchliveloadout.FightStateManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.PluginErrorPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class CombatPanel extends JPanel
{
	private static final String FIGHT_LIST_PANEL = "FIGHT_LIST_PANEL";
	private static final String ERROR_PANEL = "ERROR_PANEL";

	private final GridBagConstraints constraints = new GridBagConstraints();
	private final GridBagConstraints fightListConstraints = new GridBagConstraints();
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel wrapper = new JPanel(cardLayout);

	private final JPanel fightListPanel = new JPanel(new GridBagLayout());
	private final JPanel fightListWrapper = new JPanel(new BorderLayout());
	private final CopyOnWriteArrayList<FightPanel> fightPanels = new CopyOnWriteArrayList();
	private final JPanel deleteAllPanel = new JPanel(new BorderLayout());
	private final JLabel deleteAllLabel = new JLabel();

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

		fightListConstraints.fill = GridBagConstraints.HORIZONTAL;
		fightListConstraints.weightx = 1;
		fightListConstraints.gridx = 0;
		fightListConstraints.gridy = 0;

		fightListPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
		fightListPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		deleteAllPanel.setLayout(new BorderLayout());
		deleteAllPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
		deleteAllPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		deleteAllPanel.add(deleteAllLabel, BorderLayout.CENTER);
		Styles.styleBigLabel(deleteAllLabel, "Delete All Fights");
		deleteAllPanel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				int confirm = JOptionPane.showConfirmDialog(CombatPanel.this,
						"Are you sure you want to reset ALL fights?",
						"Warning", JOptionPane.OK_CANCEL_OPTION);

				if (confirm == 0)
				{
					fightStateManager.deleteAllFights();
				}
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				deleteAllPanel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				deleteAllPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				deleteAllPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				deleteAllPanel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			}
		});

		fightListWrapper.setLayout(new GridBagLayout());
		fightListWrapper.setBorder(new EmptyBorder(10, 0, 10, 0));
		fightListWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		fightListWrapper.add(deleteAllPanel, constraints);
		constraints.gridy++;
		fightListWrapper.add(fightListPanel, constraints);
		constraints.gridy++;

		errorPanel.setBorder(new EmptyBorder(50, 20, 20, 20));
		errorPanel.setContent("No fights available", "Start combat with an enemy to manage the fights.");

		errorWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		errorWrapper.add(errorPanel, BorderLayout.NORTH);

		wrapper.add(fightListWrapper, FIGHT_LIST_PANEL);
		wrapper.add(errorWrapper, ERROR_PANEL);
		add(wrapper, BorderLayout.NORTH);

		// initialize all the fight panel slots without adding them to the UI
		for (int i = 0; i < FightStateManager.MAX_FIGHT_AMOUNT_IN_MEMORY; i++)
		{
			FightPanel fightPanel = new FightPanel(fightStateManager);
			fightPanels.add(fightPanel);
		}
	}

	public void rebuild()
	{
		rebuildFightList();
		repaint();
		revalidate();
	}

	public void rebuildFightList()
	{
		final CopyOnWriteArrayList<Fight> fights = new CopyOnWriteArrayList();
		int fightPanelIndex = 0;

		// add all fights by default
		fights.addAll(fightStateManager.getFights().values());

		// order by last update time
		Collections.sort(fights, new FightSorter());

		// guard: check if there are no fights to show
		if (fights.size() <= 0)
		{
			cardLayout.show(wrapper, ERROR_PANEL);
			return;
		}

		cardLayout.show(wrapper, FIGHT_LIST_PANEL);
		fightListPanel.removeAll();
		fightListConstraints.gridy = 0;

		// first clear all the fight in the panels
		for (FightPanel fightPanel : fightPanels)
		{
			fightPanel.setFight(null);
		}

		// directly add all the fights again in the new order
		for (Fight fight : fights)
		{
			FightPanel fightPanel = fightPanels.get(fightPanelIndex);

			// guard: check if the panel is valid
			if (fightPanel == null)
			{
				log.debug("An invalid fight panel index was requested: "+ fightPanelIndex);
				break;
			}

			fightPanel.setFight(fight);
			fightPanel.rebuild();
			fightPanelIndex ++;

			fightListPanel.add(fightPanel, fightListConstraints);
			fightListConstraints.gridy++;
		}
	}
}
