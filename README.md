# OSRS Twitch Live Loadout Plugin ![Plugin Installs](https://img.shields.io/endpoint?url=https://i.pluginhub.info/shields/installs/plugin/twitch-live-loadout) ![Plugin Rank](https://img.shields.io/endpoint?url=https://i.pluginhub.info/shields/rank/plugin/twitch-live-loadout)
![OSRS Live Loadout](./docs/banner.png?raw=true "OSRS Live Loadout")

Join the Discord server for questions or feature requests:<br/>
[![Support Server](https://img.shields.io/discord/968224650163273798.svg?label=Discord&logo=Discord&colorB=7289da&style=for-the-badge)](https://discord.gg/3Fjm5HTFGM)

## Introduction

*"What is that helm you are wearing?!"*

Let your Twitch viewers be fully immersed by providing them with interactive and real-time information about `Worn Equipment`, `Collection Log`, `Combat Statistics`, `Skills`, `Inventory`, `Bank` and more!

Get better engagement with...
- **New players** who are not familiar with most of the content.
- **Existing casual players** who are not up-to-date about the latest changes.
- **Existing frequent players** who would like to give advice on the loadout.

[Twitch Extensions](https://www.twitch.tv/p/extensions/) allow this data to be displayed live to each individual player while giving them control what to see and what to interact with. The screenshots in this README are made using a Twitch Extension that is compatible with this plugin.

![Summary](./docs/summary.gif "Summary")

### Recent updates
- 🎉 Collection log is now available to be viewed live by your viewers!
- Enhanced stability of plugin when there are breaking client or Twitch changes.
- The Twitch data syncing has been updated to work again due to deprecated functionalities.

## Requirements
- [Runelite Client](https://runelite.net/) with [Plugin Hub](https://runelite.net/plugin-hub/) enabled
- [OSRS Live Loadout Twitch Extension](https://dashboard.twitch.tv/extensions/cuhr4y87yiqd92qebs1mlrj3z5xfp6)
- [Twitch Account](https://www.twitch.tv/) as broadcaster

#### Client Settings
Within the client the following should be enabled for all features to work:
- Enable fake XP Drops via the OSRS settings (used for `combat statistics`).

## 🚀 Getting Started

### Quick guide
- Step 1: Install [RuneLite Twitch Live Loadout Plugin](https://runelite.net/plugin-hub/Pepijn%20Verburg)
- Step 2: Install [Twitch OSRS Live Loadout Extension](https://dashboard.twitch.tv/extensions/cuhr4y87yiqd92qebs1mlrj3z5xfp6)
- Step 3: Copy token from the `Twitch Extension configuration page` and paste in the RuneLite plugin setting `Twitch Extension Token`.
- Step 4: Verify whether data is synced by previewing it in the `Twitch Extension configuration page`.

⚠️ Note that for the `collection log` and `bank` to show you need to `open them` at least once. The collection log will be saved across login sessions, the bank won't.

### Step 1: Install Runelite Plugin
You can install this plugin from the [Plugin Hub](https://runelite.net/plugin-hub/Pepijn%20Verburg).

The plugin hub can be found all the way to the bottom of the RuneLite plugins:
![Plugin Hub](./docs/getting-started/runelite-plugin-installation.gif "Plugin Hub")

### Step 2: Install Twitch Extension
The reviewed [OSRS Live Loadout Extension](https://dashboard.twitch.tv/extensions/cuhr4y87yiqd92qebs1mlrj3z5xfp6) is directly compatible and has a 1-minute setup!

First install the Twitch Extension via the `Creator Dashboard` and set the extension to `Overlay 1`:
![Twitch Extension](./docs/getting-started/twitch-extension-installation.gif "Twitch Extension")

### Step 3: Copy Twitch Extension token for authentication
Now open the `Configuration view` by clicking on the `settings/cog icon`. Here you can preview what the extension looks like. The `Settings tab` opens automatically where you can copy an Extension Token that you need to authenticate RuneLite to send data to the extension. Below you can see a GIF of how to copy that over:
![Configuration](./docs/configuration.gif "Configuration")

Note that the token is **valid for quite a long time**, meaning it is important you don't share the token with anyone. The RuneLite plugin panels show the validity of the token.

### Step 4: Test if everything works
Make sure you have something in your inventory or equipment to test with. Via the `Configuration view` of the extension you can also verify whether data is now synced, for example your gear:
![Twitch Extension](./docs/getting-started/twitch-extension-testing.gif "Twitch Extension")

## Features
Below you can find an overview which features are implemented. Note that the screenshots shown are from the [OSRS Live Loadout Extension](https://dashboard.twitch.tv/extensions/cuhr4y87yiqd92qebs1mlrj3z5xfp6).

Most data is automatically synced when logged in. The `Bank` and `Collection Log` are exceptions. See the ⚠️ marks below for an explanation.

![Icon](./docs/icons/combat.png "Icon") ![Icon](./docs/icons/items.png "Icon") ![Icon](./docs/icons/skills.png "Icon")

### 🎒 Items
- [x] `Inventory items`: live view of the inventory and total price.
- [x] `Equipment items`: live view of worn gear and total price.
- [x] `Collection log`: the collection log including all items and kill counts.
- [x] `Bank items`: Top 200 most valuable bank items and price of all bank items (not all due to Twitch Extension limitations).
- [x] `Bank tabs`: info what items are in what tab.
- [x] `Wiki lookup`: all items can be looked up by opening the official Wiki.

![Equipment](./docs/screenshots/worn-equipment.png "Equipment")

#### Bank
⚠️ The bank needs to `opened at least once after logging in` for the data to be retrieved by the plugin.
![Bank](./docs/screenshots/bank.png "Bank")

#### Collection Log
⚠️ Please note that you need to `open the collection log on each page` (e.g. each boss, each minigame, etc.) with the RuneLite plugin enabled to retrieve all the data so it can be synced with Twitch. This data is then `stored in your RuneLite profile` to be used in future sessions, so you don't have to do this every time you login 😄. When you want to update the KC or items, you need to `re-open the collection log` on the page of the boss / drop in question.

![Collection Log](./docs/screenshots/collection-log.png "Collection Log")

### 🥊 Combat
- [x] `Fight tracking`: general combat stats for multiple fights for both *PvM* & *PvP*.
- [x] `Kill tracking`: how many kills and separate stats for *all kills* and the *last kill*.
- [x] `DPS in general`: damage per seconds for all damage done to enemies.
- [x] `Poison damage`: poison and venom damage.
- [x] `Smite damage`: damage per fight smite has done including (hypothetical) prayer drain.
- [x] `Spell splashes`: count the splashes done on enemies (single combat only).
- [x] `Freezes`: count potential freezes and misses (single combat only).
- [x] `Entangle`: count snare/bind/entangle spells and misses (single combat only).
- [x] `Blood spell heals`: count potential heals through blood spells (single combat only).
- [x] `Incoming hits`: damage, splashes, etc. are also tracked on the logged in player.
- [x] `Wiki / Highscore lookup`: enemies can be looked up on the Wiki for NPC's and Highscores for players.

![Combat Stats](./docs/screenshots/combat-stats-all-compact.png "Combat Stats")
![Combat Stats](./docs/screenshots/combat-stats-list.png "Combat Stats")
![Combat Stats](./docs/screenshots/combat-tracking-spells.png "Combat Stats")
![Combat Stats](./docs/screenshots/combat-stats-self-close-up.png "Combat Stats")

### 📊 Skills
- [x] `Skill experiences`: all experience amounts per skill.
- [x] `Skill levels`: current skills levels based on boosts.

![Skills](./docs/screenshots/skills.png "Skills")

### 📝 General
- [x] `Player weight`: weight of worn and carried items including weight reducing items.
- [x] `Display name`: the name of the player in the chat bar.

### Twitch Extension specific
There are also some features available for the viewer specifically available in the [OSRS Live Loadout Extension](https://dashboard.twitch.tv/extensions/cuhr4y87yiqd92qebs1mlrj3z5xfp6).
![Dragging](./docs/screenshots/dragging.png "Dragging")
![Controls](./docs/screenshots/viewer-controls.png "Controls")

## Settings & Panels

![Plugin Panel](./docs/controls.png "Plugin Panel")

### ![Panel Icon](./docs/icons/panel_icon.png "Panel Icon") Panels

#### Twitch Connectivity
The plugin also lets you see the current status of the connection with Twitch.

#### Combat Fight Management
It is also possible to reset specific or all fights to let your statistics start from scratch.

### ⚙️ Settings
It is also possible to configure what information is being sent through the following options available in the plugin settings:

#### Twitch Extension
- `Sync enabled`: toggle off to disable all syncing and clear current data with all viewers.
- `Twitch extension token`: the login token specifically for the Twitch Extension you want to send the data to. This authenticates RuneLite to change data in the extension. This token should be retrieved when configuring the extension in the online Twitch interface.
- `Overlay top position`: tweak where a Twitch Extension overlay would be positioned to match your screen layout.
- `Sync delay`: delay the synchronization with x amount of seconds to match the broadcaster video & audio delay. Also use this to tweak when the video is delayed due to general networking.
- `Anti multi-log display name`: when multi-logging set the display name of the account you want to sync. When multiple Runelite apps are open at the same time it uses the same settings to send data to Twitch. This is to filter out only the account you want to synchronize.

#### Items
- `Sync inventory items`: toggle to sync inventory items.
- `Sync equipment items`: toggle to sync equipment items.
- `Sync bank items`: toggle to sync bank items.
- `Max bank items`: the maximum amount of bank items with an upper limit set by the plugin.
- `Sync bank value`: toggle to sync total bank value.
- `Sync collection log`: toggle to sync collection log.
- `Collection log filter`: Only include entry titles that include one of the keywords separated with a comma. For example `raids, abyssal, clue` will include the whole Raids tab (with CoX and ToB), the Abyssal Sire and all clue related categories.

#### Combat
- `Sync combat statistics`: toggle to sync combat statistics.
- `Track magic spells`: enable tracking of freezes, entangles, blood spells and splashes.
- `Track damage by others`: enable tracking of hitsplats of other players.
- `Track un-attacked enemies`: enable tracking of hitsplats on enemies you have not attacked, recommended in team settings such as Cox and ToB. Note that the enemy type should be attacked at least once. So for example: once you have attacked a goblin it will be in your combat stats. If then other players attack a different goblin you haven't attacked yet it will add to the damage by others stats.
- `Max combat fights`: the maximum amount of fights tracked with an upper limit set by the plugin.
- `Fight expiry time`: reset a fight after the configured minutes of inactivity.
- `Auto idling of fight timer`: stop fight timer when logged out or enemy is not visible.

#### Skills
- `Sync skill levels`: toggle to sync (boosted) skills.
- `Virtual levels enabled`: when enabled the maximum level shown based on experience is 126 instead of 99.

#### General info
- `Sync display name`: toggle to show basic player info.
- `Sync weight of carried items`: toggle to sync weight.

#### Advanced
- `Twitch Extension ID`: the unique identifier of the Twitch Extension where the data should be sent to. This is pre-filled with an extension known to work well with this plugin.

## 🔒 Security & Technical Details

### Data Flow

#### Twitch as the only third-party
**All data is send directly to Twitch** to make sure no other third-parties receive any information. This is using the [Twitch Configuration Service](https://dev.twitch.tv/docs/tutorials/extension-101-tutorial-series/config-service) to store a persistent state of the above data. This persistent state is used to load the extension with the latest data when a new viewer opens the stream. When a change happens due to in-game activity an update message is sent to the [Twitch PubSub Service](https://dev.twitch.tv/docs/extensions/reference/#send-extension-pubsub-message). This message is used to update the extension for the current viewers. General documentation about Twitch Extensions can be found [here](https://dev.twitch.tv/docs/extensions/reference/
).

#### Twitch Extension Token
It is worth noting that the token retrieved from Twitch to authenticate this plugin can **only access features related to the extension**. Twitch did a good job in preventing extensions and their tokens to have access outside of the extension.

#### Diagram
![Data Flow](https://mermaid.ink/svg/eyJjb2RlIjoic3RhdGVEaWFncmFtXG4gICAgUnVuZUxpdGUgLS0-IFR3aXRjaEFwaVxuICAgIFR3aXRjaEFwaSAtLT4gQ29uZmlndXJhdGlvblNlcnZpY2VcbiAgICBUd2l0Y2hBcGkgLS0-IFB1YlN1YlNlcnZpY2VcbiAgICBDb25maWd1cmF0aW9uU2VydmljZSAtLT4gVHdpdGNoVmlld2VyXG4gICAgUHViU3ViU2VydmljZSAtLT4gVHdpdGNoVmlld2VyIiwibWVybWFpZCI6eyJ0aGVtZSI6Im5ldXRyYWwifSwidXBkYXRlRWRpdG9yIjpmYWxzZX0)

### Technical Limitations
To simplify the state management one large state object is being send to Twitch. However, the Twitch Configuration and PubSub Service only allow messages of a *maximum size of 5KB*. This is also the reason why for the bank items only the top 200 most valuable items are synchronized. All messages are compressed using a GZIP compression algorithm to maximize the use of available bytes.

### State update frequency
The state updates are dependant on the maximum amount allowed by Twitch. Rate limit documentation can be found [here](https://dev.twitch.tv/docs/api/guide/#rate-limits). Currently the maximum update frequency is is once per two seconds. This means the updates are never truly 'real-time'.

### Oldschool weekly updates
The plugin is implemented with the OSRS weekly updates in mind. There are few dependencies on specific content meaning that almost all updates are directly reflected in the plugin as well. For example external item caches are used to make sure item icons and names are directly up to date. This allows for lower maintenance of this plugin and less down-time or faulty behaviour.

This also works for the collection log. A thing to note here is that if you want any new collection log items shown you need to open the collection log interface at that boss / category at least once.

## 👥 Acknowledgements
Shout-out to the following people helping out a lot giving feedback on several release versions of both the RuneLite Plugin and Twitch Extension:
- Gamma1991
- dkvl
- Adam
- deathbeam
- Alexsuperfly
- melky
- Hannah IM

## 🔮 Future
Future features that might be added based on feedback are:

### 🎯 Goals
- [ ] `Item goals`: overview of the items that one has a goal now. The completion will automatically update when an item has been obtained.
- [ ] `Item goals with KC`: for each item goal a boss killcount can be attached that is visible on hover.

### Others
- [ ] More in-depth statistics of PvP & PvM fights (e.g. `DPS`, `pray flicks`, etc.).
- [ ] Interface style choice (e.g. `old` / `2007` / `2012+` menu items).
- [ ] If the full bank is important, complex state management is considered where the bank is incrementally sent to Twitch in payloads of 5KB in size.
- [ ] Check whether different locations of viewers on the world influence the delay between video an updates so significantly that we would need to have a time reference to know when to update for each viewer independently.
- [ ] Add clan members to the fight stats to display the damage they are taking. They can then also be part of the 'incoming' fights group.
- [ ] Add live gear of PvP enemies deduced from graphical models.
- [ ] Sync data from loot tracker. Possible to have directly via API instead of via Twitch data packets?
- [ ] Keep track of the last +/- 5 combat hits and show that in the combat stats tab (e.g. nice for KO hits).
- [ ] Show list of current people in ToB / CoX party.

## 💡 Feedback
If you have any questions or suggestions please contact `support@osrs-tools.com` or open an issue here at Github.
