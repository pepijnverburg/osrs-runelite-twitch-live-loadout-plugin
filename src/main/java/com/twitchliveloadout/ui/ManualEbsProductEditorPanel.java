package com.twitchliveloadout.ui;

import com.google.gson.Gson;
import com.twitchliveloadout.marketplace.products.EbsProduct;
import com.twitchliveloadout.marketplace.products.ManualEbsProductEntry;
import com.twitchliveloadout.utilities.GameEventType;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;

public class ManualEbsProductEditorPanel extends JPanel
{
	private static final String MANUAL_ONLY_OPTION = "Manual only";
	private static final String JSON_PLACEHOLDER = "Paste event config here";
	private static final int PREFERRED_EDITOR_HEIGHT = 120;

	private final Gson gson;
	private final JTextArea jsonTextArea = new JTextArea(7, 20)
	{
		@Override
		protected void paintComponent(Graphics graphics)
		{
			super.paintComponent(graphics);

			if (!getText().isEmpty())
			{
				return;
			}

			Graphics2D graphics2D = (Graphics2D) graphics.create();
			graphics2D.setColor(Color.LIGHT_GRAY);
			graphics2D.setFont(getFont());
			Insets insets = getInsets();
			FontMetrics fontMetrics = graphics2D.getFontMetrics();
			int baseline = insets.top + fontMetrics.getAscent();
			graphics2D.drawString(JSON_PLACEHOLDER, insets.left, baseline);
			graphics2D.dispose();
		}
	};
	private final JLabel feedbackLabel = new JLabel();
	private final JComboBox<String> triggerSelect = new JComboBox<>();
	private final JScrollPane jsonScrollPane = new JScrollPane(jsonTextArea);

	public ManualEbsProductEditorPanel(Gson gson)
	{
		super(new BorderLayout(0, 8));
		this.gson = gson;

		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);

		jsonTextArea.setLineWrap(true);
		jsonTextArea.setWrapStyleWord(true);
		jsonTextArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
		jsonTextArea.setForeground(Color.WHITE);
		jsonTextArea.setCaretColor(Color.WHITE);
		jsonTextArea.setBorder(new EmptyBorder(8, 8, 8, 8));

		JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
		centerPanel.setOpaque(false);
		jsonScrollPane.setBorder(BorderFactory.createEmptyBorder());
		jsonScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		jsonScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		jsonScrollPane.setPreferredSize(new Dimension(0, PREFERRED_EDITOR_HEIGHT));
		jsonScrollPane.setMinimumSize(new Dimension(0, PREFERRED_EDITOR_HEIGHT));
		centerPanel.add(jsonScrollPane, BorderLayout.CENTER);

		JPanel triggersPanel = new JPanel(new BorderLayout(8, 0));
		triggersPanel.setOpaque(false);
		JLabel triggerLabel = new JLabel();
		Styles.styleLabel(triggerLabel, "Trigger:");
		triggerSelect.setFocusable(false);
		triggerSelect.setBackground(ColorScheme.DARK_GRAY_COLOR);
		triggerSelect.setForeground(Color.WHITE);
		triggerSelect.addItem(MANUAL_ONLY_OPTION);

		for (GameEventType gameEventType : GameEventType.values())
		{
			triggerSelect.addItem(gameEventType.getName());
		}

		triggersPanel.add(triggerLabel, BorderLayout.WEST);
		triggersPanel.add(triggerSelect, BorderLayout.CENTER);
		centerPanel.add(triggersPanel, BorderLayout.SOUTH);
		add(centerPanel, BorderLayout.CENTER);

		Styles.styleLabel(feedbackLabel, "");
		add(feedbackLabel, BorderLayout.SOUTH);
	}

	@Override
	public Dimension getPreferredSize()
	{
		Dimension preferredSize = super.getPreferredSize();
		preferredSize.height = Math.max(preferredSize.height, PREFERRED_EDITOR_HEIGHT + 60);
		return preferredSize;
	}

	@Override
	public Dimension getMinimumSize()
	{
		return getPreferredSize();
	}

	public void clearEditor()
	{
		jsonTextArea.setText("");
		triggerSelect.setSelectedIndex(0);
		setFeedback("", Color.WHITE);
	}

	public ManualEbsProductEntry buildEntry() throws Exception
	{
		String rawEbsProduct = jsonTextArea.getText();
		EbsProduct ebsProduct = gson.fromJson(rawEbsProduct, EbsProduct.class);

		if (ebsProduct == null || ebsProduct.id == null || ebsProduct.behaviour == null || ebsProduct.name == null)
		{
			throw new Exception("Manual Random Event JSON is missing one of: id, name or behaviour.");
		}

		ebsProduct.dangerous = true;
		ManualEbsProductEntry manualEbsProductEntry = new ManualEbsProductEntry(ebsProduct, getSelectedGameEventTypes());

		if (!manualEbsProductEntry.isValid())
		{
			throw new Exception("Manual Random Event JSON is not valid.");
		}

		return manualEbsProductEntry;
	}

	public ArrayList<GameEventType> getSelectedGameEventTypes()
	{
		ArrayList<GameEventType> gameEventTypes = new ArrayList<>();
		int selectedIndex = triggerSelect.getSelectedIndex();

		if (selectedIndex <= 0)
		{
			return gameEventTypes;
		}

		GameEventType gameEventType = GameEventType.values()[selectedIndex - 1];
		gameEventTypes.add(gameEventType);

		return gameEventTypes;
	}

	public void setFeedback(String text, Color color)
	{
		feedbackLabel.setText(text);
		feedbackLabel.setForeground(color);
	}
}