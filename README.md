# Noob's Container Searcher

A client-side Fabric mod that remembers the chests, furnaces, barrels and villager trades you open, and lets you search through them later. Nothing needs to be installed on the server.

Supported versions: 1.21.1, 1.21.4, 1.21.8, 1.21.10, 1.21.11, 26.1, 26.1.2, 26.2, 26.3

## How it works

When you open a container, its contents are saved. The mod only searches containers you've already opened and never asks the server for the contents of ones you haven't. Records are kept separately per server, world and dimension. Villager trades are saved along with the villager's profession.

If a container is gone, its record is deleted, but only when you're standing near it and after it has been checked several times.

## Commands

- `/containersearch iron_ore`: search within 100 blocks in your current dimension
- `/containersearch iron_ore global`: search every saved record for the current server/world
- `/containersearch clearhighlight`: clear the highlights

Results are printed in chat with counts and clickable coordinates, and matching containers are highlighted.

## Building

```
.\gradlew.bat build -Pmc=26.2
.\build-all.ps1
```

`-Pmc` picks the Minecraft version, and `build-all.ps1` builds all of them. Jars end up in `build/libs`. `mod_version` is bumped after every build, add `-PnoBump` to skip that.

The mod logic lives in `src/client`, and the version-specific parts are in the `src/mc*` folders. Each version's settings are in `gradle/mc-<version>.properties`.
