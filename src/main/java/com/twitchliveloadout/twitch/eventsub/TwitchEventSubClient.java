package com.twitchliveloadout.twitch.eventsub;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.twitchliveloadout.TwitchLiveLoadoutConfig;
import com.twitchliveloadout.twitch.eventsub.messages.*;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TwitchEventSubClient {
    private WebSocket webSocket;
    private final Gson gson;
    private final TwitchLiveLoadoutConfig config;
    private final OkHttpClient httpClientTemplate;

    private boolean shouldReconnect = true;
    private boolean socketOpen = false;

    private static final int PING_TIMER = 180_000; // ms (3 minutes)
    private static final int PING_TIMEOUT = 10_000; // ms

    private long lastPong;
    private boolean pingSent;

    private final SocketListener socketListener;

    public TwitchEventSubClient(Gson gson, TwitchLiveLoadoutConfig config, OkHttpClient httpClientTemplate)
    {
        this.gson = gson;
        this.config = config;
        this.httpClientTemplate = httpClientTemplate;

        socketListener = new SocketListener()
        {
            @Override
            public void onReady() {
                try {
                    sendMessage(new Message<>("LISTEN", new ListenOnTopic("channel-points-channel-v1.")));
                } catch (IOException ex) {
                    log.error("Unable to listen to subject. Disconnecting", ex);
                    disconnect();
                }
            }

            @Override
            public void onMessage(String type, JsonObject dataObject) {

                log.info("Message: {}: {}", type, dataObject);

                if (type.equals("reward-redeemed")) {
                    // HANDLE
                }
            }
        };
    }

    public boolean isConnected()
    {
        return socketOpen;
    }

    public boolean awaitingPing() {
        return pingSent;
    }

    private synchronized void initWebSocket()
    {
        String socketUrl = "wss://eventsub.wss.twitch.tv/ws";
        log.debug("socketCheck: initWebsocket() socketUrl = {}", socketUrl);

        Request request = new Request.Builder().url(socketUrl).build();
        OkHttpClient client = createHttpClient(10_000);
        webSocket = client.newWebSocket(request, webSocketListener);
        client.dispatcher().executorService().shutdown();
    }

    public void connect()
    {
        log.debug("socketCheck: connect()");
        shouldReconnect = true;
        initWebSocket();
    }

    public synchronized void reconnect()
    {
        log.debug("socketCheck: reconnect()");
        initWebSocket();
    }

    public void pingCheck()
    {
        if (!isConnected()) {
            return;
        }

        if (pingSent && System.currentTimeMillis() - lastPong >= PING_TIMEOUT) {
            log.debug("Ping timeout, disconnecting");
            disconnect();
            return;
        }

        if (!pingSent && System.currentTimeMillis() - lastPong >= PING_TIMER) {
            try {
                sendMessage(new Message<>("PING"));
                pingSent = true;
            } catch (IOException ex) {
                log.debug("Ping failure, disconnecting", ex);
                disconnect();
            }
        }
    }

    public <T extends MessageData> void sendMessage(Message<T> message) throws IOException, RuntimeException
    {

        if (!socketOpen) {
            return;
        }

        var type = new TypeToken<Message<T>>() {}.getType();
        if (message.data instanceof INeedAuth) {
            ((INeedAuth) message.data).setAuthToken(config.twitchOAuthAccessToken());
        }

        var jsonMessage = gson.toJson(message, type);

        log.debug("socketCheck: sendMessage({})", jsonMessage);
        webSocket.send(jsonMessage);
    }

    public void disconnect()
    {
        if (socketOpen) {
            webSocket.close(1000, "Do not need connection anymore");
        }
        shouldReconnect = false;
    }

    private final WebSocketListener webSocketListener = new WebSocketListener() {
        @Override
        public void onOpen(WebSocket webSocket, Response response)
        {
            socketOpen = true;
            log.debug("socketCheck: onOpen()");
            pingCheck();

            socketListener.onReady();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            JsonObject result = (new JsonParser()).parse(text).getAsJsonObject();
            String messageType = result.get("type").getAsString();

            log.debug("socketCheck: onMessage({})", text);

            switch (messageType) {
                case "PONG" -> {
                    lastPong = System.currentTimeMillis();
                    pingSent = false;
                }
                case "RECONNECT" -> reconnect();
                case "AUTH_REVOKED" -> disconnect();
                case "RESPONSE" -> {
                    String responseMessage = result.get("error").getAsString();
                    if (responseMessage.equals("ERR_BADAUTH")) {
                        disconnect();
                    }
                }
                default -> socketListener.onMessage(messageType, result.get("data").getAsJsonObject());
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            log.debug("socketCheck: onClosing()");
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            log.debug("socketCheck: onClosed()");
            socketOpen = false;
            if (shouldReconnect) {
                reconnect();
            }
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            log.debug("socketCheck: onFailure()");
            if (shouldReconnect) {
                reconnect();
            }
        }
    };

    public OkHttpClient createHttpClient(int timeoutMs)
    {
        return httpClientTemplate
                .newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }
}
