<div align="center">
  <h1 align="center">⚔️ EcoTaleRewards</h1>
  <p align="center">
    <strong>Daily Login Calendar · Streak System · Return Rewards</strong><br>
    MMORPG-style player retention mechanics for Hytale servers
  </p>

![Hytale Server Mod](https://img.shields.io/badge/Hytale-Server%20Mod-0ea5e9?style=for-the-badge)
![Version](https://img.shields.io/badge/version-1.1.1-10b981?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-17+-f97316?style=for-the-badge&logo=openjdk&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-a855f7?style=for-the-badge)
![Ecotale](https://img.shields.io/badge/Ecotale-1.0.7-6366f1?style=for-the-badge)

  <br>
  
  </div>
  
---

## Overview

EcoTaleRewards is a Hytale server plugin that drives player retention through a 30-day login calendar, streak-based multipliers, and return rewards for inactive players. Every aspect — from reward values to anti-abuse rules — is fully configurable via JSON or the in-game Admin GUI.

## Features

<table>
<tr>
<td width="50%">

### 📅 30-Day Login Calendar
- Color-coded GUI (claimed / available / locked / missed)
- Per-day rewards: coins, XP, items, commands
- Weekly milestone days with bonus loot
- Strict or soft mode with configurable grace period
- Auto-opens 3 seconds after player joins

</td>
<td width="50%">

### 🔥 Streak System
- Consecutive login tracking with rising multiplier
- Base 1.0 + 0.02/day (max 3.0×)
- Milestones at 7 / 14 / 30 / 60 / 90 days
- Partial reset (÷2) instead of full wipe on break
- Bonus coins, XP, and multipliers at each milestone

</td>
</tr>
<tr>
<td>

### 🎁 Return Rewards
- 4 tiers: 1 week, 2 weeks, 1 month, 3+ months
- Escalating coin and XP packages
- Custom items and commands per tier
- Special banner in calendar GUI

</td>
<td>

### 🛡️ Anti-Abuse & VIP
- Minimum online time, relog cooldown, daily claim limit
- Full reward audit logging
- Permission-based VIP multipliers (VIP / MVP / MVP+)
- Unlimited custom VIP tiers

</td>
</tr>
</table>

### Additional Features

- 🌐 **Multi-language** — English & Russian built-in, per-player switching
- ⚙️ **Admin GUI** — live config editing, calendar editor, slider controls
- 💾 **JSON storage** — per-player files, auto-save every 5 minutes
- 🔌 **Soft dependencies** — works with or without EcotaleAPI / RPG Leveling

## Architecture

```
EcoTaleRewards/
├── src/main/java/com/crystalrealm/ecotalerewards/
│   ├── EcoTaleRewardsPlugin.java      # Plugin lifecycle & event registration
│   ├── calendar/
│   │   └── CalendarService.java       # 30-day calendar logic
│   ├── commands/
│   │   └── RewardsCommandCollection.java  # /rewards command tree
│   ├── config/
│   │   ├── ConfigManager.java         # JSON config loading/saving
│   │   └── RewardsConfig.java         # Configuration POJO
│   ├── gui/
│   │   ├── RewardsCalendarGui.java    # Player-facing calendar GUI
│   │   └── AdminGui.java             # Admin configuration GUI
│   ├── lang/
│   │   └── LangManager.java          # i18n with MiniMessage support
│   ├── model/                         # Data models (RewardDay, StreakMilestone, etc.)
│   ├── returns/
│   │   └── ReturnRewardService.java   # Absence-based return rewards
│   ├── rewards/
│   │   └── RewardService.java         # Central reward distribution
│   ├── storage/
│   │   └── JsonRewardStorage.java     # Per-player JSON persistence
│   ├── streaks/
│   │   └── StreakService.java         # Consecutive login tracking
│   └── util/
│       ├── AntiAbuseGuard.java        # Abuse prevention checks
│       └── PluginLogger.java          # Structured logging
├── src/main/resources/
│   ├── manifest.json                  # Hytale plugin manifest
│   └── lang/
│       ├── en.json                    # English messages
│       └── ru.json                    # Russian messages
└── src/stubs/java/                    # Hytale API compile-time stubs
```

## Commands

| Command | Permission | Description |
|:--------|:-----------|:------------|
| `/rewards` | `ecotalerewards.use` | Open the reward calendar GUI |
| `/rewards calendar` | `ecotalerewards.use` | Open the reward calendar GUI |
| `/rewards claim` | `ecotalerewards.use` | Claim today's reward (CLI) |
| `/rewards info` | `ecotalerewards.use` | View your progress & stats |
| `/rewards admin` | `ecotalerewards.admin` | Open the admin GUI |
| `/rewards reload` | `ecotalerewards.admin` | Reload configuration |
| `/rewards reset <uuid>` | `ecotalerewards.admin` | Reset a player's data |
| `/rewards langen` | — | Switch to English |
| `/rewards langru` | — | Switch to Russian |
| `/rewards help` | — | Command reference |

## Permissions

| Permission | Description | Default |
|:-----------|:------------|:--------|
| `ecotalerewards.use` | Calendar, claim, and info access | All players |
| `ecotalerewards.admin` | Admin GUI, reload, reset | OP |
| `ecotalerewards.vip.vip` | 1.25× reward multiplier | — |
| `ecotalerewards.vip.mvp` | 1.5× reward multiplier | — |
| `ecotalerewards.vip.mvp_plus` | 2.0× reward multiplier | — |

## Dependencies

| Plugin | Type | Purpose |
|:-------|:-----|:--------|
| [Ecotale](https://hytale-server.pro-gamedev.ru) ≥1.0.0 | **Required** | Core server plugin |
| EcotaleAPI | Optional | Economy integration (coin deposits) |
| RPG Leveling | Optional | XP system integration |

Both optional APIs are accessed via reflection — the plugin runs without them.

## Installation

```bash
# 1. Copy the JAR to your mods folder
cp EcoTaleRewards-1.1.2.jar <server>/Mods/

# 2. Start the server — default config auto-generates
# 3. Edit the generated EcoTaleRewards.json
# 4. /rewards reload — or use the Admin GUI
```

## Configuration

<details>
<summary><strong>📋 Calendar Day Example</strong></summary>

```json
{
  "1": {
    "Coins": 50.0,
    "XP": 25,
    "Items": [
      "Weapon_Sword_Bronze:1",
      "Tool_Pickaxe_Adamantite:1"
    ],
    "Commands": [],
    "Description": "Day 1 — Welcome!"
  }
}
```
</details>

<details>
<summary><strong>🔥 Streak Milestone Example</strong></summary>

```json
{
  "7": {
    "BonusCoins": 200.0,
    "BonusXP": 100,
    "RewardMultiplier": 1.25,
    "Commands": [],
    "Description": "7-day streak!"
  }
}
```
</details>

<details>
<summary><strong>🎁 Return Reward Tier Example</strong></summary>

```json
{
  "MinAbsenceDays": 30,
  "MaxAbsenceDays": 89,
  "Coins": 2000.0,
  "XP": 1000,
  "Items": [],
  "Commands": [],
  "Description": "Grand return! (1 month)"
}
```
</details>

<details>
<summary><strong>👑 VIP Tiers</strong></summary>

```json
[
  { "Permission": "ecotalerewards.vip.mvp_plus", "Multiplier": 2.0, "DisplayName": "MVP+" },
  { "Permission": "ecotalerewards.vip.mvp",      "Multiplier": 1.5, "DisplayName": "MVP" },
  { "Permission": "ecotalerewards.vip.vip",       "Multiplier": 1.25, "DisplayName": "VIP" }
]
```
</details>

### Item Format

Items use Hytale's internal PascalCase IDs:
```
Weapon_Sword_Bronze:1
Tool_Pickaxe_Adamantite:1
Plant_Crop_Wheat_Item:16
Ore_Cobalt:3
```

### Command Placeholders

| Placeholder | Replaced With |
|:------------|:--------------|
| `{player}` | Player's username |

## Building from Source

```bash
# Requires Java 17+ and Gradle
./gradlew build

# Output: build/libs/EcoTaleRewards-1.1.2.jar
```

## Changelog

### v1.1.2
- **Fix:** GUI not opening for users with LuckPerms — `openGuiForSender` now resolves Player via reflection fallback (`getPlayer()`, `getHandle()`) when direct `instanceof Player` cast fails due to permission plugin wrappers
- **Fix:** All silent GUI failure points now send error messages to the player instead of failing silently
- **Improved:** Detailed logging at every failure point (sender class name, ref validity, component type) for easier debugging

### v1.1.1
- **Fix:** `/rewards langen`, `/rewards langru`, `/rewards help` now require `ecotalerewards.use` permission (previously accessible without any permission)
- **Fix:** `/rewards reset` and `/rewards reload` now work from server console (previously blocked by `isPlayer()` check)
- **Improved:** VIP permission check in `RewardService` now uses direct `CommandSender` cast instead of reflection, with reflection fallback for non-standard callers
- **Improved:** VIP check failures now logged at WARN level instead of silently swallowed at DEBUG

### v1.1.0
- Initial public release

## Technical Details

- **Hytale ECS integration** — accesses Player entity via `Store.getComponent(Ref, ComponentType)` for direct inventory manipulation
- **Item delivery** — uses native `Inventory.getCombinedHotbarFirst().addItemStack()` (same pattern as BetterBattlePass)
- **Command execution** — runs commands via `CommandManager.handleCommand(ConsoleSender.INSTANCE, cmd)`
- **Thread-safe** — `ConcurrentHashMap` for player data, world-thread execution for ECS operations
- **Stub compilation** — compiles against API stubs, runtime uses real Hytale classes

---

<p align="center">
  <strong>Made by CrystalRealm</strong><br>
  <a href="https://hytale-server.pro-gamedev.ru">hytale-server.pro-gamedev.ru</a>
</p>
