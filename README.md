# 🔨 PhoenixCraft

![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)
![Status: In development](https://img.shields.io/badge/Status-In%20development-orange.svg)
![Dev version](https://img.shields.io/badge/Dev%20version-0.9.0-informational.svg)
![Paper 1.21+](https://img.shields.io/badge/Paper-1.21%2B-green.svg)
![Java 21](https://img.shields.io/badge/Java-21-red.svg)

**A crafting add-on for [PhoenixFish](https://github.com/SudoPH/PhoenixFish): craft custom fishing rods and bait through a categorized menu, level up your crafting skill and unlock achievements.**

> 🚧 **PhoenixCraft is still in development and cannot be installed yet.** This repository contains the source code only, and there is no download. The plugin will be opened to the public in a later release. The core features work, but some are unfinished (see [Not finished yet](#-not-finished-yet)) and it has not been tested extensively. If you spot a bug in the code, feel free to [open an issue](https://github.com/SudoPH/PhoenixCraft/issues).

---

## Table of Contents

* [What is it?](#-what-is-it)
* [Features](#-features)
* [Installation](#-installation)
* [Commands & Permissions](#-commands--permissions)
* [Configuration](#-configuration)
* [Data Files](#-data-files)
* [How it works](#-how-it-works)
* [Not finished yet](#-not-finished-yet)
* [Development history](#-development-history)
* [Building from source](#-building-from-source)
* [Contributing](#-contributing)
* [License](#-license)

---

## 📋 What is it?

PhoenixCraft gives players a menu to craft the custom fishing rods and bait of PhoenixFish. Recipes are grouped into categories and unlocked through a crafting-level progression. Crafting earns XP, achievements and discovery bonuses.

* **35 recipes:** 16 fishing rods and 19 baits
* **Base recipes:** 8 rod tiers (Hardwood → Iron → Gold → Diamond → Netherite → Ocean → Legendary → Cosmic) and 13 baits from Common to Legendary
* **Enhanced recipes:** 14 upgraded versions (8 rods and 6 baits)
* **Crafting level:** every craft earns XP, and levels unlock higher-tier recipes

> Most systems are optional and can be switched on or off in `config.yml`. See [Configuration](#-configuration).

---

## ✨ Features

### 🎒 Crafting GUI

* Categorized menus for rods and bait, with a detail screen for every recipe
* **Craft**, **Craft & Close** and **MAX** buttons (MAX crafts as many as your materials allow)
* **Shift-click bulk crafting** (up to 1000 at once), with results added to your inventory in stacks
* The time of your last craft is shown on the recipe
* Materials are taken from your main inventory first, then the hotbar

### 🔍 Search, filter and sort

* **Search** recipes with `/pcraft search <query>`
* **Filter** by All, Craftable or Locked
* **Sort** by ID, Level, XP or Tier
* Your filter, sort and search choices are remembered while you are online

### 📈 Crafting level and XP

* Crafting level and XP progression with a **curved XP formula** that you can tune in the configuration
* **XP multipliers by tier range**, so higher-tier recipes can reward more
* Level-ups show a title, a sound and a milestone chime
* The crafting level cap is **100**

### 🏷️ Tier system

* **8 recipe tiers**, each with its own color, shown in the menus and used for tier sounds
* Can be switched off in the configuration

### 🏆 Achievements

* **14 built-in achievements** with a progress display in the achievements menu
* **9 achievement types:** first craft, total crafts, unique recipes, bulk crafts, a specific recipe, crafts per recipe, crafts per category, total XP earned and first discovery
* **Achievement tiers** from bronze to diamond
* **Rewards** can be commands or items
* Add your own achievements in `custom-achievements.yml`
* Open the menu with `/pcraft achievements`

### 📊 Statistics

* Per-player stats: total crafts, XP earned and crafts per recipe
* A stats menu with a **leaderboard**: `/pcraft stats`

### 🧭 Discovery

* The first time a player crafts a recipe they get **bonus XP**, with a title, a sound and a chat message

### 📖 Recipe sharing

* Learn recipes from **recipe books**: items that you right-click to learn a recipe
* A menu with the recipes you have learned: `/pcraft recipes`
* Craft a recipe book with `/pcraft craftbook <id>`
* Admins can give recipe books: `/pcraft giverecipe <player> <id>`

### 💰 Crafting fees

* Optional fee for crafting an item (off by default)

### 🔔 Notifications

* Every message type has its own switch: craft success and failure, level-ups, achievements and discoveries, each as chat, title or sound
* Two master switches (`all-sounds` and `all-chat`) turn every sound or chat message off at once

### 🎆 Effects

* Particles when you craft, capped at 100 for bulk crafts
* Sounds that depend on the recipe tier (for example Diamond plays a chime and Cosmic plays a toast sound)
* A bonus sound for bulk crafts of 10 or more

### 🌍 Languages

* English and Turkish (`messages_en.yml`, `messages_tr.yml`), with **250+ message keys** each
* No hardcoded text: all messages and settings can be changed in the configuration files

### ⚡ Performance

* Inventory checks for ingredients are done in a single pass, even for bulk crafts
* The XP formula is cached instead of being read from the configuration on every craft
* Stats and achievements are written to disk only when something changed, by a single shared auto-save timer
* The leaderboard is cached until the stats change
* Per-player click cooldown and next-tick menu refreshes prevent double-click glitches
* Per-player state is cleaned up when a player leaves or the plugin reloads
* Systems that are switched off are not loaded at all

---

## 📦 Installation

> 🚧 **Installation is not available yet.**
>
> PhoenixCraft is still in development and has not been released. There is no download, and it cannot be installed on a server for now. It will be opened to the public later, and this section will then be updated with the download and install steps.

### Requirements (planned)

* **Paper 1.21+** (Java 21)
* **PhoenixFish 1.3.4 or newer.** This is a hard dependency (`depend: [PhoenixFish]` in `plugin.yml`). That PhoenixFish version has not been published yet either.

### Planned steps

1. Put the PhoenixFish jar into your server's `plugins/` folder.
2. Put the PhoenixCraft jar into the same folder.
3. Restart the server. Do not use the server's `/reload`.
4. Join the game and run `/pcraft`.

If PhoenixFish is missing, PhoenixCraft will not start.

---

## ⌨️ Commands & Permissions

Main command: `/pcraft` (alias: `/pc`). Tab completion is supported.

| Command                            | Description                                  | Permission           |
| ---------------------------------- | -------------------------------------------- | -------------------- |
| `/pcraft`                          | Opens the main menu                          | `phoenixcraft.use`   |
| `/pcraft rods`                     | Opens the rods category                      | `phoenixcraft.use`   |
| `/pcraft baits`                    | Opens the bait category                      | `phoenixcraft.use`   |
| `/pcraft search <query>`           | Searches the recipes                         | `phoenixcraft.use`   |
| `/pcraft stats`                    | Opens the stats menu and leaderboard         | `phoenixcraft.use`   |
| `/pcraft achievements`             | Opens the achievements menu                  | `phoenixcraft.use`   |
| `/pcraft recipes`                  | Shows the recipes you have learned           | `phoenixcraft.use`   |
| `/pcraft craftbook <id>`           | Crafts a recipe book                         | `phoenixcraft.use`   |
| `/pcraft reload`                   | Reloads the configuration files              | `phoenixcraft.admin` |
| `/pcraft giverecipe <player> <id>` | Gives a recipe book to a player              | `phoenixcraft.admin` |
| `/pcraft sell`                     | Not finished yet                             | `phoenixcraft.use`   |
| `/pcraft rent`                     | Not finished yet                             | `phoenixcraft.use`   |

| Permission           | Default  | Description                 |
| -------------------- | -------- | --------------------------- |
| `phoenixcraft.use`   | everyone | Use `/pcraft` and its menus |
| `phoenixcraft.admin` | op       | Administrative subcommands  |

---

## ⚙️ Configuration

PhoenixCraft creates these files in `plugins/PhoenixCraft/`:

| File                      | Purpose                                                  |
| ------------------------- | -------------------------------------------------------- |
| `config.yml`              | All settings, with comments and diagrams explaining them |
| `messages_en.yml`         | English messages                                         |
| `messages_tr.yml`         | Turkish messages                                         |
| `custom-achievements.yml` | Your own achievements                                    |

The recipes themselves (`custom_recipes.yml`, `rods.yml`, `baits.yml`) belong to PhoenixFish. Balancing notes for them are in [`BALANCING_NOTES.md`](BALANCING_NOTES.md).

Every system can be switched on or off independently:

| System               | Config path                      | Default |
| -------------------- | -------------------------------- | ------- |
| Particle effects     | `particles.enabled`              | ✅ On   |
| Statistics           | `stats.enabled`                  | ✅ On   |
| Achievements         | `achievements.enabled`           | ✅ On   |
| Search               | `search.enabled`                 | ✅ On   |
| Filtering            | `filters.enabled`                | ✅ On   |
| Sorting              | `sort.enabled`                   | ✅ On   |
| Discovery (bonus XP) | `discovery.enabled`              | ✅ On   |
| Tier system          | `tier-system.enabled` (PhoenixFish) | ✅ On |
| Tier XP multipliers  | `crafting-xp.tier-multipliers`   | ✅ On   |
| Crafting fees        | `economy.enabled`                | ❌ Off  |
| Recipe sharing       | `recipe-sharing.enabled`         | ❌ Off  |
| Recipe book crafting | `recipe-book-crafting.enabled`   | ❌ Off  |
| Recipe rental        | `recipe-rental.enabled`          | ❌ Off  |
| Black market         | `blackmarket.enabled`            | ❌ Off  |

Notifications can be switched off one by one or all at once:

```yaml
notifications:
  craft-success-chat: true
  craft-success-sound: true
  craft-fail-chat: true
  craft-fail-sound: true
  crafting-level-up-chat: true
  crafting-level-up-title: true
  crafting-level-up-sound: true
  achievement-chat: true
  achievement-title: true
  achievement-sound: true
  discovery-chat: true
  discovery-title: true
  discovery-sound: true
  all-sounds: true   # false = no sounds at all
  all-chat: true     # false = no chat messages at all
```

---

## 💾 Data Files

These files are created on the server while the plugin runs:

| File                     | Content                                 |
| ------------------------ | --------------------------------------- |
| `stats.yml`              | Per-player crafting statistics          |
| `achievements.yml`       | Achievements unlocked by each player    |
| `learned_recipes.yml`    | Recipes learned by each player          |
| `discovered_recipes.yml` | Recipes discovered by each player       |
| `blackmarket.yml`        | Black market stock and purchase records |

---

## 🏗️ How it works

PhoenixCraft uses the PhoenixFish API (`PhoenixFishAPI`, provided through the Bukkit services manager). Recipes, items and the crafting level and XP come from PhoenixFish, and PhoenixCraft adds the menus and its own optional systems on top.

```text
┌─────────────────────────────────┐    ┌──────────────────────────────────┐
│      PhoenixFish (required)     │    │      PhoenixCraft (this plugin)  │
│  ─────────────────────────────  │    │  ──────────────────────────────  │
│  • custom_recipes.yml (recipes) │    │  • /pcraft command               │
│  • rods.yml, baits.yml          │    │  • Crafting GUI                  │
│  • Crafting level / XP          │    │  • Stats, achievements, discovery│
│  • PhoenixFishAPI (service)     │◄───┤  • Recipe sharing                │
│  • Crafting game logic          │    │  • PhoenixFishAPI consumer       │
└─────────────────────────────────┘    └──────────────────────────────────┘
            ▲                                      │
            │        Bukkit ServicesManager        │
            └──────────────────────────────────────┘
```

Open menus are closed automatically when PhoenixFish reloads or is disabled.

---

## 🚧 Not finished yet

These are planned for the 1.0.0 release:

* **Recipe rental:** `/pcraft rent` is a stub (offer, accept, list and cancel are not implemented, and the rentals data file is missing).
* **Black market menu:** the stock system works, but there is no menu or command to use it yet.
* **Selling:** `/pcraft sell` is a stub.
* **Recipe book crafting menu:** the command exists, but the menu does not.
* **Missing recipes:** some levels still have no rod or bait recipe (5 rods and 10 baits).
* **API freeze:** the PhoenixFish API may still change before 1.0.0.

---

## 📅 Development history

| Version | Theme                                                                    |
| ------- | ------------------------------------------------------------------------ |
| 0.0.1   | MVP: basic crafting GUI                                                  |
| 0.0.2   | Stabilization (race conditions, memory leaks, level-up effects)          |
| 0.0.3   | Reload, bulk crafting, MAX button                                        |
| 0.1.0   | Configuration and translations (English and Turkish)                     |
| 0.2.0   | Particles and tier sounds                                                |
| 0.3.0   | Statistics and achievements                                              |
| 0.4.0   | Search, filtering and sorting                                            |
| 0.5.0   | Crafting fees and recipe sharing                                         |
| 0.6.0   | Discovery bonus and black market                                         |
| 0.7.0   | Achievements overhaul (tiers, custom achievements) and `/pcraft craftbook` |
| 0.8.0   | Enhanced equipment (14 new recipes)                                      |
| 0.9.0   | Curved XP formula, tier system, notification controls, bug fixes and performance work |

---

## 🔧 Building from source

This is meant for developers only, since there is no public release yet.

```bash
git clone https://github.com/SudoPH/PhoenixCraft.git
cd PhoenixCraft
mvn clean package
```

> The build needs the PhoenixFish artifact (`com.Phoenix.Fish:PhoenixFish:1.3.4`) in your local Maven repository. Install it first with:
>
> ```bash
> mvn install:install-file \
>   -Dfile=PhoenixFish-1.3.4.jar \
>   -DgroupId=com.Phoenix.Fish \
>   -DartifactId=PhoenixFish \
>   -Dversion=1.3.4 \
>   -Dpackaging=jar \
>   -DgeneratePom=true
> ```

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
**Development version:** 0.9.0  
**Status:** 🚧 In development (not released yet)
