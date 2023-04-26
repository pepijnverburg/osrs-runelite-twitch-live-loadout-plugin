package com.twitchliveloadout.ui;

import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.marketplace.MarketplaceDuration;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
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
public class MarketplaceProductPanel extends EntityActionPanel<MarketplaceProduct> {

	public MarketplaceProductPanel(JPanel parentPanel) {
		super(
			parentPanel,
			"Invalid Random Event donation",
			"Are you sure you want to stop the effects of this donation?",
			"Stop Random Event",
			EntityActionPanel.DELETE_ICON,
			EntityActionPanel.DELETE_HOVER_ICON
		);
	}

	@Override
	protected String[] getLines() {
		MarketplaceProduct marketplaceProduct = getEntity();
		boolean isActive = marketplaceProduct.isActive();
		boolean isExpired = marketplaceProduct.isExpired();
		long expiresInMs = marketplaceProduct.getExpiresInMs();
		String streamerProductName = marketplaceProduct.getStreamerProduct().name;
		String viewerName = marketplaceProduct.getTransaction().user_name;
		TwitchProductCost productCost = marketplaceProduct.getTwitchProduct().cost;
		Integer costAmount = productCost.amount;
		String costCurrency = productCost.type;
		String statusLine = "<b color='green'>ACTIVE</b>";

		if (isExpired) {
			statusLine = "<b color='red'>EXPIRED</b>";
		} else if (!isActive) {
			statusLine = "<b color='orange'>PAUSED</b>";
		}

		String[] lines = {
			statusLine,
			"<b>"+ streamerProductName +"</b>",
			"Donation of <b color='yellow'>"+ costAmount +" "+ costCurrency +"</b>",
			"By <b color='yellow'>"+ viewerName +"</b>",
			"Expires in "+ MarketplaceDuration.humanizeDurationMs(expiresInMs),
		};
		return lines;
	}

	@Override
	protected void executeAction() {
		MarketplaceProduct marketplaceProduct = getEntity();
		MarketplacePanel marketplacePanel = (MarketplacePanel) parentPanel;
		TwitchTransaction twitchTransaction = marketplaceProduct.getTransaction();
		String transactionId = (twitchTransaction != null ? twitchTransaction.id : "unknown");

		log.info("A marketplace product is manually requested to be stopped, transaction ID: "+ transactionId);
		marketplaceProduct.stop(false);
		marketplacePanel.rebuild();
	}
}
