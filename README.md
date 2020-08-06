# Twitch Live Loadout

*"What is that helm you are wearing?!"*

The aim of this plugin is to let Twitch viewers be more immersed by providing them with interactive and real-time information about `Equipment`, `Skills`, `Inventory`, `Bank` and more.

Several use-cases on what the impact on viewer engagement is:
- **New players** that don't know about the multitude of items being used. Asking about everything is not something someone would do and this provides them with a low-threshold way of still getting the information.
- **Existing casual players** that are not up to date of the latest items that were recently released. Answering the same questions about these items over and over can be repetitive for existing viewers.
- **Existing frequent players** that would like to give advice on the current loadout by knowing exactly what you are taking with you. Most of the items are directly visible on the player, but some harder to see (e.g. rings / bracelets). This also allows viewers to take some time to inspect the loadout without being dependant on what menu tab the streamer has open.

[Twitch Extensions](https://www.twitch.tv/p/extensions/) are used to allow this additional viewer engagement.

Other use-cases include 

## Requirements
- [Runelite Client](https://runelite.net/) with Plugin Hub enabled
- [Twitch Account](https://www.twitch.tv/) as broadcaster
- [OSRS Live Loadout Twitch Extension]()

## Features

### Live Loadout
Currently the following information is synchronized to Twitch:
- Inventory items
- Equipment items
- Top 200 most valuable bank items
- Bank tabs
- Inventory items total price
- Equipment items total price
- Bank items total price
- Skill experiences
- Skill levels based on boosts
- Player weight
- Display name

### Customization
It is also possible to configure what information is being sent through the following options:
- *Twitch extension token*: the login token specifically for the Twitch Extension you want to send the data to. This authenticates RuneLite to change data in the extension. This token should be retrieved when configuring the extension in the online Twitch interface.
- *Sync delay*: delay the synchronization with x amount of seconds to match the broadcaster video & audio delay.
- *Sync display name*: toggle to show basic player info.
- *Sync inventory*: toggle to sync inventory items.
- *Sync bank*: toggle to sync bank items.
- *Sync skills*: toggle to sync (boosted) skills.
- *Sync weight*: toggle to sync weight.
- *Overlay top position*: tweak where a Twitch Extension overlay would be positioned to match your screen layout.
- *Sync disabled*: toggle to disable all syncing.
- *Extension Client ID*: the unique identifier of the Twitch Extension where the data should be sent to. This is pre-filled with an extension known to work well with this plugin.

## Getting Started

### Runelite Plugin
You can install this plugin from the [Plugin Hub](https://runelite.net/plugin-hub/).

### Twitch Extension
This plugin is implemented in such a way any Twitch Extension can use the synchronized information. A list below is available to show what Twitch Extensions can be used with this plugin:

#### 1. OSRS Live Loadout
The [OSRS Live Loadout plugin]() is directly compatible. You should add this extension to your Twitch account first to get the proper Twitch token to authenticate the Runelite plugin.

## Security & Technical Details

### Twitch Extension Token
It is worth noting that the token retrieved from Twitch to authenticate this plugin can **only access features related to the extension**. Twitch did a good job in preventing extensions and their tokens to have access outside of the extension.

### Data Flow
**All information is only send directly to Twitch** to make sure no other third-parties receive any data. This is using the [Twitch Configuration Service](https://dev.twitch.tv/docs/tutorials/extension-101-tutorial-series/config-service) to store a persistent state of the above data. This persistent state is used to show the extension with the latest data when a new viewer opens the stream. When a change happens an update message is sent to the [Twitch PubSub Service](https://dev.twitch.tv/docs/extensions/reference/#send-extension-pubsub-message). This message is used to update the extension for the current viewers.

### Technical Limitations
To simplify the state management one large state object is being send to Twitch. However, the Twitch Configuration and PubSub Service only allow messages of a *maximum size of 5KB*. This is also the reason why for the bank items only the top 200 most valuable items are synchronized. All messages are compressed using a GZIP compression algorithm to maximize the use of available bytes.

### Dealing with updates
The plugin is implemented with the OSRS weekly updates in mind. There are no dependencies on specific content meaning that all updates are directly reflected in the plugin as well. This allows for lower maintenance of this plugin and less down-time.

## Future
Future features that might be added based on feedback are:
- Let players 'vote' on wearing/dropping certain items by clicking on the items in question.
- List of current goal items with automatic progress based on items in bank/inventory/equipment.
- Statistics of PvP & PvM fights (e.g. `DPS`, `pray flicks`, etc.).
- Interface style choice (e.g. `old` / `2007` / `2012+` menu items).
- If the full bank is important, complex state management is considered where the bank is incrementally sent to Twitch in payloads of 5KB in size.

## Feedback
If you have any questions or suggestions please contact `support@osrs-tools.com` or open an issue here at Github.