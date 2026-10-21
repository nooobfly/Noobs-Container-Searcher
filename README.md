# Noob's Container Searcher

Remembers every chest, barrel, furnace and villager trade you open. Search them from anywhere, and see what changed since your last visit. Basically an item finder.

A client-side Fabric and NeoForge mod, nothing needs to be installed on the server. One jar works on Minecraft 1.21.1, 1.21.4, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.1.2, 26.2 and 26.3.

The mod saves container contents locally when you open a physical container such as a chest, double chest, barrel, furnace, shulker box, or other storage block. It does not scan unopened containers, does not reveal hidden inventories, and only searches through data that your client has already seen.

## How to Use

1. Open a physical container first, such as a chest, barrel, furnace, or shulker box.
2. The mod saves the container contents locally after your client sees them.
3. Press `G` to open the search GUI.
4. Search for an item by name, item id, lore, durability, or enchantment.
5. Click a result in the GUI to show the matching saved container coordinates, highlight the container in the world and highlight the matching item when you open it.

You can also search the item in your main hand with `Left Alt + R`.

All keybinds can be changed from Minecraft's Controls menu under `Noob's Container Searcher`.

If a container is gone, its record is deleted, but only when you're standing near it and after it has been checked several times.

## Commands

- `/containersearch <item>` searches nearby saved containers.
- `/containersearch <item> global` searches all saved containers in the current server/world.
- `/containersearch gui` or `/containersearch menu` opens the search GUI.
- `/containersearch clearhighlight` clears active container highlights.
- `/csr` is a short alias for `/containersearch`.

## Features

### Search
- Saves opened container contents on the client side, separately per server, world and dimension
- Saves villager trades with the villager's profession
- Search by item name, item id, lore, durability or enchantment, with a clickable "Try:" syntax row (`name:`, `lore:`, `durability:<50`, `enchanted`)
- Filter by enchantments, lore, durability, enchanted status and container type
- Quick filter chips, distance slider and distance presets, sort by name or distance
- Shows how many of the searched item are saved in each matching container, with a distance badge on every result
- Locate button on every result

### Highlights and guidance
- Highlights the matching container in the world, visible through walls
- Highlights matching items while the saved container screen is open
- Optional floating item preview in front of containers
- Direction arrows at the screen edge point to the containers you cannot see yet, with their distance (can be turned off in Settings)

### Changes since your last visit
- A panel next to the container GUI shows which items were added or removed since you last opened it
- Shows other players who opened the container while you were nearby
- Hints when a hopper, dropper or dispenser may have moved items, naming the container they went to or came from
- Tells you when half of a double chest was broken, and who did it if they were nearby
- Scrolls with the mouse wheel, can be turned off in Settings

### Locations
- Name an area (a base, a farm, a mine...) with a radius, a color and any item as its icon
- Search results get the location's color, icon and name, and you can filter by location
- "Color results by location" toggle in Settings

### Librarian Reroll
- A dedicated tab for rerolling librarian trades until the enchanted books you want show up, with a requirements checklist and saved selection

### Everything else
- Black theme, auto GUI scale so it stays readable at any window size
- One-click button to delete all saved container data for the current server

![Main Menu](https://cdn.modrinth.com/data/cached_images/ec31bd2ae7b960c6d66ab5a26cb88704cf325d3a.png)
![Changes since last container opening](https://cdn.modrinth.com/data/cached_images/ba2f546e6cdced6294658a4d799e176a99311749_0.webp)
![Locations Menu](https://cdn.modrinth.com/data/cached_images/9fae4643edd271fbc759f9c09a6133a69cee0527.png)
![Settings](https://cdn.modrinth.com/data/cached_images/f7ab6926296fc4ec6821fe14579911ce0a8066c0_0.webp)
![Item Display and Highlight in Front of Container](https://cdn.modrinth.com/data/cached_images/205464d9b60037b31b4156df6e370039f39a7423.png)
![Highlight Items in Chests](https://cdn.modrinth.com/data/cached_images/4b55e6b100dcbf3e1bd1fd290c4971e3baceddc2_0.webp)

Support: https://discord.gg/SSwGQZp4gQ

## Important Notes

This mod only works with containers you have manually opened before. It does not search the world for unopened containers and does not read server-side inventories that were never shown to your client.

Virtual menus such as server shops, markets, or trader menus are ignored when possible, because the mod is intended for physical storage containers only.

**Anonymous usage stats**

To decide which Minecraft versions and loaders are worth supporting, the mod sends a tiny anonymous ping once every 12 hours. It only contains the mod version, the Minecraft version, the loader (Fabric or NeoForge) and a random install ID. No IP address, player name, server address, world or container data is ever sent or stored.

Toggleable at the bottom of the Settings tab in the mod's GUI, or set `"enabled": false` in `config/noobs-container-searcher/analytics.json`.

## Building

```
.\gradlew.bat build -Pmc=26.2
.\build-all.ps1
```

`-Pmc` picks the Minecraft version, and `build-all.ps1` builds all of them. Jars end up in `build/libs`. `mod_version` is bumped after every build, add `-PnoBump` to skip that.

The mod logic lives in `src/client`, and the version-specific parts are in the `src/mc*` folders. Each version's settings are in `gradle/mc-<version>.properties`.
