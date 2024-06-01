package com.twitchliveloadout.twitch.eventsub.messages;

public class CharityCampaignAmount {
    public Integer value = 0;
    public Integer decimal_places = 0;
    public String currency;

    /**
     * The amount of with decimal places to get the real amount. Reference:
     * https://dev.twitch.tv/docs/eventsub/eventsub-reference/#charity-donation-event
     */
    public Double getCurrencyAmount() {
        return value / Math.pow(10, decimal_places);
    }
}
