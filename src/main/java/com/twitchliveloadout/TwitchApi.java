package com.twitchliveloadout;

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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class TwitchApi
{
	public final static int MAX_PAYLOAD_SIZE = 5120;
	public final static int MIN_SCHEDULE_DELAY = 1500; // ms
	public final static int MIN_SYNC_DELAY = 0; // ms
	public final static int BASE_SYNC_DELAY = 1000; // ms
	public final static boolean CHAT_ERRORS_ENABLED = true;
	public final static String DEFAULT_EXTENSION_CLIENT_ID = "cuhr4y87yiqd92qebs1mlrj3z5xfp6";
	private final static String BROADCASTER_SEGMENT = "broadcaster";
	private final static String RATE_LIMIT_REMAINING_HEADER = "ratelimit-ratelimitermessagesbychannel-remaining";
	private final static int ERROR_CHAT_MESSAGE_THROTTLE = 15 * 60 * 1000; // in ms
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
	private Instant lastScheduleStateTime = null;
	private String lastCompressedState = "";
	private int lastRateLimitRemaining = 100;
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

	public void shutDown()
	{
		clearScheduledBroadcasterStates();
		scheduledExecutor.shutdown();
	}

	public boolean scheduleBroadcasterState(final JsonObject state)
	{
		int delay = config.syncDelay() * 1000;

		if (!isSelectedMultiLogDisplayName())
		{
			return false;
		}

		if (!canScheduleState())
		{
			return false;
		}

		// add the base delay
		delay += BASE_SYNC_DELAY;

		if (delay < MIN_SYNC_DELAY)
		{
			delay = MIN_SYNC_DELAY;
		}

		scheduledExecutor.schedule(new Runnable()
		{
			public void run()
			{
				sendPubSubState(state);
			}
		}, delay, TimeUnit.MILLISECONDS);

		lastScheduleStateTime = Instant.now();
		return true;
	}

	public boolean isSelectedMultiLogDisplayName()
	{
		final String multiLogDisplayName = config.multiLogDisplayName();
		final String currentDisplayName = plugin.getPlayerName();

		if (multiLogDisplayName == null || multiLogDisplayName.equals(""))
		{
			return true;
		}

		if (multiLogDisplayName.equals(currentDisplayName))
		{
			return true;
		}

		return false;
	}

	public boolean canScheduleState()
	{

		if (lastScheduleStateTime == null)
		{
			return true;
		}

		final Instant now = Instant.now();
		final Instant minTime = lastScheduleStateTime.plusMillis(MIN_SCHEDULE_DELAY);

		return now.isAfter(minTime);
	}

	public void clearScheduledBroadcasterStates()
	{
		scheduledExecutor.getQueue().clear();
	}

	private Response performPubSubRequest(JsonObject data) throws Exception
	{
		final String clientId = config.extensionClientId();
		final String token = config.twitchToken();
		final String url = "https://api.twitch.tv/helix/extensions/pubsub";
		final String dataString = data.toString();

		// Documentation: https://dev.twitch.tv/docs/extensions/reference/#send-extension-pubsub-message
		Request request = new Request.Builder()
			.header("Client-ID", clientId)
			.header("Authorization", "Bearer "+ token)
			.header("User-Agent", USER_AGENT)
			.header("Content-Type", "application/json")
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
		final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
		final String nowFormatted = Instant.ofEpochSecond(now).atZone(ZoneId.systemDefault()).format(dateFormatter);
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

		// append the time
		responseCodeMessage += " The time of this message was: "+ nowFormatted;

		response.close();
		lastResponseMessage = responseCodeMessage;
		lastResponseCode = responseCode;
		lastRateLimitRemaining = Integer.parseInt(response.header(RATE_LIMIT_REMAINING_HEADER, "100"));
		plugin.updateConnectivityPanel();

		if (isErrorResponseCode(responseCode))
		{
			log.error("Could not update state via {}, http code was: {}", type, responseCode);
			log.error("The state was ({} bytes compressed): ", compressesStateSize);
			log.error("The response body was {}", responseText);
			log.error(state.toString());

			// Only send a chat message when the token is not set or expired as other errors
			// also occur due to reliability of the Twitch servers (e.g. random 500's in between).
			// Normally they are good again for the next request.
			if (isAuthErrorResponseCode(responseCode) && CHAT_ERRORS_ENABLED && isLoggedIn && canSendErrorChatMessage) {
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

	public boolean isAuthErrorResponseCode(int responseCode)
	{
		// it seems that 401 is also sometimes randomly triggered, 403 is most reliable
		return responseCode == 403;
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

	public int getLastRateLimitRemaining()
	{
		return lastRateLimitRemaining;
	}
}
