package com.twitchliveloadout;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TwitchLiveLoadoutPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TwitchLiveLoadoutPlugin.class);
		RuneLite.main(args);
	}
}