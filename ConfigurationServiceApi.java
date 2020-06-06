package net.runelite.client.plugins.twitchstreamer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static net.runelite.http.api.RuneLiteAPI.JSON;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;

public class ConfigurationServiceApi {

	private final static String BROADCASTER_SEGMENT = "broadcaster";
	private final static String VERSION = "1.0";

	OkHttpClient httpClient = new OkHttpClient();

	private TwitchStreamerConfig config;

	private String lastCompressedState;

	public ConfigurationServiceApi(TwitchStreamerConfig config)
	{
		this.config = config;
	}

	public boolean setBroadcasterState(JsonObject state)
	{
		return setConfigurationService(BROADCASTER_SEGMENT, VERSION, state);
	}

	/**
	 * Set a Configuration Service value using Gzip compression. This can
	 * save up to 50% in size when working with the 5KB limit of Twitch.
	 * @param segment
	 * @param version
	 * @param state
	 * @return
	 */
	public boolean setConfigurationService(String segment, String version, JsonObject state)
	{
		JsonObject data = new JsonObject();
		String compressedState = compressState(state);

		if (compressedState.equals(lastCompressedState))
		{
			return false;
		}

		try {
			data.addProperty("channel_id", getChannelId());
			data.addProperty("segment", segment);
			data.addProperty("version", version);
			data.addProperty("content", compressedState);
		} catch (Exception exception) {

			// TMP: debug
			System.out.println("Could construct payload");
			System.out.println(exception);
			return false;
		}

		// TMP: debug
		System.out.println("Sending out "+ segment +" state (v"+ version +"):");
		System.out.println(state.toString());
		System.out.println("Compressed state:");
		System.out.println(compressedState);

		try {
			Response response = performPutRequest(data);
			int responseCode = response.code();

			if (responseCode > 299) {
				throw new Exception("Could not set the Twitch Configuration State.");
			}

			// TMP: debug
			System.out.println("Successfully sent state: "+ responseCode);

			response.close();
		} catch (Exception exception) {

			// TMP: debug
			System.out.println("Could not send state:");
			System.out.println(exception);
			return false;
		}

		lastCompressedState = compressedState;
		return true;
	}

	private String getChannelId() throws Exception
	{
		JsonObject decodedToken = getDecodedToken();
		return decodedToken.get("channel_id").getAsString();
	}

	private JsonObject getDecodedToken() throws Exception
	{
		String[] parts = splitToken(config.twitchToken());
		String payloadBase64String = parts[1];
		String payloadString = new String(Base64.getDecoder().decode(payloadBase64String), StandardCharsets.UTF_8);;
		JsonObject payload = new JsonParser().parse(payloadString).getAsJsonObject();

		return payload;
	}

	static String[] splitToken(String token) throws Exception {
		String[] parts = token.split("\\.");
		if (parts.length == 2 && token.endsWith(".")) {
			//Tokens with alg='none' have empty String as Signature.
			parts = new String[]{parts[0], parts[1], ""};
		}
		if (parts.length != 3) {
			throw new Exception(String.format("The token was expected to have 3 parts, but got %s.", parts.length));
		}
		return parts;
	}

	private Response performPutRequest(JsonObject data) throws IOException {
		String dataString = data.toString();
		String clientId = config.extensionClientId();
		String token = config.twitchToken();
		String url = "https://api.twitch.tv/v5/extensions/"+ clientId +"/configurations/";
		Request request = new Request.Builder()
			.header("Client-ID", clientId)
			.header("Authorization", "Bearer "+ token)
			.put(RequestBody.create(JSON, dataString))
			.url(url)
			.build();

		Response response = httpClient.newCall(request).execute();
		return response;
	}

	/**
	 * Compress a JSON state object using GZIP.
	 * @param state
	 * @return
	 */
	public String compressState(JsonObject state)
	{
		try {
			String jsonString = state.toString();
			byte[] compressedState = compress(jsonString);
			String compressedStateString = new String(Base64.getEncoder().encode(compressedState), StandardCharsets.UTF_8);

			// TODO: check if without base64 is also possible, like this
			//String compressedStateString = new String(compressedState, StandardCharsets.UTF_8);

			return compressedStateString;
		} catch (Exception exception) {
			// empty?
		}

		return null;
	}

	/**
	 * Compress a string using Gzip.
	 * Source: https://stackoverflow.com/questions/16351668/compression-and-decompression-of-string-data-in-java
	 * @param str
	 * @return
	 * @throws IOException
	 */
	public static byte[] compress(final String str) throws IOException
	{
		if ((str == null) || (str.length() == 0)) {
			return null;
		}
		ByteArrayOutputStream obj = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(obj);
		gzip.write(str.getBytes("UTF-8"));
		gzip.flush();
		gzip.close();
		return obj.toByteArray();
	}
}
