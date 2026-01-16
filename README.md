# Hytale Friends

Quality of life improvements for playing with friends in Hytale.

## Features

### Player Radar (Automatic)

Automatically shows all other players on the top HUD compass - the same bar where you see portals, death markers, and waypoints.

**No commands needed** - it just works! When you join a server with this mod, all other players will appear on your compass with their username.

How it works:
- Player positions update every 500ms for smooth tracking
- Players appear/disappear from compass as they join/leave
- Uses the same marker system as portals and death points

## Requirements

- Java 17+
- Hytale Early Access
- `HytaleServer.jar` (from game installation, placed in `libs/`)

## Build

```bash
./gradlew build
```

The compiled plugin will be at `build/libs/HytaleFriends-0.1.0.jar`

## Install

Copy the JAR to your Hytale mods folder:

```bash
# Linux (Flatpak)
cp build/libs/HytaleFriends-0.1.0.jar ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/Mods/

# One-liner build + install
./gradlew build && cp build/libs/*.jar ~/.var/app/com.hypixel.HytaleLauncher/data/Hytale/UserData/Mods/
```

## Project Structure

```
hytale-friends/
├── src/main/java/com/friends/
│   ├── FriendsPlugin.java              # Main plugin entry point
│   └── features/radar/
│       └── PlayerRadarSystem.java      # HUD compass player tracking
├── src/main/resources/
│   └── manifest.json                   # Plugin metadata
├── libs/
│   └── HytaleServer.jar                # Server API (not in repo)
└── build.gradle.kts
```

## How It Works

The mod uses Hytale's `UpdateWorldMap` packet to add `MapMarker` entries for each player. These markers appear on the top HUD compass bar alongside other game markers like portals and death points.

Key components:
- `PlayerRadarSystem` - Tracks all online players and sends position updates
- `MapMarker` - Native Hytale packet structure for compass markers
- `UpdateWorldMap` - Packet sent to update player's compass display

## Future Features

- [ ] Friend list integration (highlight friends differently)
- [ ] Party member highlighting (different color/icon)
- [ ] Distance indicators on markers
- [ ] Custom marker icons per player

## License

MIT
