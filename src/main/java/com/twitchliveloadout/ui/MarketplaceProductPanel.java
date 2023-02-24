package com.twitchliveloadout.ui;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.TwitchProductCost;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Duration;

@Slf4j
public class MarketplaceProductPanel extends JPanel {
	private MarketplaceProduct marketplaceProduct;

	private static final ImageIcon DELETE_ICON;
	private static final ImageIcon DELETE_HOVER_ICON;

	private final JPanel wrapper = new JPanel(new GridBagLayout());
	private final JLabel nameLabel = new JLabel();
	private final JLabel deleteLabel = new JLabel();

	static
	{
		final BufferedImage deleteImg = ImageUtil.loadImageResource(TwitchLiveLoadoutPlugin.class, "/delete_icon.png");
		DELETE_ICON = new ImageIcon(deleteImg);
		DELETE_HOVER_ICON = new ImageIcon(ImageUtil.alphaOffset(deleteImg, -100));
	}

	public MarketplaceProductPanel(MarketplacePanel marketplacePanel)
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(0, 0, 10, 0));

		Styles.styleBigLabel(nameLabel, "N/A");

		deleteLabel.setIcon(DELETE_ICON);
		deleteLabel.setToolTipText("Stop product effect");
		deleteLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				int confirm = JOptionPane.showConfirmDialog(MarketplaceProductPanel.this,
						"Are you sure you want to stop the effects of this donation?",
						"Warning", JOptionPane.OK_CANCEL_OPTION);

				if (confirm == 0)
				{
					if (marketplaceProduct != null)
					{
						marketplaceProduct.stop();
						marketplacePanel.rebuild();
					}
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
		wrapper.add(nameLabel, BorderLayout.WEST);
		wrapper.add(deleteLabel, BorderLayout.EAST);

		add(wrapper, BorderLayout.NORTH);
	}

	public void setMarketplaceProduct(MarketplaceProduct marketplaceProduct)
	{
		this.marketplaceProduct = marketplaceProduct;
	}

	public void rebuild()
	{

		// set the label from the name
		if (marketplaceProduct == null)
		{
			nameLabel.setText("Invalid random event donation");
		}

		boolean isActive = marketplaceProduct.isActive();
		boolean isExpired = marketplaceProduct.isExpired();
		long expiresInSeconds = marketplaceProduct.getExpiresInMs() / 1000;
		String streamerProductName = marketplaceProduct.getStreamerProduct().name;
		String viewerName = marketplaceProduct.getTransaction().user_name;
		TwitchProductCost productCost = marketplaceProduct.getTwitchProduct().cost;
		Integer costAmount = productCost.amount;
		String costCurrency = productCost.type;
		String statusLine = "<b color='green'>ACTIVE</b>";

		if (isExpired)
		{
			statusLine = "<b color='red'>EXPIRED</b>";
		}

		if (!isActive)
		{
			statusLine = "<b color='orange'>PAUSED</b>";
		}

		String[] lines = {
			statusLine,
			streamerProductName,
			"By <i>"+ viewerName + "</i>",
			"For "+ costAmount +" "+ costCurrency,
			"Expires in "+ MarketplacePanel.humanizeDuration(Duration.ofSeconds(expiresInSeconds)),
		};
		String name = String.join("<br/>", lines);

		nameLabel.setText("<html>"+ name +"</html>");
	}
}
