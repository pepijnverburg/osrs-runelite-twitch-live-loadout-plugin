package com.twitchliveloadout.marketplace;

import com.twitchliveloadout.marketplace.products.*;

import java.util.ArrayList;
import java.util.Random;

public class MarketplaceRandomizers {

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
		return getValidRandomNumberByRange(randomRange, defaultMin, defaultMax, Double.MIN_VALUE, Double.MAX_VALUE);
	}

	public static double getValidRandomNumberByRange(EbsRandomRange randomRange, double defaultMin, double defaultMax, double absoluteMin, double absoluteMax)
	{
		EbsRandomRange validRandomRange = new EbsRandomRange(defaultMin, defaultMax);

		if (randomRange != null) {
			if (randomRange.min != null) {
				validRandomRange.min = randomRange.min;
			}
			if (randomRange.max != null) {
				validRandomRange.max = randomRange.max;
			}
			if (randomRange.min != null && randomRange.max == null) {
				validRandomRange.max = randomRange.min;
			}

			// make sure the max is valid compared to the min
			if (validRandomRange.min > validRandomRange.max) {
				validRandomRange.max = validRandomRange.min;
			}
		}

		Double min = validRandomRange.min;
		Double max = validRandomRange.max;
		double randomValue = min + ((int) (Math.random() * ((float) Math.abs(max - min))));

		// guard: make sure absolute min and max are not exceeded
		if (randomValue < absoluteMin) {
			randomValue = absoluteMin;
		} else if (randomValue > absoluteMax) {
			randomValue = absoluteMax;
		}

		return randomValue;
	}

	public static boolean rollChance(Double chance)
	{
		double roll = Math.random();

		if (chance == null)
		{
			return true;
		}

		return chance >= roll;
	}

	public static EbsSpawnOption getSpawnBehaviourByChance(ArrayList<EbsSpawnOption> spawnBehaviourOptions)
	{

		// guard: make sure there are any options
		if (spawnBehaviourOptions == null || spawnBehaviourOptions.size() <= 0)
		{
			return null;
		}

		int attempts = 0;
		int maxAttempts = 50;
		int optionAmount = spawnBehaviourOptions.size();
		double defaultChance = (1d / ((double) optionAmount));

		// roll for x amount of times to select the option
		// TODO: see how this impacts the selection?
		while (attempts++ < maxAttempts)
		{
			for (EbsSpawnOption option : spawnBehaviourOptions)
			{
				Double chance = option.chance;
				Double validatedChance = (chance == null ? defaultChance : chance);

				// choose this option when the chance is not known or when the roll landed
				if (MarketplaceRandomizers.rollChance(validatedChance))
				{
					return option;
				}
			}
		}

		// get the first is no valid one is found
		return spawnBehaviourOptions.get(0);
	}
}
