package net.runelite.client.plugins.twitchliveloadout;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import static net.runelite.http.api.RuneLiteAPI.JSON;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class TwitchApi
{
	public final static int MAX_PAYLOAD_SIZE = 5120;
	public final static int MIN_SYNC_DELAY = 0;
	public final static int BASE_SYNC_DELAY = 2;
	public final static boolean CHAT_ERRORS_ENABLED = false;
	public final static String DEFAULT_EXTENSION_CLIENT_ID = "cuhr4y87yiqd92qebs1mlrj3z5xfp6";
	private final static String BROADCASTER_SEGMENT = "broadcaster";
	private final static int ERROR_CHAT_MESSAGE_THROTTLE = 5 * 60 * 1000; // in ms
	private final static String VERSION = "0.0.1";
	private final static String USER_AGENT = "RuneLite";
	private enum PubSubTarget {
		BROADCAST("broadcast"),
		GLOBAL("global");

		private final String target;

		PubSubTarget(String target) {
			this.target = target;
		}
	}

	private final OkHttpClient httpClient = new OkHttpClient();
	private final ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(1);

	private final TwitchLiveLoadoutPlugin plugin;
	private final Client client;
	private final TwitchLiveLoadoutConfig config;
	private final ChatMessageManager chatMessageManager;
	private String lastCompressedState = "";
	private String lastConfigurationServiceState = "";
	private String lastResponseMessage = "";
	private int lastResponseCode = 200;
	private long lastErrorChatMessage = 0;

	public TwitchApi(TwitchLiveLoadoutPlugin plugin, Client client, TwitchLiveLoadoutConfig config, ChatMessageManager chatMessageManager)
	{
		this.plugin = plugin;
		this.client = client;
		this.config = config;
		this.chatMessageManager = chatMessageManager;
	}

	public void scheduleBroadcasterState(final JsonObject state)
	{
		int delay = config.syncDelay();

		if (delay < MIN_SYNC_DELAY)
		{
			delay = MIN_SYNC_DELAY;
		}

		// add the base delay
		delay += BASE_SYNC_DELAY;

		scheduledExecutor.schedule(new Runnable()
		{
			public void run()
			{
				boolean serviceUpdateResult = setConfigurationService(state);
				boolean pubSubResult = sendPubSubState(state);
				boolean validResults = serviceUpdateResult && pubSubResult;
			}
		}, delay, TimeUnit.SECONDS);
	}

	public void clearScheduledBroadcasterStates()
	{
		scheduledExecutor.getQueue().clear();
	}

	private boolean setConfigurationService(JsonObject state)
	{
		final String segment = BROADCASTER_SEGMENT;
		final String version = VERSION;
		final JsonObject data = new JsonObject();
		final String compressedState = compressState(state);

		lastCompressedState = compressedState;

		if (compressedState.equals(lastConfigurationServiceState))
		{
			return false;
		}

		try {
			data.addProperty("segment", segment);
			data.addProperty("version", version);
			data.addProperty("content", compressedState);
			data.addProperty("channel_id", getChannelId());
		} catch (Exception exception) {
			log.error("An error occurred when constructing the payload for Twitch state update:");
			log.error(exception.toString());
		}

		log.debug("Sending out {} state (v{}):", segment, version);
		log.debug(state.toString());
		log.debug("Compressed state:");
		log.debug(compressedState);

		try {
			Response response = performConfigurationServiceRequest(data);
			verifyStateUpdateResponse("ConfigurationService", response, state, compressedState);
		} catch (Exception exception) {
			return false;
		}

		lastConfigurationServiceState = compressedState;
		return true;
	}

	private Response performConfigurationServiceRequest(JsonObject data) throws IOException {
		final String dataString = data.toString();
		final String clientId = config.extensionClientId();
		final String token = config.twitchToken();
		final String url = "https://api.twitch.tv/v5/extensions/"+ clientId +"/configurations/";

		// Documentation: https://dev.twitch.tv/docs/extensions/reference/#set-extension-configuration-segment
		Request request = new Request.Builder()
			.header("Client-ID", clientId)
			.header("Authorization", "Bearer "+ token)
			.header("User-Agent", USER_AGENT)
			.put(RequestBody.create(JSON, dataString))
			.url(url)
			.build();

		Response response = httpClient.newCall(request).execute();
		return response;
	}

	private boolean sendPubSubState(JsonObject state)
	{
		final JsonObject data = new JsonObject();
		final JsonArray targets = new JsonArray();
		targets.add(PubSubTarget.BROADCAST.target);
		String compressedState = compressState(state);

		data.addProperty("content_type", "application/json");
		data.addProperty("message", compressedState);
		data.add("targets", targets);

		try {
			Response response = performPubSubRequest(data);
			verifyStateUpdateResponse("PubSub", response, state, compressedState);
		} catch (Exception exception) {
			return false;
		}

		return true;
	}

	private Response performPubSubRequest(JsonObject data) throws Exception
	{
		final String clientId = config.extensionClientId();
		final String token = config.twitchToken();
		final String channelId = getChannelId();
		final String url = "https://api.twitch.tv/extensions/message/"+ channelId;
		final String dataString = data.toString();

		// Documentation: https://dev.twitch.tv/docs/extensions/reference/#send-extension-pubsub-message
		Request request = new Request.Builder()
			.header("Client-ID", clientId)
			.header("Authorization", "Bearer "+ token)
			.header("User-Agent", USER_AGENT)
			.post(RequestBody.create(JSON, dataString))
			.url(url)
			.build();

		Response response = httpClient.newCall(request).execute();
		return response;
	}

	private void verifyStateUpdateResponse(String type, Response response, JsonObject state, String compressedState) throws Exception
	{
		final int responseCode = response.code();
		final String responseText = response.body().string();
		final int compressesStateSize = compressedState.getBytes("UTF-8").length;
		final long now = Instant.now().getEpochSecond();
		final long errorChatMessageDeltaTime = now - lastErrorChatMessage;
		final boolean isLoggedIn = client.getGameState() == GameState.LOGGED_IN;
		final boolean canSendErrorChatMessage = errorChatMessageDeltaTime > ERROR_CHAT_MESSAGE_THROTTLE;
		String responseCodeMessage = "An unknown error occurred. Please report this to the RuneLite plugin maintainer.";

		// handle specific errors
		switch (responseCode)
		{
			case 403: // forbidden
			case 401: // unauthorized
				responseCodeMessage = "Twitch Extension Token is expired. Get a new token via the Twitch extension configuration and copy it to the RuneLite plugin settings.";
				break;
			case 400: // bad request
			case 404: // not found
				responseCodeMessage = "Something has changed with Twitch. Please report this to the RuneLite plugin maintainer.";
				break;
		}

		// set default success message
		if (!isErrorResponseCode(responseCode))
		{
			responseCodeMessage = "The latest information is successfully synced to Twitch.";
		}

		response.close();
		lastResponseMessage = responseCodeMessage;
		lastResponseCode = responseCode;
		plugin.updateConnectivityPanel();

		if (isErrorResponseCode(responseCode))
		{
			log.error("Could not update state, http code was: {}", responseCode);
			log.error("The state was ({} bytes compressed): ", compressesStateSize);
			log.error("The response body was {}", responseText);
			log.error(state.toString());

			if (CHAT_ERRORS_ENABLED && isLoggedIn && canSendErrorChatMessage) {
				final ChatMessageBuilder message = new ChatMessageBuilder()
					.append(ChatColorType.HIGHLIGHT)
					.append("Could not synchronize loadout to Twitch " + type + " (code: " + responseCode + "). ")
					.append(responseCodeMessage)
					.append(ChatColorType.NORMAL);

				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.ITEM_EXAMINE)
					.runeLiteFormattedMessage(message.build())
					.build());
				lastErrorChatMessage = now;
			}

			throw new Exception("Could not set the Twitch Configuration State due to invalid response code: "+ responseCode);
		}

		log.debug("Successfully sent state: {}", responseCode);
		lastErrorChatMessage = 0;
	}

	private String getChannelId() throws Exception
	{
		JsonObject decodedToken = getDecodedToken();
		return decodedToken.get("channel_id").getAsString();
	}

	public JsonObject getDecodedToken() throws Exception
	{
		String[] parts = splitToken(getToken());
		String payloadBase64String = parts[1];
		String payloadString = new String(Base64.getDecoder().decode(payloadBase64String), StandardCharsets.UTF_8);;
		JsonObject payload = new JsonParser().parse(payloadString).getAsJsonObject();

		return payload;
	}

	private String[] splitToken(String token) throws Exception {
		String[] parts = token.split("\\.");

		if (parts.length == 2 && token.endsWith("."))
		{
			parts = new String[]{parts[0], parts[1], ""};
		}

		if (parts.length != 3)
		{
			throw new Exception(String.format("The token was expected to have 3 parts, but got %s.", parts.length));
		}

		return parts;
	}

	public String compressState(JsonObject state)
	{
		try {
			String jsonString = state.toString();
			byte[] compressedState = compress(jsonString);
			String compressedStateString = new String(Base64.getEncoder().encode(compressedState), StandardCharsets.UTF_8);

			return compressedStateString;
		} catch (Exception exception) {
			// empty?
		}

		return null;
	}

	public static byte[] compress(final String str) throws IOException
	{
		if ((str == null) || (str.length() == 0))
		{
			return null;
		}

		// Source: https://stackoverflow.com/questions/16351668/compression-and-decompression-of-string-data-in-java
		ByteArrayOutputStream obj = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(obj);
		gzip.write(str.getBytes("UTF-8"));
		gzip.flush();
		gzip.close();
		return obj.toByteArray();
	}

	public boolean isErrorResponseCode(int responseCode)
	{
		return responseCode > 299 || responseCode < 200;
	}

	public String getToken()
	{
		return config.twitchToken();
	}

	public String getLastCompressedState()
	{
		return lastCompressedState;
	}

	public String getLastResponseMessage()
	{
		return lastResponseMessage;
	}

	public int getLastResponseCode()
	{
		return lastResponseCode;
	}
}
