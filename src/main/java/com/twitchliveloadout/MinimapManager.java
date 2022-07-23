package com.twitchliveloadout;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Slf4j
public class MinimapManager {
	private final TwitchLiveLoadoutPlugin plugin;
	private final TwitchState twitchState;
	private final Client client;

	public MinimapManager(TwitchLiveLoadoutPlugin plugin, TwitchState twitchState, Client client)
	{
		this.plugin = plugin;
		this.twitchState = twitchState;
		this.client = client;
	}

	public void updateMinimap()
	{
		plugin.runeOnClientThread(() -> {
			try {
				String miniMap = getMiniMapAsBase64();
			} catch (Exception exception) {
				log.warn("Could not update minimap: "+ exception);
			}
		});
	}

	private String getMiniMapAsBase64() throws IOException
	{
		BufferedImage image = getMiniMapAsBufferedImage();

		if (image == null)
		{
			return null;
		}

		String base64Image = convertBufferedImageToBase64(image);
		return base64Image;
	}

	private BufferedImage getMiniMapAsBufferedImage()
	{
		if (!plugin.isLoggedIn())
		{
			return null;
		}

		int tileSize = 4;
		int sceneSize = 104;
		int radiusAroundPlayer = 12;
		int plane = client.getPlane();
		Tile[][] planeTiles = client.getScene().getTiles()[plane];

		LocalPoint playerLocation = client.getLocalPlayer().getLocalLocation();
		int playerX = playerLocation.getSceneX();
		int playerY = (planeTiles[0].length - 1) - playerLocation.getSceneY(); // flip the y-axis

		SpritePixels map = client.drawInstanceMap(plane);
		int fullWidth = map.getWidth();
		int fullHeight = map.getHeight();
		int[] pixels = map.getPixels();

		BufferedImage image = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, fullWidth, fullHeight, pixels, 0, fullWidth);

		// first crop to the scene
		image = image.getSubimage(48, 48, tileSize * sceneSize, tileSize * sceneSize);

		// now crop to the requested area
		image = image.getSubimage(
				(playerX - radiusAroundPlayer) * tileSize,
				(playerY - radiusAroundPlayer) * tileSize,
				radiusAroundPlayer * 2 * tileSize,
				radiusAroundPlayer * 2 * tileSize
		);

		return image;
	}

	private String convertBufferedImageToBase64(BufferedImage image) throws IOException
	{
		final ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageIO.write(image, "png", os);

		return Base64.getEncoder().encodeToString(os.toByteArray());
	}
}
