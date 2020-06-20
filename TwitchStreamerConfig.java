/*
 * Copyright (c) 2020, Pepijn Verburg <pepijn.verburg@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.twitchstreamer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("twitchstreamer")
public interface TwitchStreamerConfig extends Config
{

	public final int MAX_BANK_ITEMS = 200;
	public final String DEFAULT_EXTENSION_CLIENT_ID = "cuhr4y87yiqd92qebs1mlrj3z5xfp6";

	@ConfigItem(
			keyName = "twitchToken",
			name = "Twitch Extension Token",
			description = "Your token can be found in the Twitch Extension overlay 'Settings' tab when logged in as broadcaster.",
			secret = true,
			position = 0
	)
	default String twitchToken()
	{
		return "";
	}

	@ConfigItem(
			keyName = "playerInfoEnabled",
			name = "Sync display name",
			description = "Synchronize basic player info such as display name.",
			position = 2
	)
	default boolean playerInfoEnabled()
	{
		return true;
	}

	@ConfigItem(
		keyName = "inventoryEnabled",
		name = "Sync inventory items",
		description = "Synchronize all inventory items.",
		position = 4
	)
	default boolean inventoryEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "equipmentEnabled",
			name = "Sync equipment items",
			description = "Synchronize all equipment items.",
			position = 6
	)
	default boolean equipmentEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "bankEnabled",
			name = "Sync bank items",
			description = "Synchronize bank value and top "+ MAX_BANK_ITEMS +" items based on GE value.",
			position = 8
	)
	default boolean bankEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "skillsEnabled",
			name = "Sync skill levels",
			description = "Synchronize skill experience, level boosts and combat level.",
			position = 10
	)
	default boolean skillsEnabled()
	{
		return true;
	}

//	@ConfigItem(
//		keyName = "combatEnabled",
//		name = "Sync combat statistics",
//		description = "Synchronize statistics about PvM and PvP, such as DPS, freezes, etc.",
//		position = 12
//	)
//	default boolean combatEnabled()
//	{
//		return true;
//	}
//
//	@ConfigItem(
//			keyName = "goalsEnabled",
//			name = "Sync item goals",
//			description = "Synchronize the configured item wanted items, progress is determined from inventory, gear or bank items.",
//			position = 14
//	)
//	default boolean goalsEnabled()
//	{
//		return true;
//	}

	@ConfigItem(
			keyName = "weightEnabled",
			name = "Sync weight of carried items",
			description = "Synchronize the weight of the equipment and inventory items, including weight reduction.",
			position = 16
	)
	default boolean weightEnabled()
	{
		return true;
	}

	@ConfigItem(
			keyName = "overlayTopPosition",
			name = "Overlay top position",
			description = "The position of the overlay in % of the viewport height. Zero will enable extension default.",
			position = 92
	)
	default int overlayTopPosition()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "syncDelay",
			name = "Sync delay",
			description = "The amount of seconds to delay the sending of data to match your stream delay.",
			position = 94
	)
	default int syncDelay()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "syncDisabled",
			name = "Disable and clear syncing",
			description = "Temporarily disable all syncing, hide extension and clear data.",
			position = 96
	)
	default boolean syncDisabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "extensionClientId",
		name = "Twitch Extension ID",
		description = "This is the ID of the Twitch Extension you want to sync the data to. Also known as 'CLient ID'.",
		secret = true,
		position = 100,
		hidden = true
	)
	default String extensionClientId()
	{
		// the default osrs_tools extension
		return DEFAULT_EXTENSION_CLIENT_ID;
	}

}
