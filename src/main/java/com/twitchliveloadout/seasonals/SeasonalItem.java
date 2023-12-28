package com.twitchliveloadout.seasonals;

public class SeasonalItem {
    public Boolean active = true;
    public Boolean highlighted = false;
    public String title = "";
    public String spriteName;
    public Boolean spriteContain = true;
    public Integer progress;

    public SeasonalItem(String title)
    {
        this.title = title;
        this.highlighted = true;
    }

    public SeasonalItem(String title, String spriteName)
    {
        this.title = title;
        this.spriteName = spriteName;
    }

    public SeasonalItem(String title, int spriteId)
    {
        this.title = title;
        this.spriteName = spriteId +"-0.png";
    }
}
