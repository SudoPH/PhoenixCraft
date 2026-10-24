# 🔨 PhoenixCraft

![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)
![Status: Pre-release](https://img.shields.io/badge/Status-Pre--release-orange.svg)
![Version](https://img.shields.io/badge/Version-0.6.0-informational.svg)
![Paper 1.21+](https://img.shields.io/badge/Paper-1.21%2B-green.svg)
![Java 21](https://img.shields.io/badge/Java-21-red.svg)

**A categorized crafting GUI add-on for [PhoenixFish](https://github.com/SudoPH/PhoenixFish): craft custom fishing rods and bait, level up your crafting skill, unlock achievements and more.**

> ⚠️ **PhoenixCraft is a pre-release (0.6.0).** The core crafting system works, but some features are unfinished and the plugin has not been tested extensively. Please do not use it on a production server yet, and [open an issue](https://github.com/SudoPH/PhoenixCraft/issues) if you find a bug.

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

PhoenixCraft gives players a menu to craft the custom fishing rods and bait of PhoenixFish. Recipes are grouped into categories, locked behind a crafting-level progression, and rewarded with XP, achievements and discovery bonuses.

* **Rods:** 8-tier progression (Hardwood → Iron → Gold → Diamond → Netherite → Ocean → Legendary → Cosmic)
* **Bait:** 13 baits from Common to Legendary (`worm`, `bread_crust`, `minnow_bait`, `seaweed_wrap`, `glowing_bait`, `bug_paste`, `enchanted_dough`, `bloodied_flesh`, `spider_silk`, `ocean_essence`, `golden_roe`, `void_leech`, `lunar_moth`)
* **Crafting level and XP:** every craft awards XP, and levels unlock higher-tier recipes

> Most systems are optional and can be switched on or off in `config.yml`. See [Configuration](#-configuration).

---

## ✨ Features

### 🎒 Crafting GUI

* Categorized menus for rods and bait, with a detail screen for every recipe
* A **Craft** button, plus **Craft & Close** and a **MAX** button that crafts as many as your materials allow
* **Shift-click bulk crafting** (up to 1000 at once), with results added to your inventory in stacks
* "Last crafted" time shown on the recipe
* Empty categories show a placeholder instead of a blank menu
* Materials are taken from your main inventory first, then the hotbar, so your hotbar stays stable

### 🔍 Search, filter and sort

* **Search** recipes with `/pcraft search <query>` (type the query in chat)
* **Filter** by All, Craftable or Locked
* **Sort** by ID, Level, XP or Tier
* Filter, sort and search choices are remembered per player while they are online

### 📈 Crafting level and XP

* Crafting level and XP progression, with level-ups shown as a title, a sound and a milestone chime
* Bulk crafting awards XP and checks level-ups once for the whole batch

### 📊 Statistics

* Per-player stats: total crafts, total XP earned and crafts per recipe
* A stats menu with a **leaderboard**: `/pcraft stats`

### 🏆 Achievements

* **10 built-in achievements** of 5 types: first craft, total crafts, unique recipes, bulk crafts and specific recipes
* Optional **reward commands** for every achievement
* A title, a sound and a chat message when you unlock one
* An achievements menu: `/pcraft achievements`

### 🧭 Discovery

* The first time a player crafts a recipe they get **bonus XP**, with a title, a sound and a chat message

### 📖 Recipe sharing

* Recipes can be learned from **recipe books**, which are items you right-click to learn a recipe
* A menu of your learned recipes: `/pcraft recipes`
* Admins can give recipe books: `/pcraft giverecipe <player> <id>`

### 🕶️ Black market

* A black market with a **random stock** that refreshes on a timer
* A **per-player purchase limit** and a broadcast when the stock refreshes

### 💰 Crafting fees

* Optional fee for crafting an item

### 🎆 Effects

* Particles when you craft (capped at 100 particles during bulk crafts)
* Sounds that depend on the recipe tier (for example Diamond plays a chime and Cosmic plays a toast sound)
* A bonus sound for bulk crafts of 10 or more

### 🌍 Languages and messages

* English and Turkish, with **250+ message keys** each (`messages_en.yml`, `messages_tr.yml`)
* No hardcoded text: all messages and settings can be changed in the configuration files

### ⚡ Performance

* A 150 ms per-player click cooldown and next-tick menu refreshes to prevent double-click glitches
* Thread-safe data structures for cooldowns and timestamps
* Per-player state (cooldowns, filters, searches) is cleaned up when a player leaves
* Stats and achievements are saved only when something changed, with a configurable auto-save interval
* Systems that are switched off are not loaded at all

---

## 📦 Installation

> ⚠️ PhoenixCraft is a pre-release. Back up your server before trying it.

### Requirements

* **Paper 1.21+** (Java 21)
* **PhoenixFish 1.3.1 or newer.** This is a hard dependency (`depend: [PhoenixFish]` in `plugin.yml`).

### Steps

1. Download `PhoenixCraft-0.6.0.jar` from the [Releases](https://github.com/SudoPH/PhoenixCraft/releases) page. It is marked as a pre-release.
2. Put it into your server's `plugins/` folder, next to `PhoenixFish.jar`.
3. Restart the server. Do not use the server's `/reload`.
4. Join the game and run `/pcraft`.

If PhoenixFish is missing, PhoenixCraft will not start.

---

## ⌨️ Commands & Permissions

Main command: `/pcraft` (alias: `/pc`). Tab completion is supported.

| Command                          | Description                                      | Permission           |
| -------------------------------- | ------------------------------------------------ | -------------------- |
| `/pcraft`                        | Opens the main menu                              | `phoenixcraft.use`   |
| `/pcraft rods`                   | Opens the rods category                          | `phoenixcraft.use`   |
| `/pcraft baits`                  | Opens the bait category                          | `phoenixcraft.use`   |
| `/pcraft search <query>`         | Searches the recipes                             | `phoenixcraft.use`   |
| `/pcraft stats`                  | Opens the stats menu and leaderboard             | `phoenixcraft.use`   |
| `/pcraft achievements`           | Opens the achievements menu                      | `phoenixcraft.use`   |
| `/pcraft recipes`                | Shows the recipes you have learned               | `phoenixcraft.use`   |
| `/pcraft reload`                 | Reloads PhoenixCraft and PhoenixFish configs     | `phoenixcraft.admin` |
| `/pcraft giverecipe <player> <id>` | Gives a recipe book to a player                | `phoenixcraft.admin` |
| `/pcraft sell <all\|hand>`       | Not finished yet (see [below](#-not-finished-yet)) | `phoenixcraft.use`   |

| Permission           | Default  | Description                     |
| -------------------- | -------- | ------------------------------- |
| `phoenixcraft.use`   | everyone | Use `/pcraft` and its menus     |
| `phoenixcraft.admin` | op       | Administrative subcommands      |

---

## ⚙️ Configuration

PhoenixCraft creates these files in `plugins/PhoenixCraft/`:

| File               | Purpose                                                  |
| ------------------ | -------------------------------------------------------- |
| `config.yml`       | All settings, with comments and diagrams explaining them |
| `messages_en.yml`  | English messages                                         |
| `messages_tr.yml`  | Turkish messages                                         |

Every system can be switched on or off independently:

| System                | Config path                    | Default |
| --------------------- | ------------------------------ | ------- |
| Particle effects      | `particles.enabled`            | ✅ On   |
| Statistics            | `stats.enabled`                | ✅ On   |
| Achievements          | `achievements.enabled`         | ✅ On   |
| Search                | `search.enabled`               | ✅ On   |
| Filtering             | `filters.enabled`              | ✅ On   |
| Sorting               | `sort.enabled`                 | ✅ On   |
| Discovery (bonus XP)  | `discovery.enabled`            | ✅ On   |
| Crafting fees         | `economy.enabled`              | ❌ Off  |
| Recipe sharing        | `recipe-sharing.enabled`       | ❌ Off  |
| Recipe book crafting  | `recipe-book-crafting.enabled` | ❌ Off  |
| Recipe rental         | `recipe-rental.enabled`        | ❌ Off  |
| Black market          | `blackmarket.enabled`          | ❌ Off  |

---

## 💾 Data Files

These files are created on the server while the plugin runs:

| File                    | Content                                       |
| ----------------------- | --------------------------------------------- |
| `stats.yml`             | Per-player crafting statistics                |
| `achievements.yml`      | Achievements unlocked by each player          |
| `learned_recipes.yml`   | Recipes learned by each player                |
| `discovered_recipes.yml`| Recipes discovered by each player             |
| `blackmarket.yml`       | Black market stock and purchase records       |

---

## 🏗️ How it works

PhoenixCraft uses the PhoenixFish API (`PhoenixFishAPI`, provided through the Bukkit services manager). Recipes, items and the crafting level come from PhoenixFish, and PhoenixCraft adds the menus and its own optional systems on top.

```text
┌─────────────────────────────────┐    ┌──────────────────────────────────┐
│      PhoenixFish (required)     │    │      PhoenixCraft (this plugin)  │
│  ─────────────────────────────  │    │  ──────────────────────────────  │
│  • custom_recipes.yml (recipes) │    │  • /pcraft command               │
│  • rods.yml, baits.yml          │    │  • Crafting GUI (6 screens)      │
│  • Crafting level / XP          │    │  • Stats, achievements, discovery│
│  • PhoenixFishAPI (service)     │◄───┤  • Recipe sharing, black market  │
│  • Crafting game logic          │    │  • PhoenixFishAPI consumer       │
└─────────────────────────────────┘    └──────────────────────────────────┘
            ▲                                      │
            │        Bukkit ServicesManager        │
            └──────────────────────────────────────┘
```

The GUI has six screens: main menu, category, recipe detail, stats, recipes and achievements.

---

## 🚧 Not finished yet

* `/pcraft sell` is a stub and does not sell anything yet.
* **Recipe book crafting** and **recipe rental**: the configuration is ready, but there are no commands for them yet.
* Concurrent crafting by many players and open menus during a `/pf reload` have not been tested extensively.

---

## 📅 Development history

| Version | Theme                                                           |
| ------- | --------------------------------------------------------------- |
| 0.0.1   | MVP: basic crafting GUI                                         |
| 0.0.2   | Stabilization (race conditions, memory leaks, level-up effects) |
| 0.0.3   | Reload, bulk crafting, MAX button                               |
| 0.1.0   | Configuration and translations (English and Turkish)            |
| 0.2.0   | Particles and tier sounds                                       |
| 0.3.0   | Statistics and achievements                                     |
| 0.4.0   | Search, filtering and sorting                                   |
| 0.5.0   | Crafting fees and recipe sharing                                |
| 0.6.0   | Discovery bonus and black market                                |

---

## 🔧 Building from source

```bash
git clone https://github.com/SudoPH/PhoenixCraft.git
cd PhoenixCraft
mvn clean package
# → target/PhoenixCraft-0.6.0.jar
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
**Version:** 0.6.0  
**Status:** 🚧 Pre-release
