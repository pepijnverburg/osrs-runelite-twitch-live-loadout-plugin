package com.twitchliveloadout.ui;

import com.twitchliveloadout.marketplace.MarketplaceDuration;
import com.twitchliveloadout.marketplace.MarketplaceMessages;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.TwitchProductCost;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.twitch.eventsub.TwitchEventSubType;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;

@Slf4j
public class MarketplaceProductPanel extends EntityActionPanel<MarketplaceProduct> {

	public MarketplaceProductPanel(JPanel parentPanel) {
		super(
			parentPanel,
			"Invalid Random Event donation",
			false,
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
		TwitchTransaction transaction = marketplaceProduct.getTransaction();
		String streamerProductName = marketplaceProduct.getStreamerProduct().name;
		String statusLine = "<b color='green'>ACTIVE</b>";
		String[] lines = {};
		String expiresInLine = "Expires in " + MarketplaceDuration.humanizeDurationMs(expiresInMs);
		String viewerLine = MarketplaceMessages.formatMessage("By <b color='yellow'>{viewerName}</b>", marketplaceProduct, null);

		if (isExpired) {
			statusLine = "<b color='red'>EXPIRED</b>";
		} else if (!isActive) {
			statusLine = "<b color='orange'>PAUSED</b>";
		}

		if (transaction.isCurrencyTransaction())
		{
			lines = new String[]{
				statusLine,
				"<b>" + streamerProductName + "</b>",
				MarketplaceMessages.formatMessage("Donation of <b color='yellow'>{currencyAmount} {currencyType}</b>", marketplaceProduct, null),
				viewerLine,
				expiresInLine
			};
		}

		if (transaction.isFreeTransaction())
		{
			lines = new String[]{
				statusLine,
				"<b>" + streamerProductName + "</b>",
				"FREE activation",
				viewerLine,
				expiresInLine
			};
		}

		if (transaction.isEventSubTransaction())
		{
			TwitchEventSubType eventSubType = transaction.eventSubType;

			lines = new String[]{
				statusLine,
				"<b>"+ eventSubType.getName() +"</b>",
				"<b color='yellow'>" + streamerProductName + "</b>",
				viewerLine,
				expiresInLine
			};
		}

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

	@Override
	protected boolean canRunAction() {
		return true;
	}
}
