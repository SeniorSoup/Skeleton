# Skeleton Launcher

A small, dark-themed Minecraft launcher with instances, Fabric and Quilt, Modrinth mods and modpacks, and JVM tuning. It is a single Java file with no dependencies.

## Requirements

- Java 17 or newer to run the launcher
- Internet access for downloading Minecraft, loaders and mods

## Run

```
java -jar SkeletonLauncher.jar
```

Data is stored in `~/.skeleton` (`%APPDATA%\.skeleton` on Windows). Set `-Dskeleton.home=/some/path` to change it.

## Build from source

```
javac -d out Skeleton.java
jar cfe SkeletonLauncher.jar Skeleton -C out . icon.jpg
```

Put `icon.jpg` next to the classes so it is bundled as the logo.

## Using it

1. Type a player name in the sidebar.
2. Click **New instance**, pick a Minecraft version and a loader (Vanilla, Fabric or Quilt).
3. Press **Play**. The first launch downloads the game, libraries and assets.

### Tabs

- **Play**: launch or stop the game, with a live log and download progress.
- **Mods**: list, enable or disable, remove, and open the mods folder.
- **Browse Modrinth**: search mods or modpacks. Mods install into the selected instance with their required dependencies. Modpacks create a new instance. "Match this instance" limits results to the instance's Minecraft version and loader.
- **Settings**: memory, Java path, extra JVM flags, and tuned JVM flags on or off.

### Modpacks

Use **Browse Modrinth** with Modpacks selected, or **Import .mrpack** for a local file. Fabric and Quilt packs are supported. Forge and NeoForge packs are not.

### Performance

- **Add performance mods** (Mods tab, Fabric or Quilt only) installs Sodium, Lithium, FerriteCore, ModernFix, EntityCulling and ImmediatelyFast where a compatible version exists.
- Tuned JVM flags (G1GC) are on by default for new instances.
- Downloads run in parallel and skip files that are already valid.

## Limitations

- Offline accounts only. There is no Microsoft login, so online-mode servers will reject you.
- No Forge or NeoForge support.
- The game runs on the same Java as the launcher unless you set a Java path. Minecraft 1.16 and older usually need Java 8.
- Checksums are verified for Modrinth files and Mojang downloads.
