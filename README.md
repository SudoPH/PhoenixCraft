# PhoenixCraft

> **Categorized crafting GUI for PhoenixFish — fishing rod & bait crafting system.**
>
> ⚠️ **This plugin is in EARLY STAGE.** The core crafting pipeline works, but several features are incomplete and the project is pre-release. Please open an issue if you spot a bug.

---

## 📋 What is it?

PhoenixCraft is a standalone **crafting GUI module** for the PhoenixFish fishing plugin. Players craft custom fishing rods and baits through a categorized menu, gated by a crafting-level progression system.

- **Rods**: 8-tier progression (Hardwood → Iron → Gold → Diamond → Netherite → Ocean → Legendary → Cosmic)
- **Baits**: 13 baits ranging from Common to Legendary (worm, bread_crust, minnow_bait, seaweed_wrap, glowing_bait, bug_paste, enchanted_dough, bloodied_flesh, spider_silk, ocean_essence, golden_roe, void_leech, lunar_moth)
- **Crafting Level / XP system**: every craft awards XP, levels unlock higher-tier recipes
- **Strict separation of concerns**: PhoenixCraft only renders the GUI and forwards clicks — all data and game logic live in PhoenixFish

---

## 📦 Installation

> 🚧 **Installation is currently unavailable.**
>
> PhoenixCraft `0.0.1` has not been published yet. The first release will depend on **PhoenixFish 1.3.1**, which is also not yet published. Once both versions are out, the installation steps below will apply.

### Requirements (once released)

- **Paper 1.21+** (Java 21)
- **PhoenixFish 1.3.1+** — hard dependency (`depend: [PhoenixFish]` in `plugin.yml`)

### Planned steps

1. Drop `PhoenixFish-1.3.1.jar` into `plugins/`
2. Drop `PhoenixCraft-0.0.1.jar` into `plugins/`
3. Start the server
4. Expected console output:

```
[PhoenixFish] PhoenixFish başarıyla etkinleştirildi!
[PhoenixCraft] PhoenixCraft v0.0.1 etkinleştirildi.
[PhoenixCraft] Connected to PhoenixFishAPI: com.phoenix.fish.api.PhoenixFishAPI
```

5. Join the game and run `/pcraft`

---

## 🎮 Usage

### Commands

| Command | Description |
|---|---|
| `/pcraft` | Opens the main menu (alias: `/pc`) |
| `/pcraft rods` | Jumps straight to the rods category |
| `/pcraft baits` | Jumps straight to the baits category |
| `/pcraft help` | Shows help text |

### Permissions

| Permission | Default | Description |
|---|---|---|
| `phoenixcraft.use` | everyone | Use the `/pcraft` command |
| `phoenixcraft.admin` | op | Administrative subcommands |

### GUI Flow

```
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

### Crafting Flow

1. On the detail screen, click the green **CRAFT** button
2. Required materials are consumed from your inventory automatically
3. The crafted item is added to your inventory (overflow drops to the ground)
4. Crafting XP is awarded; level-ups fire automatically

---

## 🏗️ Architecture

```
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

**Design principle**: PhoenixCraft **only renders the GUI and forwards interactions**. All game logic (recipe loading, material checks, crafting, XP/level) stays in PhoenixFish. This means:

- ✅ Single source of truth
- ✅ Database / cache in one place
- ✅ Single reload command (`/pf reload`)
- ✅ Other plugins can reuse the same API

---

## 📁 Project Structure

```
PhoenixCraft/
├── pom.xml
└── src/main/
    ├── java/com/phoenix/craft/
    │   ├── PhoenixCraft.java          # Main class
    │   ├── command/
    │   │   ├── CraftCommand.java      # /pcraft executor
    │   │   └── CraftTabCompleter.java # Tab completer
    │   ├── gui/
    │   │   ├── CraftingGUIHolder.java # InventoryHolder marker
    │   │   └── CraftingGUIManager.java# Screen builder + craft logic
    │   └── listener/
    │       └── CraftingListener.java  # InventoryClick/Drag events
    └── resources/
        └── plugin.yml
```

---

## ⚠️ Early Stage Notice

This plugin is **under active development** and should be thoroughly tested before any production use.

### Known Missing Features

- ❌ No shift-click bulk crafting (one craft per click)
- ❌ No "contents" hover tooltip on detail screen
- ❌ Minimal sound design (success/fail only)
- ❌ Localization incomplete (some GUI text is hardcoded Turkish)
- ❌ No config file (GUI appearance is hardcoded)

### Known Risks

- Material consumption iterates `Inventory.getContents()` in slot order — the slot the player expects may differ
- Very rapid clicks can race the GUI refresh
- Crafting level-up currently fires as a chat message only (no title)

### Untested Scenarios

- Concurrent crafts by multiple players
- Open GUIs during a `/pf reload`
- Server start without PhoenixFish present (expected: clean disable)

---

## 🔧 Development

### Build from source

```bash
git clone <repo-url>
cd PhoenixCraft
mvn clean package
# → target/PhoenixCraft-0.0.1.jar
```

> The build requires the PhoenixFish artifact (`com.Phoenix.Fish:PhoenixFish:1.3.1`) in your local Maven repository. Install it first with:
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

## 🤝 Contributing

- Open an issue for bug reports or feature requests
- Pull requests welcome — for major changes, please open an issue first

---

## 📚 Related Projects

- **PhoenixFish** — the parent fishing plugin (required dependency)

---

**Author**: Sudo  
**Version**: 0.0.1  
**Status**: 🚧 Early Stage / Pre-release
