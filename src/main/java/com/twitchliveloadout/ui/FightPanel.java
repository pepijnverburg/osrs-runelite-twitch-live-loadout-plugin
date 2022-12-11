package com.twitchliveloadout.ui;

import com.twitchliveloadout.fights.Fight;
import com.twitchliveloadout.fights.FightStateManager;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class FightPanel extends JPanel
{
	private final FightStateManager fightStateManager;
	private Fight fight;

	private static final ImageIcon DELETE_ICON;
	private static final ImageIcon DELETE_HOVER_ICON;

	private final JPanel wrapper = new JPanel(new GridBagLayout());
	private final JLabel actorNameLabel = new JLabel();
	private final JLabel deleteLabel = new JLabel();

	static
	{
		final BufferedImage deleteImg = ImageUtil.loadImageResource(TwitchLiveLoadoutPlugin.class, "/delete_icon.png");
		DELETE_ICON = new ImageIcon(deleteImg);
		DELETE_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(deleteImg, -100));
	}

	public FightPanel(FightStateManager fightStateManager)
	{
		this.fightStateManager = fightStateManager;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(0, 0, 10, 0));

		Styles.styleBigLabel(actorNameLabel, "N/A");

		deleteLabel.setIcon(DELETE_ICON);
		deleteLabel.setToolTipText("Reset fight statistics");
		deleteLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				int confirm = JOptionPane.showConfirmDialog(FightPanel.this,
					"Are you sure you want to reset this fight?",
					"Warning", JOptionPane.OK_CANCEL_OPTION);

				if (confirm == 0)
				{
					fightStateManager.deleteFight(fight);
				}
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				deleteLabel.setIcon(DELETE_HOVER_ICON);
				deleteLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				deleteLabel.setIcon(DELETE_ICON);
				deleteLabel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			}
		});

		wrapper.setLayout(new BorderLayout());
		wrapper.setBorder(new EmptyBorder(10, 10, 10, 10));
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.add(actorNameLabel, BorderLayout.WEST);
		wrapper.add(deleteLabel, BorderLayout.EAST);

		add(wrapper, BorderLayout.NORTH);
	}

	public void setFight(Fight fight)
	{
		this.fight = fight;
	}

	public void rebuild()
	{

		// guard: check if the fight is valid
		if (fight == null)
		{
			return;
		}

		final String actorName = fight.getActorName();
		final FightStateManager.ActorType actorType = fight.getActorType();

		actorNameLabel.setText(actorName +" ("+ actorType.getName() +")");
	}
}
