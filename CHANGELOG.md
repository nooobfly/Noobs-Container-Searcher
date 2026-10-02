# Changelog

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
