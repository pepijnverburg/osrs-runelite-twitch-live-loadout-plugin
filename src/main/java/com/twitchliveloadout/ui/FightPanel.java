package net.runelite.client.plugins.twitchliveloadout.ui;

import net.runelite.client.plugins.twitchliveloadout.Fight;
import net.runelite.client.plugins.twitchliveloadout.TwitchLiveLoadoutPlugin;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class FightPanel extends JPanel
{
	private final Fight fight;

	private static final ImageIcon DELETE_ICON;
	private static final ImageIcon DELETE_HOVER_ICON;
	private final JLabel deleteFight = new JLabel(DELETE_ICON);

	private static final Color DEFAULT_BORDER_COLOR = Color.GREEN;
	private static final Color DEFAULT_FILL_COLOR = new Color(0, 255, 0, 0);

	private static final int DEFAULT_BORDER_THICKNESS = 3;

	static
	{
		final BufferedImage deleteImg = ImageUtil.getResourceStreamFromClass(TwitchLiveLoadoutPlugin.class, "delete_icon.png");
		DELETE_ICON = new ImageIcon(deleteImg);
		DELETE_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(deleteImg, -100));
	}

	public FightPanel(Fight fight)
	{
		this.fight = fight;
	}
}
