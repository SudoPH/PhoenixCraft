# 🔨 PhoenixCraft

![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)
![Status: In development](https://img.shields.io/badge/Status-In%20development-orange.svg)
![Dev version](https://img.shields.io/badge/Dev%20version-1.0.0-informational.svg)
![Paper 1.21+](https://img.shields.io/badge/Paper-1.21%2B-green.svg)
![Java 21](https://img.shields.io/badge/Java-21-red.svg)

**A crafting add-on for [PhoenixFish](https://github.com/SudoPH/PhoenixFish): craft custom fishing rods and bait through a categorized menu, level up your crafting skill, unlock achievements, trade recipes and shop on a black market.**

> 🚧 **PhoenixCraft is still in development and cannot be installed yet.** This repository contains the source code only, and there is no download. Version 1.0.0 is the feature-complete development version, but it has not been tested extensively and has not been released. The plugin will be opened to the public later. If you spot a bug in the code, feel free to [open an issue](https://github.com/SudoPH/PhoenixCraft/issues).

---

## Table of Contents

* [What is it?](#-what-is-it)
* [Features](#-features)
* [Installation](#-installation)
* [Commands & Permissions](#-commands--permissions)
* [Configuration](#-configuration)
* [Data Files](#-data-files)
* [How it works](#-how-it-works)
* [Before the public release](#-before-the-public-release)
* [Development history](#-development-history)
* [Building from source](#-building-from-source)
* [Contributing](#-contributing)
* [License](#-license)

---

## 📋 What is it?

PhoenixCraft gives players a menu to craft the custom fishing rods and bait of PhoenixFish. Recipes are grouped into categories and unlocked through a crafting-level progression (level 1 to 100). Crafting earns XP, achievements and discovery bonuses, and recipes can be learned, rented out or bought on a black market.

* **49 recipes:** 21 fishing rods and 28 baits
* **Base recipes:** 8 rod tiers (Hardwood → Iron → Gold → Diamond → Netherite → Ocean → Legendary → Cosmic) and 13 baits
* **Enhanced recipes:** 14 upgraded versions (8 rods and 6 baits)
* **Extra recipes:** 14 more (5 rods and 9 baits), so there is a recipe at least every 5 levels

> Most systems are optional and can be switched on or off in `config.yml`. See [Configuration](#-configuration).

---

## ✨ Features

### 🎒 Crafting GUI

* Six menus: main menu, category, recipe detail, statistics, achievements and black market
* **Craft**, **Craft & Close** and **MAX** buttons (MAX crafts as many as your materials allow)
* **Shift-click bulk crafting** (up to 1000 at once), with results added to your inventory in stacks
* Materials are matched by item type and custom model data, and are taken from your main inventory first, then the hotbar. Armor is never touched.
* The recipe requires the right crafting level, and optionally enough money and a learned recipe

### 🔍 Search, filter and sort

* **Search** recipes with `/pcraft search <query>`, or click the search button and type in chat (times out after 30 seconds)
* **Filter** by All, Craftable or Locked
* **Sort** by ID, Level, XP or Tier
* Your filter, sort and search choices are remembered while you are online

### 📈 Crafting level and XP

* A **curved XP formula** based on your level, with a configurable base value and exponent
* **XP multipliers by level range:** ×1.0 (levels 1-10), ×1.5 (11-30), ×2.5 (31-60), ×4.0 (61-80) and ×6.0 (81-100)
* A **global XP multiplier**, for example `2.0` for a double XP weekend
* Level-ups show a title, a sound and a milestone chime
* The crafting level cap is **100**

### 🏷️ Tier system

PhoenixFish rods and bait are split into **8 tiers**, each with its own color. Tiers are shown in the menus and change the sounds you hear when you craft.

| Tier | Color    | Levels  |
| ---- | -------- | ------- |
| 1    | White    | 1-9     |
| 2    | Green    | 10-24   |
| 3    | Blue     | 25-39   |
| 4    | Purple   | 40-54   |
| 5    | Gold     | 55-69   |
| 6    | Orange   | 70-84   |
| 7    | Red      | 85-94   |
| 8    | Rainbow  | 95-100  |

The tier system can be switched off (`tier-system.enabled: false` in the PhoenixFish configuration). For the luck and bait values of each tier, see [`BALANCING_NOTES.md`](BALANCING_NOTES.md).

### 🏆 Achievements

* **14 built-in achievements** with a progress display (for example `47/50`)
* **9 achievement types:** first craft, total crafts, unique recipes, bulk crafts, a specific recipe, crafts per recipe, crafts per category, total XP earned and first discovery
* **Achievement tiers:** bronze, silver, gold and diamond
* **Rewards** can be console commands (with a `%player%` placeholder) or items
* Add your own achievements in `custom-achievements.yml`

### 📊 Statistics

* Per-player stats: total crafts, total XP earned, crafts per recipe, unique recipes and the time of the first and last craft
* A **leaderboard** with the top crafters on the server

### 🧭 Discovery

* The first time a player crafts a recipe they get **bonus XP**, based on the recipe's XP reward with a configurable multiplier and minimum and maximum
* A title, a sound and a chat message, each of which can be switched off

### 📖 Recipe sharing

* Learn recipes from **recipe books**: items that you right-click to learn a recipe (the book is used up)
* With `require-learning` on, a recipe cannot be crafted until it has been learned
* A menu with the recipes you have learned: `/pcraft recipes`
* Craft a recipe book from materials with `/pcraft craftbook <id>`
* Admins can give recipes to players: `/pcraft giverecipe <player> <id>`

### 🔑 Recipe rental

* Players can rent out recipes to each other for a limited time
* `/pcraft rent offer`, `accept`, `list` and `cancel`
* Rentals expire automatically
* Configurable server fee, price and duration limits, and limits per player for offers and active rentals

### 🕶️ Black market

* A black market menu with **random stock** and **random prices**
* The stock refreshes on a timer (every 6 hours by default) and can be announced to the whole server
* A recipe pool you choose: all recipes, only rods, only bait, or specific recipes
* A purchase limit per player for each refresh
* Admins can refresh the stock manually

### 💰 Economy

* Optional **crafting fees**, per item or as a flat fee for bulk crafts, taken from the player's PhoenixFish balance (Vault is not used)
* `/pcraft sell all` and `/pcraft sell hand` sell crafted items, with the price based on the recipe's XP reward

### 🔔 Notifications

* Each message type has its own switch: craft success and failure, level-ups, achievements, discoveries and economy fees, each as chat, title or sound
* Two master switches (`all-sounds` and `all-chat`) turn every sound or chat message off at once

### 🎆 Effects

* Particles when you craft, capped at 100 for bulk crafts
* Sounds that depend on the recipe tier (for example Diamond plays a chime and Cosmic plays a toast sound)
* A bonus sound for bulk crafts of 10 or more

### 🌍 Languages

* English and Turkish (`messages_en.yml`, `messages_tr.yml`), with **300+ message keys** each
* No hardcoded text: all messages and settings can be changed in the configuration files

### ⚡ Performance

* Ingredient checks scan your inventory only once, even for bulk crafts
* The XP formula is cached instead of being read from the configuration on every craft
* Stats, achievements and the black market are written to disk only when something changed, by a shared auto-save timer
* The leaderboard is cached until the stats change
* A per-player click cooldown and next-tick menu refreshes prevent double-click glitches
* Per-player state is cleaned up when a player leaves or the plugin reloads
* Systems that are switched off are not loaded at all

---

## 📦 Installation

> 🚧 **Installation is not available yet.**
>
> PhoenixCraft is still in development and has not been released. There is no download, and it cannot be installed on a server for now. It will be opened to the public later, and this section will then be updated with the download and install steps.

### Requirements (planned)

* **Paper 1.21+** (Java 21)
* **PhoenixFish 1.3.4 or newer.** This is a hard dependency (`depend: [PhoenixFish]` in `plugin.yml`).

### Planned steps

1. Put the PhoenixFish jar into your server's `plugins/` folder.
2. Put the PhoenixCraft jar into the same folder.
3. Restart the server. Do not use the server's `/reload`.
4. Join the game and run `/pcraft`.

If PhoenixFish is missing, PhoenixCraft will not start.

---

## ⌨️ Commands & Permissions

Main command: `/pcraft` (alias: `/pc`). Tab completion is supported.

| Command                              | Description                                    | Permission           |
| ------------------------------------ | ---------------------------------------------- | -------------------- |
| `/pcraft`                            | Opens the main menu                            | `phoenixcraft.use`   |
| `/pcraft rods`                       | Opens the rods category                        | `phoenixcraft.use`   |
| `/pcraft baits`                      | Opens the bait category                        | `phoenixcraft.use`   |
| `/pcraft search <query>`             | Searches the recipes                           | `phoenixcraft.use`   |
| `/pcraft stats`                      | Opens the stats menu and leaderboard           | `phoenixcraft.use`   |
| `/pcraft achievements`               | Opens the achievements menu                    | `phoenixcraft.use`   |
| `/pcraft recipes`                    | Shows the recipes you have learned             | `phoenixcraft.use`   |
| `/pcraft craftbook <id>`             | Crafts a recipe book                           | `phoenixcraft.use`   |
| `/pcraft rent offer <id> <price> <minutes>` | Offers a recipe for rent                | `phoenixcraft.use`   |
| `/pcraft rent accept <player> <id>`  | Rents a recipe from a player                   | `phoenixcraft.use`   |
| `/pcraft rent list`                  | Lists the rental offers                        | `phoenixcraft.use`   |
| `/pcraft rent cancel <id>`           | Cancels one of your offers                     | `phoenixcraft.use`   |
| `/pcraft blackmarket`                | Opens the black market                         | `phoenixcraft.use`   |
| `/pcraft blackmarket refresh`        | Refreshes the black market stock               | `phoenixcraft.admin` |
| `/pcraft sell <all\|hand>`           | Sells crafted items                            | `phoenixcraft.use`   |
| `/pcraft giverecipe <player> <id>`   | Gives a recipe to a player                     | `phoenixcraft.admin` |
| `/pcraft reload`                     | Reloads PhoenixCraft and PhoenixFish configs   | `phoenixcraft.admin` |

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

The recipes themselves (`custom_recipes.yml`, `rods.yml`, `baits.yml`) and the crafting XP and tier settings belong to PhoenixFish.

Every system can be switched on or off independently:

| System               | Config path                         | Default |
| -------------------- | ----------------------------------- | ------- |
| Particle effects     | `particles.enabled`                 | ✅ On   |
| Statistics           | `stats.enabled`                     | ✅ On   |
| Achievements         | `achievements.enabled`              | ✅ On   |
| Search               | `search.enabled`                    | ✅ On   |
| Filtering            | `filters.enabled`                   | ✅ On   |
| Sorting              | `sort.enabled`                      | ✅ On   |
| Discovery (bonus XP) | `discovery.enabled`                 | ✅ On   |
| Tier system          | `tier-system.enabled` (PhoenixFish) | ✅ On   |
| Crafting fees        | `economy.enabled`                   | ❌ Off  |
| Recipe sharing       | `recipe-sharing.enabled`            | ❌ Off  |
| Recipe book crafting | `recipe-book-crafting.enabled`      | ❌ Off  |
| Recipe rental        | `recipe-rental.enabled`             | ❌ Off  |
| Black market         | `blackmarket.enabled`               | ❌ Off  |

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

| File                     | Content                                    |
| ------------------------ | ------------------------------------------ |
| `stats.yml`              | Per-player crafting statistics             |
| `achievements.yml`       | Achievements unlocked by each player       |
| `learned_recipes.yml`    | Recipes learned by each player             |
| `discovered_recipes.yml` | Recipes discovered by each player          |
| `blackmarket.yml`        | Black market stock and purchase records    |
| `rentals.yml`            | Rental offers and active rentals           |

---

## 🏗️ How it works

PhoenixCraft uses the PhoenixFish API (`PhoenixFishAPI`, provided through the Bukkit services manager). PhoenixFish owns the recipes, the crafting logic and the crafting level and XP. PhoenixCraft provides the menus and its own optional systems on top.

```text
┌─────────────────────────────────┐    ┌──────────────────────────────────┐
│      PhoenixFish (required)     │    │      PhoenixCraft (this plugin)  │
│  ─────────────────────────────  │    │  ──────────────────────────────  │
│  • custom_recipes.yml (recipes) │    │  • /pcraft command               │
│  • rods.yml, baits.yml          │    │  • Crafting GUI (6 menus)        │
│  • Crafting logic, level, XP    │    │  • Stats, achievements, discovery│
│  • PhoenixFishAPI (service)     │◄───┤  • Recipe sharing and rental     │
│  • PhoenixFishReloadEvent       │    │  • Black market, selling         │
└─────────────────────────────────┘    └──────────────────────────────────┘
            ▲                                      │
            │        Bukkit ServicesManager        │
            └──────────────────────────────────────┘
```

PhoenixFish also works without PhoenixCraft. Open menus are closed automatically when PhoenixFish reloads or is disabled, and PhoenixCraft reacts to PhoenixFish's reload event.

---

## 🚧 Before the public release

PhoenixCraft 1.0.0 has all planned features. Before it is opened to the public, we still want to:

* Test it more on real servers, including many players crafting at once and open menus during a reload
* Write documentation (a wiki) and a migration guide for older development versions
* Settle the PhoenixFish API so that it does not change after release

---

## 📅 Development history

| Version | Theme                                                                          |
| ------- | ------------------------------------------------------------------------------ |
| 0.0.1   | MVP: basic crafting GUI                                                        |
| 0.0.2   | Stabilization (race conditions, memory leaks, level-up effects)                |
| 0.0.3   | Reload, bulk crafting, MAX button                                              |
| 0.1.0   | Configuration and translations (English and Turkish)                           |
| 0.2.0   | Particles and tier sounds                                                      |
| 0.3.0   | Statistics and achievements                                                    |
| 0.4.0   | Search, filtering and sorting                                                  |
| 0.5.0   | Crafting fees and recipe sharing                                               |
| 0.6.0   | Discovery bonus and black market                                               |
| 0.7.0   | Achievements overhaul (tiers, custom achievements) and `/pcraft craftbook`     |
| 0.8.0   | Enhanced equipment (14 new recipes)                                            |
| 0.9.0   | Curved XP formula, tier system, notification controls, bug fixes, performance  |
| 1.0.0   | Recipe rental, black market menu, selling and recipes for the level gaps       |

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
**Development version:** 1.0.0  
**Status:** 🚧 In development (not released yet)
