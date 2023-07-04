package com.twitchliveloadout.ui;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceDuration;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.TwitchProductCost;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

@Slf4j
public abstract class EntityActionPanel<EntityType> extends JPanel {
	protected static final ImageIcon DELETE_ICON;
	protected static final ImageIcon DELETE_HOVER_ICON;
	protected static final ImageIcon RERUN_ICON;
	protected static final ImageIcon RERUN_HOVER_ICON;

	private EntityType entity;
	protected final JPanel parentPanel;
	protected final String invalidText;

	private final JPanel wrapper = new JPanel(new GridBagLayout());
	private final JLabel nameLabel = new JLabel();
	private final JLabel actionLabel = new JLabel();

	static
	{
		final BufferedImage deleteImg = ImageUtil.loadImageResource(TwitchLiveLoadoutPlugin.class, "/delete_icon.png");
		final BufferedImage rerunImg = ImageUtil.loadImageResource(TwitchLiveLoadoutPlugin.class, "/rerun_icon.png");
		DELETE_ICON = new ImageIcon(deleteImg);
		DELETE_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(deleteImg, -100));
		RERUN_ICON = new ImageIcon(rerunImg);
		RERUN_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(rerunImg, -100));
	}

	public EntityActionPanel(JPanel parentPanel, String invalidText, boolean enableConfirm, String confirmText, String actionTooltipText, ImageIcon actionIcon, ImageIcon actionHoverIcon)
	{
		this.parentPanel = parentPanel;
		this.invalidText = invalidText;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(0, 0, 10, 0));

		Styles.styleBigLabel(nameLabel, "N/A");

		actionLabel.setIcon(actionIcon);
		actionLabel.setToolTipText(actionTooltipText);
		actionLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{

				// guard: check if we should skip this action
				if (entity == null)
				{
					return;
				}

				// guard: immediately skip the confirm when disabled
				if (!enableConfirm)
				{
					executeAction();
					return;
				}

				int confirm = JOptionPane.showConfirmDialog(parentPanel,
					confirmText,
					"Warning",
					JOptionPane.OK_CANCEL_OPTION
				);

				if (confirm == 0)
				{
					executeAction();
				}
			}

			@Override
			public void mouseEntered(MouseEvent mouseEvent)
			{
				actionLabel.setIcon(actionHoverIcon);
				actionLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
			}

			@Override
			public void mouseExited(MouseEvent mouseEvent)
			{
				actionLabel.setIcon(actionIcon);
				actionLabel.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
			}
		});

		wrapper.setLayout(new BorderLayout());
		wrapper.setBorder(new EmptyBorder(10, 10, 10, 10));
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.add(nameLabel, BorderLayout.WEST);
		wrapper.add(actionLabel, BorderLayout.EAST);

		add(wrapper, BorderLayout.NORTH);
	}

	public void setEntity(EntityType entity)
	{
		this.entity = entity;
	}

	public EntityType getEntity()
	{
		return entity;
	}

	public void rebuild()
	{

		// guard: check if the entity is valid
		if (entity == null)
		{
			nameLabel.setText(invalidText);
			setVisible(false);
			return;
		}

		String[] lines = getLines();
		String name = String.join("<br/>", lines);

		setVisible(true);
		nameLabel.setText("<html>"+ name +"</html>");
	}

	protected abstract String[] getLines();

	protected abstract void executeAction();
}
