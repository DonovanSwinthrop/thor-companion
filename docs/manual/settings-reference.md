---
title: Every setting
summary: A reference of every setting on the top screen's Settings and the companion's SETTINGS tab, with its choices, its default and where to read more.
section: help
order: 2
keywords: [options, preferences, configuration, config, defaults, menu, reference, list]
---

PokeDaisy has two settings screens. **Settings** on the top screen opens from the library (the gear) and has everything, including the things that need a keyboard or a file picker. **SETTINGS** on the companion is the gear tab on the bottom screen, there while you play; changes made there show on the game straight away.

Many rows are on both. They're the same setting, so changing it on one screen changes it on the other. The tables below list each screen's rows in the order the app shows them, with the default in **bold**.

> [!TIP]
> The top screen's Settings has a search box. Type what you're after, even in other emulators' words ("rebind", "box art", "turbo"), and the matching rows show.

## Top screen Settings

### Fast-forward

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `FF SPEED` | `INFINITE`, 1.5x, **2x**, 3x, 4x, 5x, 6x, 8x, 10x | How fast fast-forward runs. `INFINITE` means as fast as the device can go. | [Fast-forward](fast-forward.md) |
| `FF MODE` | **`SMART`**, `NORMAL` | `SMART` drops to normal speed in menus and on the region map, and keeps battles fast. `NORMAL` is fast everywhere. | [Fast-forward](fast-forward.md) |
| `FF MUSIC` | **`STEADY`** (tagged `ALPHA`), `SPED-UP`, `OFF` | What you hear while fast-forwarding: the song at its normal tempo, the game's audio sped up, or silence. | [Fast-forward](fast-forward.md) |
| `REWIND` | `ON`, **`OFF`** | Keeps the last 20 seconds or so, so the `REWIND HOLD` hotkey can play them backwards. Never touches your save file. | [Fast-forward](fast-forward.md) |

### Controls

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `TOUCH PAD` | **`AUTO`**, `ALWAYS`, `NEVER` | The on-screen buttons. `AUTO` shows them until you use a real controller. Applies the next time a game opens. | [Controls](controls.md) |
| `GAME BUTTONS` | a page | Which button presses A, B, L, R, `START` and `SELECT`, plus `START 2`, `SELECT 2`, `TURBO A` and `TURBO B`. Press the button you want on each row. | [Controls](controls.md) |
| `HOTKEYS` | **`ON`**, `OFF` (a page) | Shortcuts for save states, fast-forward, slow motion, rewind, `ZOOM` and leaving the game. Each can be a combination of buttons, or cleared with `CLEAR`. Off, every button goes to the game. | [Controls](controls.md) |

### Screen

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `STATUS BAR` | **`OFF`**, `GAME`, `COMPANION` | A strip with the game, location, money, clock and battery, over the game or over the companion's tabs. | [Screen](screen.md) |
| `ASPECT` | **`ORIGINAL`**, `STRETCH`, `EXPAND` | The GBA's 3:2, stretched to fill the screen, or more of the map drawn past the game's edges. Opens a preview to pick from. | [Screen](screen.md) |
| `SHADERS` | a page (shows the current filter) | `FILTER`, `GRID`, `SMOOTHING`, `GBA COLORS` and `ON COMPANION`; see the next table. | [Screen](screen.md) |
| `OVERLAY` | **`NONE`**, `DAISY`, `BEZEL`, your imports (a page) | A frame around the game. Import your own RetroArch overlays here with `IMPORT FILE`. | [Screen](screen.md) |
| `COMPANION` | **`AT BOTTOM`**, `UNDER GAME` | Only on a device with one screen: where the companion sits when you hold it upright. | [Devices](devices.md) |
| `SWAP SCREENS` | `ON`, **`OFF`** | Only on a device with two screens: the game on the second screen and the companion on the main one. | [Devices](devices.md) |
| `FPS / CPU` | `ON`, **`OFF`** | Frames per second and the app's CPU use in the game's corner. Tap it for more. | [Screen](screen.md) |
| `DARK MODE` | **`OFF`**, `ON`, `AUTO` | Dark windows on both screens. `AUTO` follows the device. | [Screen](screen.md) |
| `THEME` | **`POKéDAISY`**, `FIRERED`, `LEAFGREEN`, `EMERALD`, `RUBY`, `SAPPHIRE`, `PLATINUM`, `HEARTGOLD`, `SOULSILVER` | The backdrop of the library, Settings and setup. Nothing changes in game. | [Screen](screen.md) |

The `SHADERS` page:

| Setting | Choices | What it does |
| --- | --- | --- |
| `FILTER` | **`NONE`**, `LCD`, `LCD PAPER`, `SCANLINES`, `CRT` | The screen effect over the game. Each tap steps to the next one. |
| `GRID` | `SOFT`, **`MEDIUM`**, `STRONG` | How strong the grid is for `LCD` and `LCD PAPER`. Greyed out for the others. |
| `SMOOTHING` | **`OFF`**, `xBRZ`, `xBR`, `SCALEFX` | Smooths the pixel art's edges on the game (never the companion). Shows `N/A` on devices that can't run it. |
| `GBA COLORS` | `ON`, **`OFF`** | The colours as the GBA's own screen showed them. Works with any filter. |
| `ON COMPANION` | **`ON`**, `OFF` | `FILTER` and `GBA COLORS` on the companion and the status bar too (Android 13 and newer). |

### Library

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `RESUME GAMES` | **`ON`**, `OFF` | On, a game opens right where you left it. Off, it starts at the title screen and loads your save. | [Saves](saves.md) |
| `FOLDERS` | a page | Your linked ROMs folder (`CHOOSE FOLDER`, `RESCAN`, `UNLINK`), the folder saves go to (`CHANGE FOLDER`, `USE DEFAULT`), `ALSO LOOK IN` for other emulators' save folders, and where save states are kept. | [Library](library.md), [Saves](saves.md) |
| `COVER ART` | `ON` once a key is saved, else **`OFF`** (a page) | Your own free keys for RetroAchievements box art and SteamGridDB icons (`GET KEY` opens the page to make one). `REPLACE ALL` fetches every cover again. | [Library](library.md) |
| `HIDDEN GAMES` | **`NONE`**, or how many | Games you hid from the library with `HIDE`. Tap one to `SHOW` it again, or `SHOW ALL`. | [Library](library.md) |
| `CHEATS` | `ON`, **`OFF`** (a page) | The master switch for cheat codes, and each game's codes: `ADD CODE`, `IMPORT FILE`, `REMOVE`. | [Cheats](cheats.md) |

### Online

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `RetroAchievements` | your user name, or **`OFF`** (tagged `BETA`) | Sign in to earn achievements (softcore). Signed in means on; `SIGN OUT` turns it off. | [Achievements](achievements.md) |

### App

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `HELP` | a page | This manual, with a search box. It keeps a copy for reading offline. | [Welcome](welcome.md) |
| `LANGUAGE` | **`AUTO`**, `ENGLISH`, `日本語`, `FRANÇAIS`, `DEUTSCH`, `ITALIANO`, `ESPAÑOL` | The app's own text. `AUTO` follows the game's language in game and the device's in the library. Game names (Pokémon, moves, items) stay as the game has them. | [The companion](companion.md) |
| `CRASH REPORTS` | `ON`, **`OFF`** | Sends a short report when the app crashes, only if you turn it on. Only in official builds. | [Troubleshooting](troubleshooting.md#what-does-pokedaisy-send-over-the-internet) |
| `VERSION` | the version number | Tap to look for a newer version now. | [Install](install.md) |
| `LICENSES` | a page | The licenses of PokeDaisy and everything it includes. | |
| `RUN SETUP` | | Back to the library with the first-time setup open again (ROMs folder, saves folder, game buttons, cover art). | [Install](install.md) |

## Companion SETTINGS

The companion's list has the same groups, plus a `COMPANION` group of its own. `CLOSE GAME` and `RESTART GAME` sit at the bottom of the list, and tabs you've taken off the tab bar sit under it as a second row of tabs.

### Game (for an unknown ROM)

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `PLAYS AS` | the game it's read as | Only for a ROM PokeDaisy doesn't know but reads as one it does. Opens **WHICH GAME IS THIS?** to pick another. | [Supported games](supported-games.md#which-game-is-this) |

### Fast-forward

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `FF` | `ON`, **`OFF`** | Fast-forward on or off, like the `FF TOGGLE` hotkey. | [Fast-forward](fast-forward.md) |
| `FF SPEED` | as on the top screen | How fast fast-forward runs. | [Fast-forward](fast-forward.md) |
| `GAME SPEED` | 0.25x, 0.5x, **1x**, 1.5x, 2x, 3x, 4x | A pace that stays, saved for each game. Below 1x the sound is muted. Fast-forward, when on, wins. | [Fast-forward](fast-forward.md) |
| `FF MODE` | as on the top screen | Where fast-forward slows down. | [Fast-forward](fast-forward.md) |
| `FF MUSIC` | as on the top screen | What fast-forward sounds like. | [Fast-forward](fast-forward.md) |
| `REWIND` | as on the top screen | Keep the last seconds to rewind. | [Fast-forward](fast-forward.md) |

### Controls

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `TOUCH PAD` | as on the top screen | The on-screen buttons. | [Controls](controls.md) |
| `GAME BUTTONS` | a page | Rebind a button by tapping it, then tapping a key's name. | [Controls](controls.md) |
| `HOTKEYS` | a page | Turn hotkeys on or off and bind each to one key (`NONE` unbinds). Button combinations are set on the top screen. | [Controls](controls.md) |

### Companion

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `LANGUAGE` | as on the top screen | The app's own text, on both screens at once. | [The companion](companion.md) |
| `TAB BAR` | **5 tabs** (a page) | Which tabs sit next to the gear: up to 5, `SHOWN` or `HIDDEN`. By default `PARTY`, `DEX`, `MAP`, `BAG` and `GUIDE`. The rest open from `SETTINGS`. | [The companion](companion.md) |
| `BATTLE HINTS` | **`ON`**, `OFF` | Move ratings, the foe's weaknesses and suggestions during battles. | [Battle](battle.md) |
| `ONE-TAP MOVES` | `ON`, **`OFF`** | Puts your moves on the battle's first menu, so one tap picks FIGHT and the move. | [Battle](battle.md) |
| `FOE IVS` | `ON`, **`OFF`** | A STATS page in battle with the foe's IVs, EVs and nature. | [Battle](battle.md) |
| `CLICK SOUND` | **`ON`**, `OFF` | The game's own menu click on every companion button. | [The companion](companion.md) |
| `TWEAKS` | a page, each **`ON`** | `PARTY ICONS MOVE`, `MAP CURSOR BLINK`, `TAB ANIMATIONS` and `JUMP TO BATTLE` (a battle opens the battle tab by itself). | [The companion](companion.md) |
| `HELP` | a page | This manual, without the search box (the bottom screen has no keyboard). | [Welcome](welcome.md) |

### Screen

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `STATUS BAR` | as on the top screen | The strip with game, location, money, clock and battery. | [Screen](screen.md) |
| `ASPECT` | as on the top screen | A pick-list here. `EXPAND` is tagged `NOT YET` in a game that doesn't have it. | [Screen](screen.md) |
| `SHADERS` | a page | The same five rows as on the top screen; the game changes as you tap. | [Screen](screen.md) |
| `OVERLAY` | as on the top screen | Pick a frame. Importing happens on the top screen. | [Screen](screen.md) |
| `FPS / CPU` | as on the top screen | The performance readout. | [Screen](screen.md) |
| `DARK MODE` | as on the top screen | Dark windows on both screens. | [Screen](screen.md) |
| `COMPANION` | as on the top screen | Only on a phone held upright. | [Devices](devices.md) |
| `SWAP SCREENS` | as on the top screen | Only with two screens. | [Devices](devices.md) |

### Game

| Setting | Choices | What it does | More |
| --- | --- | --- | --- |
| `CHEATS` | **`OFF`**, or how many are on (a page) | The master switch, then this game's cheats, each flipped live. Codes are added on the top screen. | [Cheats](cheats.md) |

### Buttons at the bottom

| Button | What it does |
| --- | --- |
| `CLOSE GAME` | Back to the library. With `RESUME GAMES` on, your place is kept; off, anything since your last in-game save is lost. |
| `RESTART GAME` | Reboots the game from its save file. Unsaved progress is lost. |
| `CLOSE COMPANION` | On two screens: frees the bottom screen for other apps while the game keeps running. Press BACK on the game to bring the companion back. |

See [The companion](companion.md) for the tabs themselves.
