package com.twitchliveloadout.marketplace;

import net.runelite.api.JagexColor;
import net.runelite.api.ModelData;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.MODEL_REFERENCE_SIZE;
import static com.twitchliveloadout.marketplace.MarketplaceConstants.RANDOM_ROTATION_TYPE;

@Slf4j
public class MarketplaceModelUtilities {

	public static void rotateModels(ArrayList<ModelData> models, EbsProduct.ModelSet modelSet)
	{
		String modelRotationType = modelSet.modelRotationType;

		// check for rotation
		switch(modelRotationType) {
			case RANDOM_ROTATION_TYPE:
				rotateModelsRandomly(models);
				break;
		}
	}

	public static void scaleModels(ArrayList<ModelData> models, EbsProduct.ModelSet modelSet)
	{

		// guard: check for scaling
		if (modelSet.modelScale == null)
		{
			return;
		}

		double modelScale = MarketplaceConfigGetters.getValidRandomNumberByRange(modelSet.modelScale, 1, 1);
		int modelSize = (int) (MODEL_REFERENCE_SIZE * modelScale);

		for (ModelData model : models) {
			model.scale(modelSize, modelSize, modelSize);
		}
	}

	public static void recolorAllFaces(ModelData model, Color color)
	{
		recolorAllFaces(model, color, 1.0d);
	}

	public static void recolorAllFaces(ModelData model, Color color, double brightness)
	{
		short[] faceColors = model.getFaceColors();

		for (int faceColorIndex = 0; faceColorIndex < faceColors.length; faceColorIndex++)
		{
			recolorFace(model, faceColorIndex, color, brightness);
		}
	}

	public static void recolorFace(ModelData model, int faceColorIndex, Color color, double brightness)
	{
		short[] faceColors = model.getFaceColors();
		int rgb = color.getRGB();

		if (faceColorIndex < 0 || faceColorIndex >= faceColors.length)
		{
			log.warn("An invalid face color index was requested for a recolor: ", faceColorIndex);
			return;
		}

		short faceColor = faceColors[faceColorIndex];
		model.recolor(faceColor, JagexColor.rgbToHSL(rgb, brightness));
	}

	public static void rotateModelsRandomly(ArrayList<ModelData> models)
	{
		final double random = Math.random();
		if (random < 0.25) {
			for (ModelData model : models) {
				model.rotateY90Ccw();
			}
		} else if (random < 0.5) {
			for (ModelData model : models) {
				model.rotateY180Ccw();
			}
		} else if (random < 0.75) {
			for (ModelData model : models) {
				model.rotateY270Ccw();
			}
		} else {
			// no rotation
		}
	}
}
