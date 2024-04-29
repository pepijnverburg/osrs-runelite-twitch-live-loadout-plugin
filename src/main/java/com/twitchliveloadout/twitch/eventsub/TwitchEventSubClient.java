package com.twitchliveloadout.twitch.eventsub;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.TwitchLiveLoadoutPlugin;
import com.twitchliveloadout.twitch.TwitchApi;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TwitchEventSubClient {
   private final static String DEFAULT_TWITCH_WEBSOCKET_URL = "wss://eventsub.wss.twitch.tv/ws";
//     private final static String DEFAULT_TWITCH_WEBSOCKET_URL = "ws://127.0.0.1:8080/ws";

    private final TwitchLiveLoadoutPlugin plugin;
    private final TwitchLiveLoadoutConfig config;
    private final TwitchApi twitchApi;
    private final Gson gson;
    private final OkHttpClient httpClientTemplate;
    private WebSocket webSocket;

    private String websocketUrl = DEFAULT_TWITCH_WEBSOCKET_URL;
    private String sessionId = "";
    private int keepAliveTimeoutS = 10;
    private Instant lastKeepAliveAt = Instant.now();
    private boolean socketOpen = false;

    private final TwitchEventSubListener listener;

    public TwitchEventSubClient(TwitchLiveLoadoutPlugin plugin, TwitchLiveLoadoutConfig config, TwitchApi twitchApi, Gson gson, OkHttpClient httpClientTemplate, TwitchEventSubListener listener)
    {
        this.plugin = plugin;
        this.config = config;
        this.twitchApi = twitchApi;
        this.gson = gson;
        this.httpClientTemplate = httpClientTemplate;
        this.listener = listener;

        // instantly attempt to connect
        connect();
    }

    private synchronized void connect()
    {
        plugin.logSupport("Initialising the websocket for url: "+ websocketUrl);

        // guard: disconnect when one already exists
        if (webSocket != null) {
            disconnect();
        }

        OkHttpClient client = createHttpClient(10_000);
        Request request = new Request.Builder().url(websocketUrl).build();
        webSocket = client.newWebSocket(request, webSocketListener);
    }

    public synchronized void reconnect()
    {
        connect();
    }

    public void disconnect()
    {
        if (socketOpen) {
            webSocket.close(1000, null);
        }
    }

    private final WebSocketListener webSocketListener = new WebSocketListener() {
        @Override
        public void onOpen(WebSocket webSocket, Response response)
        {
            log.info("Successfully opened Twitch websocket...");
            listener.clearActiveSubscriptionTypes();
            socketOpen = true;
        }

        @Override
        public void onMessage(WebSocket webSocket, String rawMessage) {
            try {
                plugin.logSupport("Received new Twitch websocket message: "+ rawMessage);

                JsonObject message = (new JsonParser()).parse(rawMessage).getAsJsonObject();
                JsonObject metadata = message.getAsJsonObject("metadata");
                JsonObject payload = message.getAsJsonObject("payload");
                String messageId = metadata.get("message_id").getAsString();
                String messageType = metadata.get("message_type").getAsString();

                plugin.logSupport("Handling Twitch websocket message with type: "+ messageType);
                switch (messageType) {

                    // acknowledge that this client works and store the session ID for future subscriptions
                    case "session_welcome" -> {
                        JsonObject session = payload.getAsJsonObject("session");
                        sessionId = session.get("id").getAsString();
                        keepAliveTimeoutS = session.get("keepalive_timeout_seconds").getAsInt();

                        // the socket is ready when a session ID has been received
                        listener.onReady(sessionId);
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

                    // message when a subscription was revoked for whatever reason
                    case "revocation" -> {
                        String rawSubscriptionType = metadata.get("subscription_type").getAsString();
                        TwitchEventSubType subscriptionType = TwitchEventSubType.getByType(rawSubscriptionType);
                        log.info("A subscription was revoked from the Twitch websocket: "+ rawSubscriptionType);
                        listener.revokeActiveSubscriptionType(subscriptionType);
                    }

                    // message for an event we've been subscribed to
                    case "notification" -> {
                        String rawEventType = metadata.get("subscription_type").getAsString();
                        TwitchEventSubType eventType = TwitchEventSubType.getByType(rawEventType);
                        JsonObject eventPayload = payload.getAsJsonObject("event");

                        if (eventType == null)
                        {
                            plugin.logSupport("Could not find known Twitch EventSub type in enum, skipping it: "+ rawEventType);
                            return;
                        }

                        listener.onEvent(messageId, eventType, eventPayload);
                    }
                }
            } catch (Exception exception) {
                log.warn("Could not handle Twitch websocket message due to error: ", exception);
                log.warn("The websocket message that was skipped is: {}", rawMessage);
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            plugin.logSupport("Twitch websocket is closing due to: "+ reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            plugin.logSupport("Twitch websocket was closed due to: "+ reason);
            listener.clearActiveSubscriptionTypes();
            socketOpen = false;
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            plugin.logSupport("Twitch websocket failed with error: "+ t.getMessage());
        }
    };

    public boolean isConnected()
    {
        return webSocket != null && socketOpen && !sessionId.isEmpty() && Instant.now().minusSeconds(keepAliveTimeoutS).isBefore(lastKeepAliveAt);
    }

    public boolean isConnecting()
    {
        return webSocket != null && socketOpen && sessionId.isEmpty();
    }

    private OkHttpClient createHttpClient(int timeoutMs)
    {
        return httpClientTemplate
            .newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build();
    }
}
