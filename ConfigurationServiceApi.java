package net.runelite.client.plugins.twitchstreamer;

import com.google.gson.JsonObject;
import okhttp3.MediaType;
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

	/**
	 * Constants.
	 */
	private final static String BROADCASTER_SEGMENT = "broadcaster";
	private final static String VERSION = "1.0";

	OkHttpClient httpClient = new OkHttpClient();

	public boolean setBroadcasterState(JsonObject state)
	{
		return set(BROADCASTER_SEGMENT, VERSION, state);
	}

	/**
	 * Set a Configuration Service value using Gzip compression. This can
	 * save up to 50% in size when working with the 5KB limit of Twitch.
	 * @param segment
	 * @param version
	 * @param state
	 * @return
	 */
	public boolean set(String segment, String version, JsonObject state)
	{
		JsonObject data = new JsonObject();
		String compressedState = compressState(state);

		// TODO: fetch from JWT
		data.addProperty("channel_id", "534266944");
		data.addProperty("segment", segment);
		data.addProperty("version", version);
		data.addProperty("content", compressedState);

		// TMP: debug
		System.out.println("Sending out "+ segment +" state (v"+ version +"):");
		System.out.println(state.toString());
		System.out.println("Compressed state:");
		System.out.println(compressedState);

		try {
			Response response = performPutRequest(data);
			System.out.println("Content is set! Status code: "+ response.code());
			System.out.println(response.body().toString());
			response.close();
		} catch (Exception exception) {
			System.out.println("Could not set state");
			return false;
		}

		return true;
	}

	private Response performPutRequest(JsonObject data) throws IOException {
		String dataString = data.toString();
		Request request = new Request.Builder()
			.header("Client-ID", "cuhr4y87yiqd92qebs1mlrj3z5xfp6")
			.header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJleHAiOjE1OTA0MzMzNDQsIm9wYXF1ZV91c2VyX2lkIjoiVTUzNDI2Njk0NCIsInJvbGUiOiJicm9hZGNhc3RlciIsInB1YnN1Yl9wZXJtcyI6eyJsaXN0ZW4iOlsiYnJvYWRjYXN0IiwiZ2xvYmFsIl0sInNlbmQiOlsiYnJvYWRjYXN0Il19LCJjaGFubmVsX2lkIjoiNTM0MjY2OTQ0IiwidXNlcl9pZCI6IjUzNDI2Njk0NCIsImlhdCI6MTU5MDM0Njk0NH0.zXQQ42VZ1dFvno84Sq8QiUPRYRrhoS3zxqGw90-KN20")
			.put(RequestBody.create(JSON, dataString))
			.url("https://api.twitch.tv/v5/extensions/cuhr4y87yiqd92qebs1mlrj3z5xfp6/configurations/")
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
