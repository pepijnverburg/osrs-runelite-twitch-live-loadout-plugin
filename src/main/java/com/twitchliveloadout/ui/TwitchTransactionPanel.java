package com.twitchliveloadout.ui;

import com.twitchliveloadout.marketplace.products.TwitchProductCost;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
public class TwitchTransactionPanel extends JPanel {
	private TwitchTransaction twitchTransaction;

	private final JPanel wrapper = new JPanel(new GridBagLayout());
	private final JLabel nameLabel = new JLabel();

	public TwitchTransactionPanel()
	{
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(0, 0, 10, 0));

		Styles.styleBigLabel(nameLabel, "N/A");

		wrapper.setLayout(new BorderLayout());
		wrapper.setBorder(new EmptyBorder(10, 10, 10, 10));
		wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		wrapper.add(nameLabel, BorderLayout.WEST);

		add(wrapper, BorderLayout.NORTH);
	}

	public void setTwitchTransaction(TwitchTransaction twitchTransaction)
	{
		this.twitchTransaction = twitchTransaction;
	}

	public void rebuild()
	{

		// set the label from the name
		if (twitchTransaction == null)
		{
			nameLabel.setText("Invalid transaction");
		}

		String viewerName = twitchTransaction.user_name;
		TwitchProductCost productCost = twitchTransaction.product_data.cost;
		Integer costAmount = productCost.amount;
		String costCurrency = productCost.type;
		Instant transactionAt = Instant.parse(twitchTransaction.timestamp);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd MMM yyyy").withZone(ZoneId.systemDefault());
		String transactionAtString = formatter.format(transactionAt);

		String[] lines = {
			"Donation of <b color='yellow'>"+ costAmount +" "+ costCurrency +"</b>",
			"By <b color='yellow'>"+ viewerName + "</b>",
			"At: "+ transactionAtString,
		};
		String name = String.join("<br/>", lines);

		nameLabel.setText("<html>"+ name +"</html>");
	}
}
