
## Changelog

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
