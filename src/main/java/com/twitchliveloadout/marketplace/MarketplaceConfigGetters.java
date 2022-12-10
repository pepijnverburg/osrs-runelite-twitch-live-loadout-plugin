package com.twitchliveloadout.marketplace;

import com.twitchliveloadout.marketplace.products.*;

import java.util.ArrayList;
import java.util.Random;

public class MarketplaceConfigGetters {

	public static <T> T getRandomEntryFromList(ArrayList<T> list)
	{
		if (list == null || list.size() < 0)
		{
			return null;
		}

		Random selector = new Random();
		int randomIndex = selector.nextInt(list.size());
		T randomEntry = list.get(randomIndex);

		return randomEntry;
	}

	public static double getValidRandomNumberByRange(EbsRandomRange randomRange, double defaultMin, double defaultMax)
	{
		EbsRandomRange validRandomRange = new EbsRandomRange();
		validRandomRange.min = defaultMin;
		validRandomRange.max = defaultMax;

		if (randomRange != null) {
			if (randomRange.min != null) {
				validRandomRange.min = randomRange.min;
			}
			if (randomRange.max != null) {
				validRandomRange.max = randomRange.max;
			}

			// when there is a minimum range, but no maximum the minimum
			// will be used for BOTH
			if (randomRange.min != null && randomRange.max == null) {
				validRandomRange.min = randomRange.min;
				validRandomRange.max = randomRange.min;
			}
		}

		Double min = validRandomRange.min;
		Double max = validRandomRange.max;
		double randomValue = min + ((int) (Math.random() * ((float) Math.abs(max - min))));

		return randomValue;
	}

	public static boolean rollChance(Double chance)
	{
		Double roll = Math.random();

		if (chance == null)
		{
			return true;
		}

		return chance >= roll;
	}
}
