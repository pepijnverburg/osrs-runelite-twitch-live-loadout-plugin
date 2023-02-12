package com.twitchliveloadout.marketplace;

import com.twitchliveloadout.marketplace.products.MarketplaceProduct;

import java.time.Instant;
import java.util.Comparator;

public class MarketplaceProductSorter implements Comparator<MarketplaceProduct>
{
	public int compare(MarketplaceProduct p1, MarketplaceProduct p2)
	{
		Instant startedAt = p1.getStartedAt();
		Instant comparedStartedAt = p2.getStartedAt();
		long startedEpoch = (startedAt == null ? 0 : startedAt.getEpochSecond());
		long startedUpdateEpoch = (comparedStartedAt == null ? 0 : comparedStartedAt.getEpochSecond());
		boolean isLater = startedEpoch > startedUpdateEpoch;

		// check for equal
		if (startedEpoch == startedUpdateEpoch)
		{
			return 0;
		}

		// asc order, from first to new ones
		return !isLater ? -1 : 1;
	}
}
