package com.twitchliveloadout.twitch.eventsub;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchApi;
import com.twitchliveloadout.twitch.eventsub.messages.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static net.runelite.http.api.RuneLiteAPI.JSON;

@Slf4j
public class TwitchEventSubClient {
    private final static String DEFAULT_TWITCH_WEBSOCKET_URL = "wss://eventsub.wss.twitch.tv/ws";
//    private final static String DEFAULT_TWITCH_WEBSOCKET_URL = "ws://127.0.0.1:8080/ws";
    private final static String TWITCH_CREATE_SUBSCRIPTION_URL = "https://api.twitch.tv/helix/eventsub/subscriptions";
    public final static String DEFAULT_APP_CLIENT_ID = "qaljqu9cfow8biixuat6rbr303ocp2";
    private final static String USER_AGENT = "RuneLite";
    private final static int CREATE_SUBSCRIPTION_TIMEOUT_MS = 10_000;

    private final TwitchLiveLoadoutPlugin plugin;
    private final TwitchLiveLoadoutConfig config;
    private final TwitchApi twitchApi;
    private final Gson gson;
    private final OkHttpClient httpClientTemplate;
    private final OkHttpClient createSubscriptionHttpClient;
    private WebSocket webSocket;

    private String websocketUrl = DEFAULT_TWITCH_WEBSOCKET_URL;
    private String sessionId = "";
    private int keepAliveTimeoutS = 10;
    private Instant lastKeepAliveAt = Instant.now();
    private boolean shouldReconnect = true;
    private boolean socketOpen = false;

    private final SocketListener socketListener;

    public TwitchEventSubClient(TwitchLiveLoadoutPlugin plugin, TwitchLiveLoadoutConfig config, TwitchApi twitchApi, Gson gson, OkHttpClient httpClientTemplate)
    {
        this.plugin = plugin;
        this.config = config;
        this.twitchApi = twitchApi;
        this.gson = gson;
        this.httpClientTemplate = httpClientTemplate;
        this.createSubscriptionHttpClient = createHttpClient(CREATE_SUBSCRIPTION_TIMEOUT_MS);

        socketListener = new SocketListener()
        {
            @Override
            public void onReady() {

                // once the socket is ready we can create the subscriptions
                createSubscriptions();
            }

            @Override
            public void onMessage(String type, JsonObject payload) {
                log.info("Received new subscription event: {}: {}", type, payload);

                if (type.equals("reward-redeemed")) {
                    // HANDLE
                }
            }
        };

        // instantly attempt to connect
        connect();
    }

    public boolean isConnected()
    {
        return webSocket != null && socketOpen && !sessionId.isEmpty() && Instant.now().minusSeconds(keepAliveTimeoutS).isBefore(lastKeepAliveAt);
    }

    private synchronized void connect()
    {
        log.info("Initialising the websocket for url: {}", websocketUrl);

        // guard: disconnect when one already exists
        if (webSocket != null) {
            disconnect();
        }

        OkHttpClient client = createHttpClient(10_000);
        Request request = new Request.Builder().url(websocketUrl).build();
        webSocket = client.newWebSocket(request, webSocketListener);
        shouldReconnect = true;
    }

    public synchronized void reconnect()
    {
        connect();
    }

    public <T extends MessageData> void sendMessage(Message<T> message) throws IOException, RuntimeException
    {

        // guard: skip when not connected
        if (!isConnected()) {
            return;
        }

        var type = new TypeToken<Message<T>>() {}.getType();
        if (message.data instanceof INeedAuth) {
            ((INeedAuth) message.data).setAuthToken(config.twitchOAuthAccessToken());
        }

        var jsonMessage = gson.toJson(message, type);

        log.info("Send Twitch websocket message: {}", jsonMessage);
        webSocket.send(jsonMessage);
    }

    public void disconnect()
    {
        if (socketOpen) {
            webSocket.close(1000, null);
        }
        shouldReconnect = false;
    }

    private final WebSocketListener webSocketListener = new WebSocketListener() {
        @Override
        public void onOpen(WebSocket webSocket, Response response)
        {
            log.info("Opened Twitch websocket...");
            socketOpen = true;
        }

        @Override
        public void onMessage(WebSocket webSocket, String rawMessage) {
            try {
                plugin.logSupport("Received new Twitch websocket message: "+ rawMessage);

                JsonObject message = (new JsonParser()).parse(rawMessage).getAsJsonObject();
                JsonObject metadata = message.getAsJsonObject("metadata");
                JsonObject payload = message.getAsJsonObject("payload");
                String messageType = metadata.get("message_type").getAsString();

                plugin.logSupport("Handling Twitch websocket message with type: "+ messageType);
                switch (messageType) {

                    // acknowledge that this client works and store the session ID for future subscriptions
                    case "session_welcome" -> {
                        JsonObject session = payload.getAsJsonObject("session");
                        sessionId = session.get("id").getAsString();
                        keepAliveTimeoutS = session.get("keepalive_timeout_seconds").getAsInt();

                        // the socket is ready when a session ID has been received
                        socketListener.onReady();
                    }

                    // keepalive message from the server to show it is still a valid connection
                    case "session_keepalive" -> {
                        lastKeepAliveAt = Instant.now();
                    }

                    // force the session to reconnect to a new URL
                    case "session_reconnect" -> {
                        JsonObject session = payload.getAsJsonObject("session");
                        String reconnectUrl = session.get("reconnect_url").getAsString();

                        // override to the new URL and reconnect
                        websocketUrl = reconnectUrl;
                        reconnect();
                    }

                    // message for an event we've been subscribed to
                    case "notification" -> {
                        String subscriptionType = metadata.get("subscription_type").getAsString();
                        JsonObject subscriptionPayload = payload.getAsJsonObject("event");
                        socketListener.onMessage(subscriptionType, subscriptionPayload);
                    }
                }
            } catch (Exception exception) {
                log.warn("Could not handle websocket message, skipping it: " + rawMessage);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            log.warn("Twitch websocket is closing due to: {}", reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            log.warn("Twitch websocket was closed due to: {}", reason);
            socketOpen = false;
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            log.warn("Twitch websocket failed with error: {}", t.getMessage());
        }
    };

    private void createSubscriptions() {
        createSubscription("channel.channel_points_custom_reward_redemption.add", 1);
        // createSubscription("channel.subscribe", 1);
    }

    private void createSubscription(String type, int version)
    {
        final String channelId = twitchApi.getChannelId();
        final String token = config.twitchOAuthAccessToken();

        // guard: check if the auth parameters are valid
        if (channelId == null || token == null) {
            return;
        }

        final JsonObject condition = new JsonObject();
        condition.addProperty("broadcaster_user_id", channelId);
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
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int responseCode = response.code();

                if (responseCode == 202) {
                    plugin.logSupport("Successfully created Twitch websocket subscription for type: "+ type);
                } else {
                    log.warn("Could not create Twitch websocket subscription due to error code: "+ responseCode);
                    log.warn("And response body: "+ response.body().string());
                }

                // always close the response to be sure there are no memory leaks
                response.close();
            }
        });
    }

    private OkHttpClient createHttpClient(int timeoutMs)
    {
        return httpClientTemplate
            .newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build();
    }
}
