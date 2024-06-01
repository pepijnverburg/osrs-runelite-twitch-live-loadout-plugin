package com.twitchliveloadout.twitch.eventsub.messages;

import java.util.ArrayList;

public class BaseHypeTrain extends BaseBroadcasterInfo {
    public Integer level;
    public Integer total;
    public ArrayList<HypeTrainContribution> top_contributions;
    public String started_at;
}
