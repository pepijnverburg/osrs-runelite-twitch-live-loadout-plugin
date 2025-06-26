## Changelog

#### v2.3.0
- feat: Added gameClient property to each payload sent to Twitch. This allows the extension to identify the game client and adjust behavior based on the client type.
- fix: Resolved two state management issues related to tracking login state and whether streamer products have been fetched. This ensures the login game event triggers properly.
- chore: Added debug messages for handling game events to aid troubleshooting.
- feat: Increased the preview duration for manual event testing, allowing longer test periods.
- fix: Fixed widget flickering when updating or altering properties due to incorrect timing between widget and marketplace product sync. Now using onPostClientTick event for WidgetManager synchronization.
- fix: Fixed issue where empty tokens would still trigger Twitch EventSub WebSocket creation.
- feat: Added DMM safe deposit box support for secure item handling.
- feat: Implemented game event listeners for graphic changes and chat messages.
- feat: Introduced support for additional game events (level up, raid completion, boss kill, pet drop) to trigger random events.
- chore: Updated version number to reflect latest changes.
- fix: Resolved projectile handling issues with random events due to the change in startZ parameter. Also added placeholder for future API changes.
- refactor: Updated how the shouldLoop property works following the deprecation of the shouldLoop function.
- fix: Fixed deprecated custom projectile API issue in WorldView instance.
- fix: Fixed issue where data payload to Twitch exceeded the 5KB limit, particularly with combat achievements. Syncing combat achievements now takes longer.
- fix: Resolved issues related to new game values and various deprecations.
- fix: Fixed issue where the previous player location wasn't known on login if the player hadn’t moved yet. Now, the system selects the first nearby tile as the ‘fake’ previous location, ensuring immediate product functionality.
- feat: Introduced support for resetting the expiry timer of a marketplace product when game events trigger. This ensures events can stack by resetting active product timers.
- feat: Added support for new game event triggers to activate marketplace products, including on login, specific menu entries, or game ticks.
- fix: Fixed issue preventing preview transactions from being fetched when no products were added to slots.
- fix: Resolved minor issue when checking for valid items in menu entries.
- chore: Rearranged settings for better logical structure.
- feat: Added new cyclic state for group storage handling with options to disable syncing of group storage items and/or price.
- feat: Added animation override for all animations, useful for NPC transmogs to prevent weird animation glitches.

#### v2.2.0
- feat: added reload configurations button to force reload the EBS products, configured channel points and configured streamer products from Twitch.
- feat: made a distinction between manual and test transactions to allow specific behavior (such as how the duration is determined).
- feat: added conditional rendering of the manual marketplace panel based on settings.
- feat: added loaded at timestamp to manual products to make it clear when you updated it.
- feat: added support for absolute tile positions for spawns and projectiles.
- feat: added proper color feedback when a custom EBS product is parsed.
- feat: added UI to trigger manual random events by inputting a JSON configuration.
- fix: fixed an issue where channel events would still be handled even though they are disabled in the RL settings. NOTE: in the future consider stopping the websocket as well upon setting change.
- fix: fixed issue where viewers could duplicate follow events by unfollowing and following the whole time. Currently, a singular Runelite session caches which viewers already did a follow event and dedupes the rest. NOTE: this is not cached when you close and restart RL.
- fix: fixed potential issue where a config change with same key names of a different plugin could trigger a config update.
- feat: added support for entity hiding random events.
- feat: added proper support for disabling clicks based on distance to the click, also added support for game objects and ground items!
- feat: added check when there are required spawns but somehow nothing spawned. The transaction is then rerun.
- feat: increased the time tolerance when receiving donations too late. It can now be delayed by 5 minutes at most.
- feat: added proper titles to the channel events in the panel to make it easier to identify which channel event triggered which random event.
- fix: increased the internal watchdog timeout that is tracked to see if the Twitch event sub connection is still live, because it might take a bit longer for a keep-alive to be received.
- fix: when no channel point rewards are found after a valid request, reset the channel points just to ensure it’s disabled with the viewers as well.
- fix: updated some API changes for the future RL release.
- feat: added proper support to handle free transactions, its cooldowns, its in-game messages, and the UI panels.
- fix: added backward compatibility for a new streamer product field.
- feat: added proper messages when free mode or chaos mode is active.
- feat: added support for FREE transactions and a check whether the client is allowing them to prevent malicious use.
- feat: added support for the extraction of the collection totals on the client-side to be more accurate.
- feat: added support to send over the 'free' and 'chaos' mode to the Twitch extension.
- feat: added button to reset all state for the account that is currently logged in.
- feat: added temporary test function to read the collection log entries from a CS2 script. This will be removed later, but is added to GIT to perhaps use in the future.
- fix: fixed long-standing issue of the wrong KCs being shown with the wrong bosses due to an internal client race condition that cannot really be checked for.
- feat: added support for sending Twitch Chat messages through Random Event activations for better viewer engagement. With this, some changes took place in the order of various config items.
- feat: added support for conditionals based on the gender of the player. This is to support different equipment models to be replaced depending on the character.
- feat: improved many things with the different channel event messages along with support in the marketplace panels to show the correct information for event sub-based transactions.
- fix: fixed a bug where we would not check how many panels are available.
- fix: fixed bug where the id field was defined multiple times causing issues when instantiating from JSON.
- feat: added support to customize the chat color notifications.
- feat: finished support for various notification messages for channel events. Refactored some settings to be placed in the new 'notifications' category to make it a bit better manageable in the side panel.
- feat: added support to update the visibility state based on the settings.
- feat: added support in the fight manager for some missing hitsplat types.
- feat: finished setting and enabling/disabling the default messages per event sub type.
- feat: added support for varying messages when a random event is activated based on a channel event. The messages are configurable via the RL settings.
- fix: fixed function signature of 'hasLineOfSightTo' that has been updated within RL.
- feat: added visibility entry for streamers to configure when the extension is visible on the screen.
- feat: expanded the RL config settings to support different types of messages for each channel event type.
- feat: added region ID state entry.
- feat: added Twitch product type to distinguish bits product, channel point products, or channel event products.
- feat: added menu entries to the EBS product.
- feat: added support for right-click menu entries on SpawnedObjects.
- feat: added support to check the region a player is in and then deactivate the random event.
- fix: fixed issue where websocket client would not reset the URL upon full connection reset.
- fix: disabled the channel events in the state when the tokens are not set up.
- feat: added chaos and free mode to the random events. Free mode is not possible from the Twitch side of things yet due to having some security challenges in how to properly check those transactions are valid.
- feat: added free origin.
- feat: added a prefix to the chat message of the default message with the name of the activated random event. This makes it a bit easier to see who triggered what when multiple things happen at once.
- fix: fixed blinding overlay in fixed mode. The interface IDs changed.
- fix: fixed some minor importing issues.
- feat: added initial support to check whether the menu item clicked was close enough to the conditions.
- feat: added initial support to have marketplace product conditions based on which combat style is being used.
- feat: added support for preview events to be triggered by the Twitch Extension. This has some security challenges, which is now mainly solved by giving the streamers a fixed window these 'free' events are available.
- feat: added fetching and syncing of the channel point rewards. Because we manage the OAuth token on the client, we need to send over the channel point rewards to the Twitch Extension via RuneLite. The extension token or default Helix token do not have permissions to get the channel point custom rewards, unfortunately. This is the trade-off with the client-first approach.
- feat: added proper typing for all channel event messages where they can be handled individually but have a shared basis upon which a transaction is added to the queue.
- feat: added support for different EventSub types to handle them in each their own way to populate the TwitchTransaction instance correctly. The channel point redeem is now fully working when such an event comes in via the EventSub websocket. The others should still be implemented.
- feat: added support for refreshing the oAuth tokens automatically based on the expiry time when validating the current tokens. With this moved some Twitch API calls to the TwitchApi class as well for consistency purposes.
- feat: fully refactored the pubsub client to be in line with the EventSub specifications (see: https://dev.twitch.tv/docs/eventsub/handling-websocket-events/). This also includes creating the proper subscriptions to get live events for.
- feat: added initial management of the EventSub client based on https://github.com/pepijnverburg/osrs-runelite-twitch-live-loadout-plugin/commit/ccc1b65ba1c09c8309fef96cbd6bc3f69877be7b
- fix: fixed potential bug where an invalid widget is requested.
- fix: fixed a bug where on initial configuration cache load the seasonal line items were not shown.
- feat: added new setting to skip collection log pages without any items collected.
- fix: slightly increased the frame rate of widget effects of Random Events, this is to decrease some UI flickering.
- feat: finished the first version of seasonal relics and areas syncing support. This probably changes on the next runs, but it is a good basis for future seasonals. This implementation is currently based on Leagues 4: Trailblazer Reloaded.
- feat: added popup notification constants.
- feat: added support for popup notifications along with new settings to choose what kind of donation message notifications you would like to receive.
- feat: started with seasonal game mode support in a dedicated tab to display for example relics/regions/specific points and more in the future. This tab is set up fairly dynamically where you simply have a group containing items with each a short title and an optional sprite. An additional change was made in how accounts are identified when fetching persistent Twitch data such as bank items and collection log. When changing world type this is considered an account switch, causing this cache to be reloaded for the appropriate account hash PLUS world type. Not all world types cause this change, but specific ones are whitelisted.

#### v2.0.0
- Feature: support for viewer activated Random Events which can be configured within Twitch. These are temporary visual effects and can be managed in the new 'donations' tab.
- Feature: support for syncing multiple accounts at once to Twitch meaning there is no need anymore to keep track of what RuneLite window is active. This opens up an opportunity in the future to have GIM / other types of teams send their loadout to each others stream. This is not implemented yet, but will be considered in the future.
- Feature: added support for mobile devices within the Twitch extension, some data syncing of the plugin is now optimized for this.
- Feature: the quest tab along with the progression of each quest can be sent over to Twitch.
- Refactor: a major part of how data is collected and synced to Twitch had a major overhaul where connection status is being synced when not logged in and the loadout when being logged in. This allows us to give proper feedback to the streamer whether everything is setup correctly. Also, data is not collected when certain settings are disabled.
- Fix: fixed an issue where ToA invocations could not be synced when not being the party leader.
- Fix: fixed an issue where the skill levels would not be synced when logged in while activating the plugin.

#### v1.1.0
- Tombs of Amascut Invocations can now be synced automatically when at the raid lobby and in the raid itself.
- You can now switch between dark and light theme for your viewers.
- All bank items are now synced to your viewers, rather than only the 200 most valuable items.
- The looting bag can now be synced to viewers.
- Items can now have 'special behaviours' for example the looting bag opens a new side-panel as one of the first items for this behaviour.
- Collection log can be filtered using keywords to chose what you want too sync.
- Added support and documentation links to the `Status` tab in the plugin tab.
- Added automatic detection which account should be synced when multi-logging. The plugin keeps track of which RuneLite window is focussed on for a minimum (configurable) time to determine which account to sync.
- Expanded the `Status` tab to be more transparent in which RuneLite window is currently synchronizing data.
- Enhanced the stability of the plugin by ignoring game events that are disabled by the streamer to sync anyways.

#### v1.0.3
- Patch to be compatible with the new RuneLite hitsplats API.

#### v1.0.2
- Collection log is now available to be viewed live by your viewers!
- Enhanced stability of plugin when there are breaking client or Twitch changes.
- The Twitch data syncing has been updated to work again due to deprecated functionalities.
