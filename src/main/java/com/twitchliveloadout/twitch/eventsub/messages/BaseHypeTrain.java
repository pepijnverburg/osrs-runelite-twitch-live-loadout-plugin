package com.twitchliveloadout.twitch.eventsub.messages;

import java.util.ArrayList;

public class BaseHypeTrain extends BaseBroadcasterInfo {
    public String id;
    public int level;
    public int total;
    public int progress;
    public int goal;
    public ArrayList<HypeTrainContribution> top_contributions;
    public String started_at;
}
