# 🔨 PhoenixCraft

**A powerful, feature-rich crafting add-on for [PhoenixFish](https://github.com/SudoPH/PhoenixFish). Craft custom fishing rods and bait through a fully categorized menu, level up your crafting skill, unlock achievements, trade recipes, and trade on the black market!**

---

## 📋 Table of Contents

* [What is it?](https://www.google.com/search?q=%23-what-is-it)
* [✨ Features](https://www.google.com/search?q=%23-features)
* [📦 Installation](https://www.google.com/search?q=%23-installation)
* [⌨️ Commands & Permissions](https://www.google.com/search?q=%23%EF%B8%8F-commands--permissions)
* [⚙️ Configuration](https://www.google.com/search?q=%23%EF%B8%8F-configuration)
* [💾 Data Files](https://www.google.com/search?q=%23-data-files)
* [🏗️ How It Works](https://www.google.com/search?q=%23%EF%B8%8F-how-it-works)
* [🔧 Building from Source](https://www.google.com/search?q=%23-building-from-source)
* [🤝 Contributing](https://www.google.com/search?q=%23-contributing)
* [📜 License](https://www.google.com/search?q=%23-license)

---

## 📋 What is it?

PhoenixCraft is a standalone **crafting GUI and progression module** for the PhoenixFish fishing plugin. Players craft custom fishing rods and baits through an intuitive, categorized menu system gated by a crafting-level progression (levels 1 to 100). Every craft earns XP, unlocks achievements and discovery bonuses, and recipes can be learned, rented, or traded on the black market.

* **49 Recipes:** 21 fishing rods and 28 baits.
* **Base Recipes:** 8 rod tiers (Hardwood → Iron → Gold → Diamond → Netherite → Ocean → Legendary → Cosmic) and 13 base baits.
* **Enhanced Recipes:** 14 upgraded versions (8 rods and 6 baits).
* **Extra Recipes:** 14 additional recipes ensuring progression milestones at least every 5 levels.

> *Most advanced systems are optional and can be easily toggled on or off in `config.yml`.*

---

## ✨ Features

### 🎒 Crafting GUI

* **Six distinct menus:** Main menu, category browser, recipe detail, statistics, achievements, and black market.
* **Smart Actions:** **Craft**, **Craft & Close**, and **MAX** buttons (MAX automatically crafts as many items as your inventory materials allow).
* **Bulk Crafting:** Shift-click bulk crafting (up to 1,000 at once) with results neatly added to your inventory in stacks.
* **Smart Inventory Scanning:** Materials match by item type and Custom Model Data. Consumes from your main inventory (slots 9–35) first, then your hotbar (slots 0–8). Armor and offhand slots are never touched.

### 🔍 Search, Filter, and Sort

* **Search:** Find recipes instantly via `/pcraft search <query>` or by clicking the search button and typing in chat.
* **Filter:** Toggle between *All*, *Craftable*, and *Locked* recipes.
* **Sort:** Organize by ID, Level, XP, or Tier. Your preferences are saved while online.

### 📈 Crafting Level & XP System

* Curved XP formula based on your level with configurable base values and exponents.
* **XP Multipliers by Level Range:** $\times 1.0$ (levels 1–10), $\times 1.5$ (11–30), $\times 2.5$ (31–60), $\times 4.0$ (61–80), and $\times 6.0$ (81–100).
* Global XP multipliers for special server events (e.g., double XP weekends).
* Level-up celebrations feature titles, custom sounds, and milestone chimes up to level 100.

### 🏷️ Tier System

PhoenixFish rods and baits are split into **8 color-coded tiers**, influencing crafting sounds and item statistics:

| Tier | Color | Levels |
| --- | --- | --- |
| **1** | White | 1–9 |
| **2** | Green | 10–24 |
| **3** | Blue | 25–39 |
| **4** | Purple | 40–54 |
| **5** | Gold | 55–69 |
| **6** | Orange | 70–84 |
| **7** | Red | 85–94 |
| **8** | Rainbow | 95–100 |

*(Can be disabled via `tier-system.enabled` in PhoenixFish config).*

### 🏆 Achievements & Statistics

* **14 Built-in Achievements** across 9 achievement types (first craft, total crafts, unique recipes, bulk crafts, specific recipes, category milestones, total XP, and first discovery) with bronze, silver, gold, and diamond tiers.
* Rewards can grant items or execute console commands (supporting `%player%`). Add custom achievements in `custom-achievements.yml`.
* **Statistics & Leaderboards:** Track total crafts, XP earned, unique recipes, and top server crafters.

### 🧭 Discovery & Recipe Sharing

* **Discovery Bonus:** First-time crafting of a recipe awards bonus XP with a configurable multiplier and limits.
* **Recipe Books:** Right-click recipe books to learn new recipes when `require-learning` is enabled.
* **Recipe Rental:** Rent recipes to other players for a limited time with secure offers (`/pcraft rent`).
* **Black Market:** Random stock and pricing that refreshes on an automated timer with customizable recipe pools.

### ⚡ Performance & Quality of Life

* Fast inventory checking scans player inventories in a single pass.
* Cached XP formulas and leaderboards.
* Click cooldowns (150ms) and deferred menu refreshes prevent double-click glitches.
* Full localization support with English (`messages_en.yml`) and Turkish (`messages_tr.yml`) containing 300+ customizable message keys.

---

## 📦 Installation

### Requirements

* **Paper 1.21+** (Java 21)
* **PhoenixFish 1.3.4+** (Hard dependency: `depend: [PhoenixFish]` in `plugin.yml`)

### Installation Steps

1. Drop `PhoenixFish-1.3.4.jar` into your server's `plugins/` folder.
2. Drop `PhoenixCraft-1.0.0.jar` into the same folder.
3. Start or restart your server *(do not use `/reload`)*.
4. Verify console success output:
```text
[PhoenixFish] PhoenixFish başarıyla etkinleştirildi!
[PhoenixCraft] PhoenixCraft v1.0.0 etkinleştirildi.
[PhoenixCraft] Connected to PhoenixFishAPI: com.phoenix.fish.api.PhoenixFishAPI

```


5. Join the server and run `/pcraft`!

---

## ⌨️ Commands & Permissions

Main command: `/pcraft` (Alias: `/pc`). Full tab-completion is supported.

| Command | Description | Permission |
| --- | --- | --- |
| `/pcraft` | Opens the main crafting menu | `phoenixcraft.use` |
| `/pcraft rods` | Jumps straight to rods | `phoenixcraft.use` |
| `/pcraft baits` | Jumps straight to baits | `phoenixcraft.use` |
| `/pcraft search <query>` | Searches recipes | `phoenixcraft.use` |
| `/pcraft stats` | Opens statistics and leaderboard | `phoenixcraft.use` |
| `/pcraft achievements` | Opens achievements menu | `phoenixcraft.use` |
| `/pcraft recipes` | Shows learned recipes | `phoenixcraft.use` |
| `/pcraft craftbook <id>` | Crafts a recipe book | `phoenixcraft.use` |
| `/pcraft rent offer <id> <price> <mins>` | Offers a recipe for rent | `phoenixcraft.use` |
| `/pcraft rent accept <player> <id>` | Rents a recipe | `phoenixcraft.use` |
| `/pcraft rent list` | Lists rental offers | `phoenixcraft.use` |
| `/pcraft rent cancel <id>` | Cancels your rental offer | `phoenixcraft.use` |
| `/pcraft blackmarket` | Opens the black market | `phoenixcraft.use` |
| `/pcraft blackmarket refresh` | Refreshes black market stock | `phoenixcraft.admin` |
| `/pcraft sell <all/hand>` | Sells crafted items | `phoenixcraft.use` |
| `/pcraft giverecipe <player> <id>` | Gives a recipe to a player | `phoenixcraft.admin` |
| `/pcraft reload` | Reloads plugin configs | `phoenixcraft.admin` |

### Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `phoenixcraft.use` | Everyone | Access to `/pcraft` and its menus |
| `phoenixcraft.admin` | OP | Access to administrative commands & reloads |

---

## ⚙️ Configuration

PhoenixCraft generates the following configuration files in `plugins/PhoenixCraft/`:

* `config.yml` — Main settings and system toggles.
* `messages_en.yml` — English localization strings.
* `messages_tr.yml` — Turkish localization strings.
* `custom-achievements.yml` — Custom achievement definitions.

Every optional system can be independently toggled in `config.yml`:

```yaml
particles:
  enabled: true
stats:
  enabled: true
achievements:
  enabled: true
search:
  enabled: true
filters:
  enabled: true
sort:
  enabled: true
discovery:
  enabled: true
economy:
  enabled: false
recipe-sharing:
  enabled: false
recipe-rental:
  enabled: false
blackmarket:
  enabled: false

```

---

## 💾 Data Files

Runtime data is stored cleanly across dedicated files:

* `stats.yml` — Player crafting statistics
* `achievements.yml` — Unlocked achievements per player
* `learned_recipes.yml` — Learned recipes per player
* `discovered_recipes.yml` — Discovered recipes per player
* `blackmarket.yml` — Black market stock & purchase logs
* `rentals.yml` — Rental offers and active rentals

---

## 🏗️ How It Works

PhoenixCraft cleanly separates UI rendering from business logic by consuming the **PhoenixFish API** via the Bukkit ServicesManager.

```text
┌─────────────────────────────────┐     ┌──────────────────────────────────┐
│      PhoenixFish (required)     │     │   PhoenixCraft (this plugin)     │
│  ─────────────────────────────  │     │  ──────────────────────────────  │
│  • custom_recipes.yml (recipes) │     │  • /pcraft command               │
│  • rods.yml, baits.yml          │     │  • Crafting GUI (6 menus)        │
│  • Crafting logic, level, XP    │     │  • Stats, achievements, discovery│
│  • PhoenixFishAPI (service)     │◄────┤  • Recipe sharing and rental     │
│  • PhoenixFishReloadEvent       │     │  • Black market, selling         │
└─────────────────────────────────┘     └──────────────────────────────────┘
            ▲                                              │
            │          Bukkit ServicesManager              │
            └──────────────────────────────────────────────┘

```

---

## 🔧 Building from Source

```bash
git clone https://github.com/SudoPH/PhoenixCraft.git
cd PhoenixCraft
mvn clean package

```

> **Note:** Building requires the PhoenixFish artifact (`com.Phoenix.Fish:PhoenixFish:1.3.4`) installed in your local Maven repository:
> ```bash
> mvn install:install-file \
>    -Dfile=PhoenixFish-1.3.4.jar \
>    -DgroupId=com.Phoenix.Fish \
>    -DartifactId=PhoenixFish \
>    -Dversion=1.3.4 \
>    -Dpackaging=jar \
>    -DgeneratePom=true
> 
> ```
> 
> 

---

## 🤝 Contributing

* Please open an [issue](https://github.com/SudoPH/PhoenixCraft/issues) for bug reports or feature requests.
* Pull requests are welcome!

---

## 📜 License

PhoenixCraft is open-source software licensed under the **GNU General Public License v3.0**. See [`LICENSE`](https://www.google.com/search?q=LICENSE) for details.

---

**Author:** Sudo ([@SudoPH](https://github.com/SudoPH))

**Version:** 1.0.0

**Status:** 🚀 Released
