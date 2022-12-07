package com.twitchliveloadout.marketplace;

import net.runelite.api.JagexColor;
import net.runelite.api.ModelData;

import lombok.extern.slf4j.Slf4j;

import java.awt.*;

import static com.twitchliveloadout.marketplace.MarketplaceConstants.RUNELITE_OBJECT_FULL_RADIUS;

@Slf4j
public class MarketplaceModelUtilities {

	public static void scaleModel(ModelData model, double modelScale)
	{
		int modelSize = (int) (RUNELITE_OBJECT_FULL_RADIUS * modelScale);

		model.cloneVertices();
		model.scale(modelSize, modelSize, modelSize);
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
		model.cloneVertices();
		model.recolor(faceColor, JagexColor.rgbToHSL(rgb, brightness));
	}

	public static void rotateModel(ModelData modelData, double angleDegrees)
	{
		modelData.cloneVertices();

		double angleRadians = Math.toRadians(angleDegrees);
		for(int verticesIndex = 0; verticesIndex < modelData.getVerticesCount(); ++verticesIndex) {
			int[] xVertices = modelData.getVerticesX();
			int[] yVertices = modelData.getVerticesY();
			int[] zVertices = modelData.getVerticesZ();
			int x = xVertices[verticesIndex];
			int y = yVertices[verticesIndex];
			int z = zVertices[verticesIndex];

			xVertices[verticesIndex] = (int) (x*Math.cos(angleRadians) + z*Math.sin(angleRadians));
			yVertices[verticesIndex] = y;
			zVertices[verticesIndex] = (int) (z*Math.cos(angleRadians) - x*Math.sin(angleRadians));
		}
	}
}
