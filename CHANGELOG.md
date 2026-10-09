# Changelog

## 1.12

### Added

- Added support for Minecraft 26.3, in the same single universal jar.
- Added an "Item display" toggle to the Settings tab to turn the floating item preview in front of containers on or off (on by default). The preview now works on every supported version instead of only 26.2.
- Added saving of the selected books in the Librarian Reroll tab. The selection survives closing the GUI or cancelling a reroll by accident, and when a wanted enchantment is obtained only that book is deselected, so the rest stay selected for the next villager.

### Fixed

- Fixed container highlight outlines drifting away from the container when moving the camera on 1.21.1, 1.21.4 and 1.21.8.
- Fixed the search GUI being covered by a blur on 1.21.1 and 1.21.4.
- Fixed "Search held item" stealing the S key (move backward) on 1.21.1 - 1.21.11 by changing its default key to R. If you already used the mod, rebind it in Controls.

## 1.11

### Added

- Added a redesigned search GUI: the sidebar is replaced by a tab bar (Search, Librarian Reroll, Settings) with a cleaner, rounded layout. The reroll page is now a tab in the same screen on every supported version.
- Added a Locate button that appears when hovering a result, plus a distance badge on every result and a durability percentage for damaged items.
- Added a sort button (by name or by distance) and clickable active-filter chips that can be removed one by one or all at once.
- Added quick filter chips, a clickable "Try:" row for search syntax (name:, lore:, durability:<50, enchanted), and distance preset chips next to the slider.
- Added a requirements checklist (lectern and axe in hotbar), a status box, and a selected books list to the Librarian Reroll tab. Enchantments are now shown as cards in a grid.
- Added a saved data summary (containers and items) to the Settings tab.

## 1.10

### Fixed

- Fixed the game freezing when opening the search GUI and when saving a newly opened container or villager. The container database is now written to disk on a background thread instead of blocking the game, and the file is written in a compact format so it is smaller and faster to save.
- Fixed the search GUI doing heavy repeated work on every open and every keystroke in the search box. Item and enchantment names are now resolved once and reused, so opening the GUI and typing a search is much smoother, especially with a large number of saved containers.

## 1.9

### Added

- Added support for 1.21.1, 1.21.4, 1.21.8, 1.21.10, 26.1, and 26.1.2, on top of the existing 1.21.11 and 26.2 support — all from the same single universal jar.

### Note

- The newly added versions (1.21.1, 1.21.4, 1.21.8, 1.21.10, 26.1, 26.1.2) are freshly added and less battle-tested than 1.21.11/26.2. If you run into anything odd on one of them, please report it.

## 1.8

### Changed

- Both 1.21.11 and 26.2 are now supported by a single universal jar. Download one file, and it works on either Minecraft version — no more picking the right jar or ending up with leftover old versions in your mods folder after an update.

### Fixed

- Fixed container highlight outlines not appearing in the world at all in some cases (the item glow inside an opened container still worked, but the box outline around the container itself silently failed to draw). Outlines are now also visible through walls, matching the intended "x-ray" highlight behavior.

## 1.7

### Added

- Added a "Delete All Data" button to the search GUI, to permanently clear all saved container data for the current server.

### Fixed

- Fixed some servers' virtual/plugin menus being wrongly attributed to a real nearby chest and saved as if they were its contents. The search GUI now also verifies the opened menu's title against the block's own container name before recording it.

## 1.4

### Added

- 26.2 support.
- Villager items support.

### Fixed

- Fixed container and villager records deleting themselves over time. Cleanup now waits 15 seconds after joining a world or changing dimension, only verifies records near the player (48 blocks for containers, 32 for villagers), and requires several consecutive confirmations before removing a record.
- Fixed containers behind item frames not being detected when opened through entity interaction.
- Fixed obstructed chests being ignored by container detection.
- Fixed silent data loss when the saved container data file was corrupted; it is now backed up instead of being overwritten.

### Changed

- Gui design.
- Reduced disk writes by batching villager position updates instead of saving every second.
- Added safeguards so an opened menu can no longer be attributed to the wrong container.

## 1.3

### Added

- Added a distance slider to the search GUI.
- Added a separate container type filter section.

### Changed

- Updated the search GUI background to a cleaner dark gray style.
- Made result rows more compact to reduce text overlap.
- Improved result text layout for long item names and item IDs.

## 1.2

- Container search GUI now merges duplicate stacks from the same container into one result row.
- Result item count now shows the total amount for that item in that container.
- Added distance info to GUI result rows.
- Fixed distant/unloaded container outlines rendering incorrectly.
- Updated the search GUI theme to a darker gray style from cyan style.
