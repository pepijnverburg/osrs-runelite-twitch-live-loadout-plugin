package com.twitchliveloadout.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.fights.FightStateManager;
import com.twitchliveloadout.twitch.TwitchApi;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

public class TwitchLiveLoadoutPanel extends PluginPanel
{
	private final JPanel mainPanel = new JPanel(new GridBagLayout());
	private final MaterialTabGroup tabGroup = new MaterialTabGroup(mainPanel);
	private final MaterialTab connectivityTab;
	private final MaterialTab combatTab;
	private final MaterialTab marketplaceTab;

	private final ConnectivityPanel connectivityPanel;
	private final CombatPanel combatPanel;
	private final MarketplacePanel marketplacePanel;

	public TwitchLiveLoadoutPanel(TwitchApi twitchApi, FightStateManager fightStateManager, CanvasListener canvasListener, TwitchLiveLoadoutConfig config)
	{
		super(true);
		setLayout(new BorderLayout());

		combatPanel = new CombatPanel(fightStateManager);
		connectivityPanel = new ConnectivityPanel(twitchApi, canvasListener, config);
		marketplacePanel = new MarketplacePanel(twitchApi);

		connectivityTab = new MaterialTab("Status", tabGroup, connectivityPanel);
		marketplaceTab = new MaterialTab("Donations", tabGroup, marketplacePanel);
		combatTab = new MaterialTab("Combat", tabGroup, combatPanel);

		tabGroup.setBorder(new EmptyBorder(5, 0, 0, 0));
		tabGroup.addTab(connectivityTab);
		tabGroup.addTab(marketplaceTab);
		tabGroup.addTab(combatTab);

		tabGroup.select(connectivityTab);

		add(tabGroup, BorderLayout.NORTH);
		add(mainPanel, BorderLayout.CENTER);
	}

	public void rebuild()
	{
		connectivityPanel.rebuild();
		combatPanel.rebuild();
		marketplacePanel.rebuild();
		repaint();
		revalidate();
	}

	public CombatPanel getCombatPanel()
	{
		return combatPanel;
	}

	public ConnectivityPanel getConnectivityPanel()
	{
		return connectivityPanel;
	}

	public MarketplacePanel getMarketplacePanel()
	{
		return marketplacePanel;
	}
}
