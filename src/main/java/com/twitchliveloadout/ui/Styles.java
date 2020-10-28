package com.twitchliveloadout.ui;

import net.runelite.client.ui.FontManager;

import javax.swing.*;
import java.awt.*;

public class Styles
{
	public static void styleBigLabel(JLabel label, String text)
	{
		label.setText(text);
		label.setForeground(Color.WHITE);
	}

	public static void styleLabel(JLabel label, String text)
	{
		label.setText(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(Color.WHITE);
	}
}
