package net.runelite.client.plugins.twitchstreamer;

import net.runelite.api.Client;
import net.runelite.api.SpritePixels;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static net.runelite.api.Constants.CLIENT_DEFAULT_ZOOM;

public class SpriteEncoder {

	/**
	 * Constants.
	 */
	private final String DEFAULT_IMAGE_TYPE = "png";
	private final int ITEM_SPRITE_SCALE = (int) (CLIENT_DEFAULT_ZOOM / 2.0f);

	/**
	 * Client used to get item information
	 */
	private Client client;

	/**
	 * Constructor.
	 * @param client
	 */
	public SpriteEncoder(Client client)
	{
		this.client = client;
	}

	/**
	 * Create a default base64 encoded image for an item.
	 * @param itemId
	 * @return Base64 encoded image
	 */
	public String getEncodedSpriteByItemId(int itemId)
	{
		final int quantity = 1;
		final int border = 1;
		final int stackable = 0;
		final boolean noted = false;

		try {
			SpritePixels sprite = client.createItemSprite(
				itemId, quantity, border,
				SpritePixels.DEFAULT_SHADOW_COLOR, stackable, noted, ITEM_SPRITE_SCALE
			);

			return getEncodedSpriteByPixels(sprite);
		} catch (Exception e) {
			// empty?
		}

		return null;
	}

	/**
	 * Convert any sprite to base64 encoded string.
	 * @param pixels
	 * @return Base64 encoded image
	 */
	public String getEncodedSpriteByPixels(SpritePixels pixels)
	{
		return getEncodedSpriteByImage(pixels.toBufferedImage(), DEFAULT_IMAGE_TYPE);
	}

	/**
	 * Converts an image buffer to a base64 encoded string.
	 * Source: https://stackoverflow.com/questions/7178937/java-bufferedimage-to-png-format-base64-string
	 * @param image
	 * @param type
	 * @return Base64 encoded image
	 */
	public String getEncodedSpriteByImage(BufferedImage image, String type) {
		String imageString = null;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();

		try {
			ImageIO.write(image, type, bos);
			byte[] imageBytes = bos.toByteArray();

			Base64.Encoder encoder = Base64.getEncoder();
			imageString = encoder.encodeToString(imageBytes);

			bos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return imageString;
	}
}
