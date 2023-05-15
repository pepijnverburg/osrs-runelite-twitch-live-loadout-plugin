package com.twitchliveloadout.ui;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.products.StreamerProduct;
import com.twitchliveloadout.marketplace.products.TwitchProductCost;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
public class TwitchTransactionPanel extends EntityActionPanel<TwitchTransaction> {

	private final MarketplaceManager marketplaceManager;

	public TwitchTransactionPanel(JPanel parentPanel, MarketplaceManager marketplaceManager) {
		super(
			parentPanel,
			"Invalid Twitch Transaction",
			"Are you sure you want to rerun this donation Random Event?",
			"Rerun Random Event",
			EntityActionPanel.RERUN_ICON,
			EntityActionPanel.RERUN_HOVER_ICON
		);

		this.marketplaceManager = marketplaceManager;
	}

	@Override
	protected String[] getLines() {
		TwitchTransaction twitchTransaction = getEntity();
		StreamerProduct streamerProduct = marketplaceManager.getStreamerProductByTransaction(twitchTransaction);
		String viewerName = twitchTransaction.user_name;
		TwitchProductCost productCost = twitchTransaction.product_data.cost;
		Integer costAmount = productCost.amount;
		String costCurrency = productCost.type;
		Instant transactionAt = Instant.parse(twitchTransaction.timestamp);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss - dd MMM yyyy").withZone(ZoneId.systemDefault());
		String transactionAtString = formatter.format(transactionAt);
		String streamerProductName = "Unknown";

		if (streamerProduct != null)
		{
			streamerProductName = streamerProduct.name;
		}

		String[] lines = {
			"<b>"+ streamerProductName +"</b>",
			"Donation of <b color='yellow'>"+ costAmount +" "+ costCurrency +"</b>",
			"By <b color='yellow'>"+ viewerName + "</b>",
			"At "+ transactionAtString,
		};

		return lines;
	}

	@Override
	protected void executeAction() {
		TwitchTransaction twitchTransaction = getEntity();
		String transactionId = (twitchTransaction != null ? twitchTransaction.id : "unknown");

		log.info("A transaction is manually requested for a rerun, transaction ID: "+ transactionId);
		marketplaceManager.rerunTransaction(twitchTransaction);
	}
}
