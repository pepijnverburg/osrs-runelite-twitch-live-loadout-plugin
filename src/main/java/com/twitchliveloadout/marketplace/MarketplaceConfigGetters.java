package com.twitchliveloadout.marketplace;

import java.util.ArrayList;
import java.util.Random;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.*;

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

	public static EbsProductMovementAnimations getValidMovementAnimations(EbsProductMovementAnimations movementAnimations)
	{
		EbsProductMovementAnimations validMovementAnimations = new EbsProductMovementAnimations();
		validMovementAnimations.idleAnimationId = -1;
		validMovementAnimations.runAnimationId = -1;
		validMovementAnimations.walkAnimationId = -1;

		if (movementAnimations != null) {
			if (movementAnimations.idleAnimationId != null) {
				validMovementAnimations.idleAnimationId = movementAnimations.idleAnimationId;
			}
			if (movementAnimations.walkAnimationId != null) {
				validMovementAnimations.walkAnimationId = movementAnimations.walkAnimationId;
			}
			if (movementAnimations.runAnimationId != null) {
				validMovementAnimations.runAnimationId = movementAnimations.runAnimationId;
			}
		}

		return validMovementAnimations;
	}

	public static double getValidRandomNumberByRange(EbsProductRandomRange randomRange, double defaultMin, double defaultMax)
	{
		EbsProductRandomRange validRandomRange = new EbsProductRandomRange();
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

	public static EbsProductAnimationFrame getValidAnimationFrame(EbsProductAnimationFrame frame)
	{
		EbsProductAnimationFrame validFrame = new EbsProductAnimationFrame();
		validFrame.id = null;
		validFrame.durationMs = 0;
		validFrame.delayMs = 0;

		// override properties that are valid
		if (frame != null) {
			if (frame.id != null) {
				validFrame.id = frame.id;
			}
			if (frame.delayMs != null) {
				validFrame.delayMs = frame.delayMs;
			}
			if (frame.durationMs != null) {
				validFrame.durationMs = frame.durationMs;
			}
		}

		return validFrame;
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

	public static EbsProductInterval generateDefaultInterval()
	{
		EbsProductInterval interval = new EbsProductInterval();
		interval.chance = 1.0d;
		interval.delayMs = 0;
		interval.durationMs = 0;
		interval.repeatAmount = 1;

		return interval;
	}

	public static EbsModelPlacement generateDefaultModelPlacement()
	{
		EbsModelPlacement placement = new EbsModelPlacement();
		placement.locationType = CURRENT_TILE_LOCATION_TYPE;
		placement.radiusType = OUTWARD_RADIUS_TYPE;
		placement.radius = DEFAULT_RADIUS;

		return placement;
	}
}
