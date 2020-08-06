# Twitch Live Loadout

*"What is that helm you are wearing?!"*

The aim of this plugin is to let Twitch viewers be more immersed by providing them with interactive and real-time information about `Equipment`, `Skills`, `Inventory`, `Bank` and more.

[Twitch Extensions](https://www.twitch.tv/p/extensions/) are used to allow this additional viewer engagement.

Several use-cases on what the impact on viewer engagement is:
- **New players** that don't know about the multitude of items being used. Asking about everything is not something someone would do and this provides them with a low-threshold way of still getting the information.
- **Existing casual players** that are not up to date of the latest items that were recently released. Answering the same questions about these items over and over can be repetitive for existing viewers.
- **Existing frequent players** that would like to give advice on the current loadout by knowing exactly what you are taking with you. Most of the items are directly visible on the player, but some harder to see (e.g. rings / bracelets). This also allows viewers to take some time to inspect the loadout without being dependant on what menu tab the streamer has open.
- In general **provide statistics about combat** that might be interesting for different types of viewers and something streamers can relate to after a fight.

## Requirements
- [Runelite Client](https://runelite.net/) with Plugin Hub enabled
- [Twitch Account](https://www.twitch.tv/) as broadcaster
- [OSRS Live Loadout Twitch Extension]()

## Features

### Live Loadout
Currently the following information is synchronized to Twitch:
- `Combat statistics`: DPS per attack type for both PvP & PvM, freeze counters and 
- `Inventory items`: live view of the inventory and total price.
- `Equipment items`: live view of worn gear and total price.
- `Bank items`: Top 200 most valuable bank items and price of all bank items.
- `Bank tabs`: info what items are in what tab.
- `Skill experiences`: all experience amounts per skill.
- `Skill levels`: current skills levels based on boosts.
- `Player weight`: weight of worn and carried items including weight reducing items.
- `Display name`: the name of the player in the chat bar.

### Customization
It is also possible to configure what information is being sent through the following options:
- `Twitch extension token`: the login token specifically for the Twitch Extension you want to send the data to. This authenticates RuneLite to change data in the extension. This token should be retrieved when configuring the extension in the online Twitch interface.
- `Sync delay`: delay the synchronization with x amount of seconds to match the broadcaster video & audio delay. Also use this to tweak when the video is delayed due to general networking.
- `Sync display name`: toggle to show basic player info.
- `Sync inventory`: toggle to sync inventory items.
- `Sync bank`: toggle to sync bank items.
- `Sync skills`: toggle to sync (boosted) skills.
- `Sync weight`: toggle to sync weight.
- `Overlay top position`: tweak where a Twitch Extension overlay would be positioned to match your screen layout.
- `Sync disabled`: toggle to disable all syncing.
- `Extension Client ID`: the unique identifier of the Twitch Extension where the data should be sent to. This is pre-filled with an extension known to work well with this plugin.

## Getting Started

### Runelite Plugin
You can install this plugin from the [Plugin Hub](https://runelite.net/plugin-hub/).

### Twitch Extension
This plugin is implemented in such a way any Twitch Extension can use the synchronized information. A list below is available to show what Twitch Extensions can be used with this plugin:

#### 1. OSRS Live Loadout
The [OSRS Live Loadout plugin]() is directly compatible. You should add this extension to your Twitch account first to get the proper Twitch token to authenticate the Runelite plugin.

## Security & Technical Details

### Data Flow

#### Third-parties
**All information is only send directly to Twitch** to make sure no other third-parties receive any data. This is using the [Twitch Configuration Service](https://dev.twitch.tv/docs/tutorials/extension-101-tutorial-series/config-service) to store a persistent state of the above data. This persistent state is used to show the extension with the latest data when a new viewer opens the stream. When a change happens an update message is sent to the [Twitch PubSub Service](https://dev.twitch.tv/docs/extensions/reference/#send-extension-pubsub-message). This message is used to update the extension for the current viewers.

#### Twitch Extension Token
It is worth noting that the token retrieved from Twitch to authenticate this plugin can **only access features related to the extension**. Twitch did a good job in preventing extensions and their tokens to have access outside of the extension.

#### Diagram
![Data Flow](https://mermaid.ink/svg/eyJjb2RlIjoic3RhdGVEaWFncmFtXG4gICAgUnVuZUxpdGUgLS0-IFR3aXRjaEFwaVxuICAgIFR3aXRjaEFwaSAtLT4gQ29uZmlndXJhdGlvblNlcnZpY2VcbiAgICBUd2l0Y2hBcGkgLS0-IFB1YlN1YlNlcnZpY2VcbiAgICBDb25maWd1cmF0aW9uU2VydmljZSAtLT4gVHdpdGNoVmlld2VyXG4gICAgUHViU3ViU2VydmljZSAtLT4gVHdpdGNoVmlld2VyIiwibWVybWFpZCI6eyJ0aGVtZSI6Im5ldXRyYWwifSwidXBkYXRlRWRpdG9yIjpmYWxzZX0)

### Technical Limitations
To simplify the state management one large state object is being send to Twitch. However, the Twitch Configuration and PubSub Service only allow messages of a *maximum size of 5KB*. This is also the reason why for the bank items only the top 200 most valuable items are synchronized. All messages are compressed using a GZIP compression algorithm to maximize the use of available bytes.

### State update frequency
The state updates are dependant on the maximum amount allowed by Twitch. Rate limit documentation can be found [here](https://dev.twitch.tv/docs/api/guide/#rate-limits). Currently the maximum update frequency is is once per two seconds. This means the updates are never truly 'real-time'.

### Dealing with updates
The plugin is implemented with the OSRS weekly updates in mind. There are no dependencies on specific content meaning that all updates are directly reflected in the plugin as well. This allows for lower maintenance of this plugin and less down-time.

## Future
Future features that might be added based on feedback are:
- Let viewers 'vote' on wearing/dropping certain items by clicking on the items in question.
- List of current goal items with automatic progress based on items in bank/inventory/equipment.
- More in-depth statistics of PvP & PvM fights (e.g. `DPS`, `pray flicks`, etc.).
- Interface style choice (e.g. `old` / `2007` / `2012+` menu items).
- If the full bank is important, complex state management is considered where the bank is incrementally sent to Twitch in payloads of 5KB in size.
- Check whether different locations on the world influence the delay between video an updates so significantly that we would need to have a time reference to know when to update for each viewer independently.

## Feedback
If you have any questions or suggestions please contact `support@osrs-tools.com` or open an issue here at Github.