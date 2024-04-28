package com.twitchliveloadout.twitch.eventsub;

import com.twitchliveloadout.twitch.eventsub.messages.MessageData;

public class Message<T extends MessageData> {
    public String type;
    public T data;

    public Message(String type)
    {
        this.type = type;
    }

    public Message(String type, T data)
    {
        this.type = type;
        this.data = data;
    }
}