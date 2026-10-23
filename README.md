# 🔨 PhoenixCraft

![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)
![Status: Early Stage](https://img.shields.io/badge/Status-Early%20Stage-orange.svg)
![Paper 1.21+](https://img.shields.io/badge/Paper-1.21%2B-green.svg)
![Java 21](https://img.shields.io/badge/Java-21-red.svg)

**Categorized crafting GUI for [PhoenixFish](https://github.com/SudoPH/PhoenixFish): fishing rod and bait crafting system.**

> ⚠️ **This plugin is in an EARLY STAGE and has not been released yet.** The core crafting pipeline works, but several features are incomplete. Please [open an issue](https://github.com/SudoPH/PhoenixCraft/issues) if you spot a bug.

---

## Table of Contents

* [What is it?](#-what-is-it)
* [Installation](#-installation)
* [Usage](#-usage)
* [Architecture](#-architecture)
* [Project Structure](#-project-structure)
* [Early Stage Notice](#-early-stage-notice)
* [Development](#-development)
* [Contributing](#-contributing)
* [License](#-license)

---

## 📋 What is it?

PhoenixCraft is a standalone **crafting GUI module** for the PhoenixFish fishing plugin. Players craft custom fishing rods and bait through a categorized menu, gated by a crafting-level progression system.

* **Rods:** 8-tier progression (Hardwood → Iron → Gold → Diamond → Netherite → Ocean → Legendary → Cosmic)
* **Bait:** 13 baits ranging from Common to Legendary (`worm`, `bread_crust`, `minnow_bait`, `seaweed_wrap`, `glowing_bait`, `bug_paste`, `enchanted_dough`, `bloodied_flesh`, `spider_silk`, `ocean_essence`, `golden_roe`, `void_leech`, `lunar_moth`)
* **Crafting level and XP:** every craft awards XP, and levels unlock higher-tier recipes
* **Strict separation of concerns:** PhoenixCraft only renders the GUI and forwards clicks. All data and game logic live in PhoenixFish.

---

## 📦 Installation

> 🚧 **Installation is currently unavailable.**
>
> PhoenixCraft `0.0.1` has not been published yet. The first release depends on **PhoenixFish 1.3.1**, which has not been published either. Once both versions are out, the steps below will apply.

### Requirements (once released)

* **Paper 1.21+** (Java 21)
* **PhoenixFish 1.3.1 or newer.** This is a hard dependency (`depend: [PhoenixFish]` in `plugin.yml`).

### Planned steps

1. Put `PhoenixFish-1.3.1.jar` into your server's `plugins/` folder.
2. Put `PhoenixCraft-0.0.1.jar` into the same folder.
3. Start the server.
4. Check the console. You should see output like this (the log messages are currently in Turkish):

   ```text
   [PhoenixFish] PhoenixFish başarıyla etkinleştirildi!
   [PhoenixCraft] PhoenixCraft v0.0.1 etkinleştirildi.
   [PhoenixCraft] Connected to PhoenixFishAPI: com.phoenix.fish.api.PhoenixFishAPI
   ```

5. Join the game and run `/pcraft`.

---

## 🎮 Usage

### Commands

| Command         | Description                          |
| --------------- | ------------------------------------ |
| `/pcraft`       | Opens the main menu (alias: `/pc`)   |
| `/pcraft rods`  | Jumps straight to the rods category  |
| `/pcraft baits` | Jumps straight to the bait category  |
| `/pcraft help`  | Shows help text                      |

### Permissions

| Permission           | Default  | Description                |
| -------------------- | -------- | -------------------------- |
| `phoenixcraft.use`   | everyone | Use the `/pcraft` command  |
| `phoenixcraft.admin` | op       | Administrative subcommands |

### GUI flow

```text
/pcraft
  └── MAIN MENU (27 slots)
       ├─ 🎣 RODS         → category menu
       ├─ ℹ️ INFO          → shows crafting level + XP
       ├─ 🪱 BAITS         → category menu
       └─ ❌ CLOSE
              ↓
       CATEGORY MENU (54 slots, paginated)
       ├─ Recipe icons (lore lists required materials)
       ├─ « PREVIOUS PAGE
       ├─ MAIN MENU
       └─ NEXT PAGE »
              ↓
       RECIPE DETAIL (27 slots)
       ├─ 📖 Required Materials (book)
       ├─ 🎣 Result preview
       ├─ ✔ CRAFT (green = ready / red = blocked)
       └─ « BACK
```

### Crafting flow

1. On the detail screen, click the green **CRAFT** button.
2. The required materials are consumed from your inventory automatically.
3. The crafted item is added to your inventory (items that don't fit drop on the ground).
4. Crafting XP is awarded, and level-ups happen automatically.

---

## 🏗️ Architecture

```text
┌─────────────────────────────────┐    ┌──────────────────────────────────┐
│      PhoenixFish (required)     │    │      PhoenixCraft (this plugin)  │
│  ─────────────────────────────  │    │  ──────────────────────────────  │
│  • custom_recipes.yml (recipes) │    │  • /pcraft command               │
│  • rods.yml, baits.yml          │    │  • Categorized crafting GUI      │
│  • FishingData (craftLevel/XP)  │    │  • Rods / Baits menus            │
│  • BaitManager, ItemFixer       │    │  • Craft button + material list  │
│  • PhoenixFishAPI (service)     │◄───┤  • PhoenixFishAPI consumer       │
│  • CraftingManager (game logic) │    │  • CraftingListener (click/drag) │
└─────────────────────────────────┘    └──────────────────────────────────┘
            ▲                                      │
            │   Bukkit ServicesManager             │
            └──────────────────────────────────────┘
```

**Design principle:** PhoenixCraft **only renders the GUI and forwards interactions**. All game logic (recipe loading, material checks, crafting, XP and levels) stays in PhoenixFish. This means:

* ✅ A single source of truth
* ✅ Database and cache in one place
* ✅ A single reload command (`/pf reload`)
* ✅ Other plugins can reuse the same API

---

## 📁 Project Structure

```text
PhoenixCraft/
├── pom.xml
└── src/main/
    ├── java/com/phoenix/craft/
    │   ├── PhoenixCraft.java           # Main class
    │   ├── command/
    │   │   ├── CraftCommand.java       # /pcraft executor
    │   │   └── CraftTabCompleter.java  # Tab completer
    │   ├── gui/
    │   │   ├── CraftingGUIHolder.java  # InventoryHolder marker
    │   │   └── CraftingGUIManager.java # Screen builder + craft logic
    │   └── listener/
    │       └── CraftingListener.java   # InventoryClick/Drag events
    └── resources/
        └── plugin.yml
```

---

## ⚠️ Early Stage Notice

This plugin is **under active development** and should be tested thoroughly before any production use.

### Known missing features

* ❌ No shift-click bulk crafting (one craft per click)
* ❌ No "contents" hover tooltip on the detail screen
* ❌ Minimal sound design (success and fail only)
* ❌ Incomplete localization (some GUI text is hardcoded in Turkish)
* ❌ No config file (the GUI appearance is hardcoded)

### Known risks

* Material consumption goes through `Inventory.getContents()` in slot order, so the slot the player expects may differ.
* Very rapid clicks can race the GUI refresh.
* A crafting level-up currently shows only as a chat message (no title).

### Untested scenarios

* Concurrent crafts by multiple players
* Open GUIs during a `/pf reload`
* Starting the server without PhoenixFish present (expected: PhoenixCraft disables itself cleanly)

---

## 🔧 Development

### Build from source

```bash
git clone https://github.com/SudoPH/PhoenixCraft.git
cd PhoenixCraft
mvn clean package
# → target/PhoenixCraft-0.0.1.jar
```

> The build needs the PhoenixFish artifact (`com.Phoenix.Fish:PhoenixFish:1.3.1`) in your local Maven repository. Install it first with:
>
> ```bash
> mvn install:install-file \
>   -Dfile=PhoenixFish-1.3.1.jar \
>   -DgroupId=com.Phoenix.Fish \
>   -DartifactId=PhoenixFish \
>   -Dversion=1.3.1 \
>   -Dpackaging=jar \
>   -DgeneratePom=true
> ```

### Maven dependency (for downstream plugins)

```xml
<dependency>
    <groupId>com.phoenix</groupId>
    <artifactId>PhoenixCraft</artifactId>
    <version>0.0.1</version>
    <scope>provided</scope>
</dependency>
```

---

## 🤝 Contributing

* Open an [issue](https://github.com/SudoPH/PhoenixCraft/issues) for bug reports or feature requests.
* Pull requests are welcome. For major changes, please open an issue first.
* When reporting a bug, include your Minecraft version, server software and version, PhoenixFish and PhoenixCraft versions, and relevant logs.

---

## 📜 License

PhoenixCraft is free and open-source software, licensed under the **GNU General Public License v3.0**.

You are free to use, study, share and modify it under the terms of that license. If you distribute a modified version, it must also be released under the GPL v3.0 and its source code must be made available.

See the [`LICENSE`](LICENSE) file for the complete license text.

---

## 📚 Related Projects

* [**PhoenixFish**](https://github.com/SudoPH/PhoenixFish): the parent fishing plugin (required dependency)

---

**Author:** Sudo ([@SudoPH](https://github.com/SudoPH))  
**Version:** 0.0.1  
**Status:** 🚧 Early stage / pre-release
