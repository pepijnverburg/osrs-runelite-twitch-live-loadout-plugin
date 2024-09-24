package com.twitchliveloadout.ui;

import com.twitchliveloadout.marketplace.MarketplaceDuration;
import com.twitchliveloadout.marketplace.MarketplaceManager;
import com.twitchliveloadout.marketplace.MarketplaceMessages;
import com.twitchliveloadout.marketplace.products.EbsProduct;
import com.twitchliveloadout.marketplace.products.MarketplaceProduct;
import com.twitchliveloadout.marketplace.products.TwitchProductCost;
import com.twitchliveloadout.marketplace.transactions.TwitchTransaction;
import com.twitchliveloadout.twitch.eventsub.TwitchEventSubType;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;

@Slf4j
public class EbsProductPanel extends EntityActionPanel<EbsProduct> {
    private final MarketplaceManager marketplaceManager;

    public EbsProductPanel(JPanel parentPanel, MarketplaceManager marketplaceManager) {
        super(
                parentPanel,
                "Invalid custom Random Event",
                false,
                "Are you sure you want to run this custom Random Event?",
                "Run custom Random Event",
                EntityActionPanel.RERUN_ICON,
                EntityActionPanel.RERUN_HOVER_ICON
        );
        this.marketplaceManager = marketplaceManager;
    }

    @Override
    protected String[] getLines() {
        EbsProduct ebsProduct = getEntity();
        String[] lines = new String[]{
                "<b>"+ ebsProduct.category +"</b>",
                "<b color='yellow'>" + ebsProduct.name + "</b>",
        };

        return lines;
    }

    @Override
    protected void executeAction() {
        EbsProduct ebsProduct = getEntity();
        MarketplacePanel marketplacePanel = (MarketplacePanel) parentPanel;
        String ebsProductId = ebsProduct.id;

        log.info("A custom EBS product is being run, ID: "+ ebsProductId);
        marketplaceManager.testEbsProduct(ebsProduct);
        marketplacePanel.rebuild();
    }

    @Override
    protected boolean canRunAction() {
        return true;
    }
}
