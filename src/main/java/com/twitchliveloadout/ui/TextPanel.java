package com.twitchliveloadout.ui;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class TextPanel extends JPanel
{
	private final GridBagConstraints constraints = new GridBagConstraints();
	private final GridBagConstraints textConstraints = new GridBagConstraints();

	private final JPanel textWrapper = new JPanel(new GridBagLayout());

	private final JLabel titleLabel = new JLabel();
	private final JLabel textLabel = new JLabel();

	public TextPanel(String title, String text)
	{
		super(new GridBagLayout());
		setBorder(new EmptyBorder(10, 0, 5, 0));

		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		textConstraints.fill = GridBagConstraints.HORIZONTAL;
		textConstraints.weightx = 1;
		textConstraints.gridx = 0;
		textConstraints.gridy = 0;

		Styles.styleBigLabel(titleLabel, title);
		titleLabel.setBorder(new EmptyBorder(0, 0, 5, 0));
		Styles.styleLabel(textLabel, text);

		textWrapper.setLayout(new GridBagLayout());
		textWrapper.setBorder(new EmptyBorder(10, 10, 10, 10));
		textWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		textWrapper.add(textLabel, textConstraints);

		add(titleLabel, constraints);
		constraints.gridy++;
		add(textWrapper, constraints);
		constraints.gridy++;
	}

	public void setText(String text)
	{
		if (text == null)
		{
			text = "";
		}

		textLabel.setText(text);
	}
}
