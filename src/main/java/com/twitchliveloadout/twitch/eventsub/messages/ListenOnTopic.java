package com.twitchliveloadout.twitch.eventsub.messages;


public class ListenOnTopic extends WithAuth implements MessageData {
    public String[] topics;

    public ListenOnTopic(String topic) {
        topics = new String[]{topic};
    }
}
