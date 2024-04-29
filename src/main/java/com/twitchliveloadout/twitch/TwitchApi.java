package com.twitchliveloadout.twitch;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import javax.annotation.Nullable;

import com.twitchliveloadout.twitch.eventsub.TwitchEventSubType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import okhttp3.*;

import static com.twitchliveloadout.TwitchLiveLoadoutConfig.*;
import static net.runelite.http.api.RuneLiteAPI.JSON;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class TwitchApi
{
	public final static int MAX_PAYLOAD_SIZE = 5120; // bytes

	// this delay supports two clients to sync fast enough while not exceeding the rate limit (1000ms)
	public final static int MIN_SCHEDULE_DEFAULT_DELAY = 1300; // ms
	public final static int MIN_SCHEDULE_LOGGED_OUT_DELAY = 6000; // ms
	public final static int MIN_SCHEDULE_GROUP_DELAY = 6000; // ms
	public final static int MAX_SCHEDULED_STATE_AMOUNT = 50;

	public final static String DEFAULT_EXTENSION_CLIENT_ID = "cuhr4y87yiqd92qebs1mlrj3z5xfp6";
	public final static String DEFAULT_TWITCH_EBS_BASE_URL = "https://liveloadout.com";
	public final static String DEFAULT_TWITCH_BASE_URL = "https://api.twitch.tv/helix/extensions";
	private final static String RATE_LIMIT_REMAINING_HEADER = "Ratelimit-Remaining";

	public final static int MIN_SYNC_DELAY = 0; // ms
	public final static int BASE_SYNC_DELAY = 1000; // ms
	public final static double LOW_RATE_LIMIT_DELAY_MULTIPLIER = 2d;
	public final static int LOW_RATE_LIMIT_REMAINING = 50;
	public final static double HIGH_RATE_LIMIT_DELAY_MULTIPLIER = 0.5d;
	public final static int HIGH_RATE_LIMIT_REMAINING = 90;

	public final static boolean NOTIFY_IN_CHAT_ENABLED = true;
	private final static int SEND_PUBSUB_TIMEOUT_MS = 10 * 1000;
	private final static int GET_CONFIGURATION_SERVICE_TIMEOUT_MS = 5 * 1000;
	private final static int GET_EBS_PRODUCTS_TIMEOUT_MS = 10 * 1000;
	private final static int GET_EBS_TRANSACTIONS_TIMEOUT_MS = 10 * 1000;
	private final static int ENSURE_OAUTH_TOKEN_TIMEOUT_MS = 10 * 1000;
	private final static int CREATE_SUBSCRIPTION_TIMEOUT_MS = 10 * 1000;
	public final static int TRIGGER_OAUTH_REFRESH_TOKEN_TIME_S = 10 * 60; // refresh token x minutes before expiry
	private final static int ERROR_CHAT_MESSAGE_THROTTLE = 15 * 60 * 1000; // in ms
	private final static String USER_AGENT = "RuneLite";
	private final static String TWITCH_CREATE_SUBSCRIPTION_URL = "https://api.twitch.tv/helix/eventsub/subscriptions";
	public final static String TWITCH_VALIDATE_TOKEN_URL = "https://id.twitch.tv/oauth2/validate";
	public final static String DEFAULT_APP_CLIENT_ID = "qaljqu9cfow8biixuat6rbr303ocp2";

	/**
	 * Dedicated scheduler for sending the new state with a stream delay
	 */
	private final ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(1);

	/**
	 * Dedicated HTTP clients for every type of request
	 */
	private final OkHttpClient httpClientTemplate;
	private final ConfigManager configManager;
	private final OkHttpClient ebsTransactionsHttpClient;
	private final OkHttpClient configurationSegmentHttpClient;
	private final OkHttpClient pubSubHttpClient;
	final OkHttpClient ebsProductsHttpClient;
	private final OkHttpClient createSubscriptionHttpClient;

	private final OkHttpClient oAuthTokenHttpClient;

	private final TwitchLiveLoadoutPlugin plugin;
	private final Client client;
	private final TwitchLiveLoadoutConfig config;
	private final ChatMessageManager chatMessageManager;
	private Instant lastScheduleStateTime = null;

	@Getter
	private String lastCompressedState = "";

	@Getter
	private int lastRateLimitRemaining = 100;

	@Getter
	private String lastResponseMessage = "Unknown status";

	@Getter
	private int lastResponseCode = 200;

	@Getter
	private long lastErrorChatMessage = 0;

	private final ConcurrentHashMap<TwitchSegmentType, JsonObject> configurationSegmentContents = new ConcurrentHashMap<>();

	public TwitchApi(TwitchLiveLoadoutPlugin plugin, Client client, TwitchLiveLoadoutConfig config, ChatMessageManager chatMessageManager, OkHttpClient httpClientTemplate, ConfigManager configManager)
	{
		this.plugin = plugin;
		this.client = client;
		this.config = config;
		this.chatMessageManager = chatMessageManager;
		this.httpClientTemplate = httpClientTemplate;
		this.configManager = configManager;

		// instantiate a HTTP client for every call with a different timeout
		ebsTransactionsHttpClient = createHttpClient(GET_EBS_TRANSACTIONS_TIMEOUT_MS);
		configurationSegmentHttpClient = createHttpClient(GET_CONFIGURATION_SERVICE_TIMEOUT_MS);
		pubSubHttpClient = createHttpClient(SEND_PUBSUB_TIMEOUT_MS);
		ebsProductsHttpClient = createHttpClient(GET_EBS_PRODUCTS_TIMEOUT_MS);
		createSubscriptionHttpClient = createHttpClient(CREATE_SUBSCRIPTION_TIMEOUT_MS);
		oAuthTokenHttpClient = createHttpClient(ENSURE_OAUTH_TOKEN_TIMEOUT_MS);
	}

	public void shutDown()
	{
		clearScheduledBroadcasterStates();
		scheduledExecutor.shutdown();
	}

	public void scheduleBroadcasterState(final JsonObject state)
	{
		int delay = config.syncDelay() * 1000;

		// add the base delay, because every streamer
		// has some delay we want to take into account
		// to not show the future of the streamer too quickly
		delay += BASE_SYNC_DELAY;

		// make sure the min sync delay is set when
		// user inputs negative numbers in the config
		if (delay < MIN_SYNC_DELAY)
		{
			delay = MIN_SYNC_DELAY;
		}

		// schedule in the future, this also makes sure the HTTP requests are done on their own thread.
		scheduledExecutor.schedule(new Runnable()
		{
			public void run()
			{
				try {
					sendAsyncPubSubState(state);
				} catch (Exception exception) {
					plugin.logSupport("Could not send the pub sub state due to the following error: ", exception);
				}
			}
		}, delay, TimeUnit.MILLISECONDS);

		lastScheduleStateTime = Instant.now();
	}

	public boolean canScheduleState()
	{

		// guard: if the scheduler is shutdown block all future requests
		if (scheduledExecutor.isShutdown())
		{
			return false;
		}

		// guard: check if the queue is too large
		if (scheduledExecutor.getQueue().size() >= MAX_SCHEDULED_STATE_AMOUNT)
		{
			return false;
		}

		// guard: when state is never send it is allowed
		if (lastScheduleStateTime == null)
		{
			return true;
		}

		boolean isLoggedIn = plugin.isLoggedIn(true);
		Instant now = Instant.now();
		int delayMs = isLoggedIn ? MIN_SCHEDULE_DEFAULT_DELAY : MIN_SCHEDULE_LOGGED_OUT_DELAY;

		// multiply the delay time if the rate limit is decreasing too fast
		// this can be the case with many accounts logged in
		if (lastRateLimitRemaining < LOW_RATE_LIMIT_REMAINING)
		{
			delayMs *= LOW_RATE_LIMIT_DELAY_MULTIPLIER;
		}

		// decrease the delay when there are enough requests available
		// this can be the case with only one RL client open
		if (lastRateLimitRemaining > HIGH_RATE_LIMIT_REMAINING)
		{
			delayMs *= HIGH_RATE_LIMIT_DELAY_MULTIPLIER;
		}

		Instant minTime = lastScheduleStateTime.plusMillis(delayMs);

		return now.isAfter(minTime);
	}

	public void clearScheduledBroadcasterStates()
	{
		scheduledExecutor.getQueue().clear();
	}

	private boolean sendAsyncPubSubState(JsonObject state)
	{
		try {
			final JsonObject data = new JsonObject();
			final JsonArray targets = new JsonArray();
			final String channelId = getChannelId();

			// guard: make sure the channel ID is valid
			if (channelId == null)
			{
				return false;
			}

			targets.add(TwitchPubSubTargetType.BROADCAST.getTarget());
			String compressedState = compressState(state);

			data.addProperty("message", compressedState);
			data.addProperty("broadcaster_id", channelId);
			data.add("target", targets);

			sendAsyncPubSubMessage(data, (Response response) -> {
				verifyStateUpdateResponse("PubSub", response, compressedState);
			}, (exception) -> {
				plugin.logSupport("Could not send pub sub state due to the following error: ", exception);
			});

			lastCompressedState = compressedState;
		} catch (Exception exception) {
			plugin.logSupport("Could not send pub sub state due to the following error: ", exception);
			return false;
		}

		return true;
	}

	private void sendAsyncPubSubMessage(JsonObject data, HttpResponseHandler responseHandler, HttpErrorHandler errorHandler)
	{
		final String url = DEFAULT_TWITCH_BASE_URL +"/pubsub";

		// Documentation: https://dev.twitch.tv/docs/extensions/reference/#send-extension-pubsub-message
		performPostRequest(url, data, pubSubHttpClient, responseHandler, errorHandler);
	}

	public void fetchAsyncEbsProducts(HttpResponseHandler responseHandler, HttpErrorHandler errorHandler)
	{
		String url = DEFAULT_TWITCH_EBS_BASE_URL +"/api/marketplace-products";
		final JsonObject data = new JsonObject();

		if (TwitchLiveLoadoutPlugin.IN_DEVELOPMENT)
		{
			url = "http://localhost:3010/api/marketplace-products";
		}

		performPostRequest(url, data, ebsProductsHttpClient, responseHandler, errorHandler);
	}

	public void fetchAsyncEbsTransactions(String lastTransactionId, HttpResponseHandler responseHandler, HttpErrorHandler errorHandler)
	{
		String url = DEFAULT_TWITCH_EBS_BASE_URL +"/api/marketplace-transactions";
		final JsonObject data = new JsonObject();

		if (TwitchLiveLoadoutPlugin.IN_DEVELOPMENT)
		{
			url = "http://localhost:3010/api/marketplace-transactions";
		}

		// only add last checked at when it is valid
		if (lastTransactionId != null)
		{
			data.addProperty("lastTransactionId", lastTransactionId);
		}

		performPostRequest(url, data, ebsTransactionsHttpClient, responseHandler, errorHandler);
	}

	public void fetchAsyncConfigurationSegment(TwitchSegmentType segmentType) throws Exception
	{
		final String clientId = DEFAULT_EXTENSION_CLIENT_ID;
		final String channelId = getChannelId();
		final String baseUrl = DEFAULT_TWITCH_BASE_URL +"/configurations";

		// guard: make sure the channel ID is valid
		if (channelId == null)
		{
			return;
		}

		final String url = baseUrl +"?broadcaster_id="+ channelId +"&extension_id="+ clientId +"&segment="+ segmentType.getKey();

		// documentation: https://dev.twitch.tv/docs/api/reference#get-extension-configuration-segment
		performGetRequest(url, configurationSegmentHttpClient, (Response response) -> {

			// there is a fair chance the configuration segment is empty when nothing is configured yet
			// for this reason we silently ignore the error
			try {
				String rawSegmentResult = response.body().string();
				JsonObject segmentResult = parseJson(rawSegmentResult);
				String rawSegmentContent = segmentResult
					.getAsJsonArray("data")
					.get(0)
					.getAsJsonObject()
					.get("content")
					.getAsString();
				JsonObject segmentContent = parseJson(rawSegmentContent);

				// cache the response if valid
				configurationSegmentContents.put(segmentType, segmentContent);
			} catch (Exception exception) {
				// empty
			}
		}, (error) -> {
			// empty
		});
	}

	private void verifyStateUpdateResponse(String type, Response response, String compressedState) throws Exception
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

		if (isErrorResponseCode(responseCode))
		{
			log.debug("Could not update state via {}, http code was: {}", type, responseCode);
			log.debug("The state was ({} bytes compressed): ", compressesStateSize);
			log.debug("The response body was {}", responseText);

			// Only send a chat message when the token is not set or expired as other errors
			// also occur due to reliability of the Twitch servers (e.g. random 500's in between).
			// Normally they are good again for the next request.
			if (isAuthErrorResponseCode(responseCode) && NOTIFY_IN_CHAT_ENABLED && isLoggedIn && canSendErrorChatMessage) {
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

			throw new Exception("Could not set the Twitch PubSub State due to invalid response code: "+ responseCode);
		}

		log.debug("Successfully sent state with response code: {}", responseCode);
	}

	public void ensureValidOAuthToken()
	{
		final String accessToken = config.twitchOAuthAccessToken();
		final String refreshToken = config.twitchOAuthRefreshToken();

		// guard: skip this then there is no token set
		if (accessToken.isEmpty() || refreshToken.isEmpty())
		{
			return;
		}

		final Request validateRequest = new Request.Builder()
			.header("Authorization", "OAuth "+ accessToken)
			.header("User-Agent", USER_AGENT)
			.header("Content-Type", "application/json")
			.get()
			.url(TWITCH_VALIDATE_TOKEN_URL)
			.build();

		performRequest(
			validateRequest, 
			oAuthTokenHttpClient,
			(response) -> {
				int responseCode = response.code();

				// guard: skip when no valid body
				if (response.body() == null)
				{
					return;
				}

				// guard: when unauthorized the token is already expired!
				if (responseCode == 401)
				{
					plugin.logSupport("Twitch OAuth access token is already expired. Refreshing...");
					refreshOAuthToken();
					return;
				}

				JsonObject payload = parseJson(response.body().string());
				Integer expiresInS = payload.get("expires_in").getAsInt();
				boolean needsRefresh = expiresInS == null || expiresInS < TRIGGER_OAUTH_REFRESH_TOKEN_TIME_S;

				if (needsRefresh)
				{
					plugin.logSupport("Twitch OAuth access token almost expires in "+ expiresInS +" seconds. Refreshing...");
					refreshOAuthToken();
				}
			},
			(exception) -> {
				log.warn("Could not check whether the Twitch OAuth token is valid: ", exception);
			}
		);
	}

	private void refreshOAuthToken()
	{
		final String refreshToken = config.twitchOAuthRefreshToken();
		String url = DEFAULT_TWITCH_EBS_BASE_URL + "/api/refresh-token";

		// guard: check if the refresh token is valid
		if (refreshToken.isEmpty())
		{
			plugin.logSupport("Will not refresh the Twitch OAuth token because the refresh token is empty.");
			return;
		}

		final JsonObject data = new JsonObject();
		data.addProperty("refresh_token", refreshToken);

		final Request refreshRequest = new Request.Builder()
			.header("User-Agent", USER_AGENT)
			.header("Content-Type", "application/json")
			.post(RequestBody.create(JSON, data.toString()))
			.url(url)
			.build();

		plugin.logSupport("Attempting to refresh the Twitch OAuth token...");

		performRequest(
			refreshRequest,
			oAuthTokenHttpClient,
			(response) -> {
				int responseCode = response.code();

				// guard: skip when no valid body
				if (response.body() == null)
				{
					plugin.logSupport("Could not refresh the Twitch OAuth token due to no body for response code: "+ responseCode);
					return;
				}

				String rawPayload = response.body().string();

				// guard: when unauthorized the token is already expired!
				if (responseCode != 200)
				{
					plugin.logSupport("Could not refresh the Twitch OAuth token due to unexpected response code: "+ responseCode);
					return;
				}

				JsonObject payload = parseJson(rawPayload);
				String newAccessToken = payload.get("access_token").getAsString();
				String newRefreshToken = payload.get("refresh_token").getAsString();

				// guard: make sure the new tokens are valid
				if (newAccessToken == null || newRefreshToken == null || newAccessToken.isEmpty() || newRefreshToken.isEmpty())
				{
					plugin.logSupport("The Twitch OAuth token could not be refreshed as it turns out to be empty.");
					return;
				}

				// when all is valid update the config in the plugin panel
				configManager.setConfiguration("twitchstreamer", TWITCH_OAUTH_ACCESS_TOKEN_KEY, newAccessToken);
				configManager.setConfiguration("twitchstreamer", TWITCH_OAUTH_REFRESH_TOKEN_KEY, newRefreshToken);
				plugin.logSupport("The Twitch OAuth tokens are successfully refreshed and stored in the config panel!");
			},
			(exception) -> {
				log.warn("Could not refresh the Twitch OAuth token: ", exception);
			}
		);
	}

	public void createEventSubSubscription(String sessionId, TwitchEventSubType subscriptionType, HttpResponseHandler onSuccess, HttpErrorHandler onError)
	{
		final String channelId = getChannelId();
		final String token = config.twitchOAuthAccessToken();
		final String type = subscriptionType.getType();
		final int version = subscriptionType.getVersion();

		// guard: check if the auth parameters are valid
		if (channelId == null || token == null) {
			return;
		}

		final JsonObject condition = new JsonObject();

		// check which conditions to add based on the type of event
		// usually we only need to add the broadcaster user ID, but there are some exceptions
		// reference: https://dev.twitch.tv/docs/eventsub/eventsub-subscription-types
		if (subscriptionType == TwitchEventSubType.EXTENSION_BITS_TRANSACTION) {

			// NOTE: this one doesn't actually work because oauth user tokens don't have access to these events...
			// maintain the code here for reference purposes
			condition.addProperty("extension_client_id", DEFAULT_EXTENSION_CLIENT_ID);
		} else if (subscriptionType == TwitchEventSubType.RAID) {
			condition.addProperty("to_broadcaster_user_id", channelId);
		} else if (subscriptionType == TwitchEventSubType.FOLLOW) {
			condition.addProperty("moderator_user_id", channelId);
			condition.addProperty("broadcaster_user_id", channelId);
		} else {
			condition.addProperty("broadcaster_user_id", channelId);
		}

		final JsonObject transport = new JsonObject();
		transport.addProperty("method", "websocket");
		transport.addProperty("session_id", sessionId);
		final JsonObject data = new JsonObject();
		data.addProperty("type", type);
		data.addProperty("version", version);
		data.add("condition", condition);
		data.add("transport", transport);

		final Request request = new Request.Builder()
			.header("Client-ID", DEFAULT_APP_CLIENT_ID)
			.header("Authorization", "Bearer "+ token)
			.header("User-Agent", USER_AGENT)
			.header("Content-Type", "application/json")
			.post(RequestBody.create(JSON, data.toString()))
			.url(TWITCH_CREATE_SUBSCRIPTION_URL)
			.build();

		createSubscriptionHttpClient.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException exception) {
				plugin.logSupport("Could not create Twitch websocket subscription: "+ type);
				plugin.logSupport("The error that occurred was: ");
				plugin.logSupport(exception.getMessage());
				onError.execute(exception);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException {
				int responseCode = response.code();

				if (responseCode == 202) {
					plugin.logSupport("Successfully created Twitch websocket subscription for type: "+ type);
					try {
						onSuccess.execute(response);
					} catch (Exception exception) {
						onError.execute(exception);
					}
				} else {
					plugin.logSupport("Could not create Twitch websocket subscription (type: "+ type +") due to error code: "+ responseCode);
					plugin.logSupport("And response body: "+ response.body().string());
					onError.execute(new Exception("Could not create Twitch websocket subscription due to error code: "+ responseCode));
				}

				// always close the response to be sure there are no memory leaks
				response.close();
			}
		});
	}

	@Nullable
	public String getChannelId()
	{

		try {
			JsonObject decodedToken = getDecodedToken();
			return decodedToken.get("channel_id").getAsString();
		} catch (Exception exception) {
			// empty
		}

		return null;
	}

	public JsonObject getDecodedToken() throws Exception
	{
		String[] parts = splitToken(getToken());
		String payloadBase64String = parts[1];
		String payloadString = new String(Base64.getDecoder().decode(payloadBase64String), StandardCharsets.UTF_8);;
		JsonObject payload = parseJson(payloadString);

		return payload;
	}

	public JsonObject getConfigurationSegmentContent(TwitchSegmentType segmentType)
	{
		return configurationSegmentContents.get(segmentType);
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

	private String getToken()
	{
		return config.twitchToken();
	}

	private JsonObject parseJson(String rawJson)
	{
		return (new JsonParser()).parse(rawJson).getAsJsonObject();
	}

	/**
	 * Perform a generic GET request to the Twitch API.
	 */
	public void performGetRequest(String url, OkHttpClient httpClient, HttpResponseHandler responseHandler, HttpErrorHandler errorHandler)
	{
		final String token = config.twitchToken();
		final Request request = new Request.Builder()
			.header("Client-ID", DEFAULT_EXTENSION_CLIENT_ID)
			.header("Authorization", "Bearer "+ token)
			.header("User-Agent", USER_AGENT)
			.header("Content-Type", "application/json")
			.get()
			.url(url)
			.build();

		performRequest(request, httpClient, responseHandler, errorHandler);
	}

	/**
	 * Perform a generic POST request to the Twitch API.
	 */
	public void performPostRequest(String url, JsonObject data, OkHttpClient httpClient, HttpResponseHandler responseHandler, HttpErrorHandler errorHandler)
	{
		final String token = config.twitchToken();
		final Request request = new Request.Builder()
			.header("Client-ID", DEFAULT_EXTENSION_CLIENT_ID)
			.header("Authorization", "Bearer "+ token)
			.header("User-Agent", USER_AGENT)
			.header("Content-Type", "application/json")
			.post(RequestBody.create(JSON, data.toString()))
			.url(url)
			.build();

		performRequest(request, httpClient, responseHandler, errorHandler);
	}

	/**
	 * Perform a generic request to the Twitch API.
	 */
	public void performRequest(Request request, OkHttpClient httpClient, HttpResponseHandler responseHandler, HttpErrorHandler errorHandler)
	{
		final HttpUrl url = request.url();

		// queue the request on the OkHttp thread pool to prevent blocking other threads
		httpClient.newCall(request).enqueue(new Callback() {
			@Override
			public void onFailure(Call call, IOException exception) {
				plugin.logSupport("Could not send request to: "+ url);
				plugin.logSupport("The error that occurred was: ");
				plugin.logSupport(exception.getMessage());
				errorHandler.execute(exception);
			}

			@Override
			public void onResponse(Call call, Response response) {
				try {
					responseHandler.execute(response);
				} catch (Exception exception) {
					plugin.logSupport("Could not handle the response that was received from: "+ url);
					plugin.logSupport(exception.getMessage());
				}

				// always close the response to be sure there are no memory leaks
				response.close();
			}
		});
	}

	/**
	 * Create a new HTTP client instance with a specific timeout
	 */
	public OkHttpClient createHttpClient(int timeoutMs)
	{
		return httpClientTemplate
			.newBuilder()
			.callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
			.build();
	}

	public interface HttpResponseHandler {
		public void execute(Response response) throws Exception;
	}

	public interface HttpErrorHandler {
		public void execute(Exception error);
	}
}
