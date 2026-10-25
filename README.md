# Skeleton Launcher

Skeleton is a simple Minecraft launcher I made so you can keep your game, your mods and your modpacks in one tidy place. Each setup you make is its own "instance", with its own mods, worlds and settings, so playing a big modpack never messes up your plain vanilla game.

It's one small Java program. No installer, no extra downloads other than the game itself.

## What you need

- Java 17 or newer installed on your computer. If double-clicking the jar does nothing, you probably need to install Java first.
- An internet connection the first time you play, because Skeleton downloads Minecraft, the mod loader and any mods you pick.

## Starting it

Open a terminal in the folder with the file and run:

```
java -jar SkeletonLauncher.jar
```

On most computers you can also just double-click the jar.

Skeleton keeps everything in a hidden folder called `.skeleton` in your home folder (on Windows it's inside `AppData\Roaming`). That's where your instances, mods and worlds live. If you want to move it somewhere else, start it with `-Dskeleton.home=/your/path`.

## Your first game

1. Type a player name in the box at the bottom left.
2. Click **New instance**. Give it a name, choose a Minecraft version, and pick a loader:
   - **Vanilla** is the plain game with no mods.
   - **Fabric** or **Quilt** let you use mods. Fabric has the biggest choice of mods, and Quilt can run most Fabric mods too.
3. Press **Play**. The first launch takes a while because it's downloading the game. After that it starts quickly.

You can make as many instances as you like and switch between them in the left sidebar.

## The four tabs

**Play** starts the game and shows what's going on: download progress and the game's own messages. While the game is running the button turns into **Stop**. If something breaks, the answer is usually in this log.

**Mods** shows what's installed in the selected instance. You can switch a mod off without deleting it (handy when hunting for a crash), remove it, or open the folder to drop in your own mod files.

**Browse Modrinth** is a built-in search for [Modrinth](https://modrinth.com), a site with thousands of free mods and modpacks. Search, click a result, then **Install selected**.
- Mods go into the instance you have selected, and anything they depend on is installed too.
- Modpacks create a brand new instance for you.
- Leave "Match this instance" ticked and you'll only see mods that work with your Minecraft version and loader, so you don't download things that won't run.

**Settings** is where you change how much memory the game gets, point to a different Java if you need one, add extra Java flags, or delete the instance.

## Modpacks

There are two ways to get one:
- Search for it in **Browse Modrinth** with the dropdown set to Modpacks.
- Download a `.mrpack` file from somewhere and click **Import .mrpack**.

Skeleton downloads every mod in the pack and sets up the loader for you. This works for Fabric and Quilt packs. Packs that need Forge or NeoForge won't install, and Skeleton will tell you so instead of failing quietly.

A reasonable starting point for memory is 4096 MB for a lightly modded game and 6144 MB or more for a big modpack. Giving it far more than it needs doesn't help.

- **Offline accounts only.** Skeleton just uses the player name you type. There's no Microsoft login, so you can't join servers that check for a real account. Singleplayer and LAN work fine.

