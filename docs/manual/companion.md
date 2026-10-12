---
title: The companion at a glance
summary: What the bottom screen shows, how the tab bar works, and what the companion's SETTINGS tab holds.
section: companion
order: 1
keywords: [bottom screen, second screen, tabs, tab bar, overview, back button, click, sound, language, translation, close, restart, reset, gear]
---

The companion is everything on the bottom screen while a game runs. It reads your game as you play, so your party, bag, location and battles are always up to date without opening a single menu on the top screen. It's drawn in the game's own menu style: FireRed's party screen in FireRed, Emerald's in Emerald, and each supported hack's own look where it has one.

You don't have to do anything to start it. Open a supported game and the companion fills in once the game has loaded. On a device with only one screen it lives in a panel beside the game instead (see [Devices](devices.md)).

## The tabs

Along the bottom of the companion is a row of tabs, with the SETTINGS gear at the right end. Out of the box you get:

| Tab | What it shows |
| --- | --- |
| `PARTY` | Your team, and a full summary for each Pokémon. See [Party and summaries](party.md). |
| `DEX` | Your Pokédex with seen and caught marks. See [Pokédex](dex.md). |
| `MAP` | The region map with you on it. See [Map](map.md). |
| `BAG` | Every pocket of your bag. See [Bag](bag.md). |
| `GUIDE` | Hints, the next boss and what's around you. See [Guide](guide.md). |

A few more are off the bar to begin with: `CARD` (your [trainer card](trainer-card.md)), `STATES` (save states) and `CHEEVOS` ([RetroAchievements](achievements.md)).

Some tabs only appear when the game has what they need. `DEX` needs the game's Pokédex data and `GUIDE` needs a guide for that game, so a game without them has fewer tabs. The bag tab also takes the game's own word for it: `CUBE` in Unbound, `INVENTORY` in R.O.W.E., `POCKETS` in Super Mariomon, `BAG` everywhere else.

### Choosing your tabs

Up to five tabs fit next to the gear. To pick them, open SETTINGS and tap `TAB BAR` in the COMPANION group. Each tab there says `SHOWN` or `HIDDEN`; tap one to flip it. The title counts how many you've placed (for example `5/5`). If the bar is full, hide one first. Tabs always sit in the same order on the bar, whichever you pick.

Your choice is shared by every game. A tab the running game doesn't have shows `N/A` and keeps its setting for the games that do.

![The TAB BAR page in the companion's SETTINGS, listing each tab as SHOWN or HIDDEN](shots/companion-settings-tabs.png "SETTINGS > TAB BAR")

### Tabs that aren't on the bar

Hidden tabs are still one tap away. At the bottom of SETTINGS, under the list of options, there's a second row of tab chips: every tab you left off the bar. Tap one to open it. While it's open the gear stays lit, and BACK takes you back to SETTINGS.

### The BATTLE tab

When a battle starts, a `BATTLE` tab appears at the front of the bar and opens by itself. To make room, the last of your chosen tabs steps aside until the battle ends. When the battle is over, the tab you had open before comes back. Everything about battles is on the [Battles](battle.md) page.

If you'd rather stay where you are when a battle starts, turn off `JUMP TO BATTLE` in TWEAKS (below): the `BATTLE` tab still appears, but doesn't open on its own.

## The BACK button

The device's BACK button works for the companion, from either screen:

- **Tap BACK** to go back one step in the companion: close a summary, a pick-list, a pop-up window or a sub-page of SETTINGS, or return from a tab you opened from SETTINGS.
- **Hold BACK** to leave the game.

On a controller, the HOME or guide button does the same as a BACK tap unless you've bound it to something else.

## Button sounds

Companion buttons play your game's own menu click, recorded from the ROM itself, so they sound like the game. Buttons that press the game's buttons for you (the battle buttons, for example) stay quiet, because the game makes its own sound. There's no vibration. To turn the clicks off, switch `CLICK SOUND` to `OFF` in SETTINGS.

## The SETTINGS tab

The gear opens the companion's SETTINGS, laid out like FireRed's OPTION screen. The title shows the game's name and your battery. Tap a row to change it: on / off rows flip, and rows with more choices open a short list to pick from. SETTINGS remembers where you were, so a trip to another tab and back lands on the same row.

![The companion's SETTINGS tab, with the FAST-FORWARD and CONTROLS groups at the top](shots/companion-settings.png "The companion's SETTINGS")

The options come in groups:

- **FAST-FORWARD**: `FF`, `FF SPEED`, `GAME SPEED`, `FF MODE`, `FF MUSIC` and `REWIND`. See [Fast-forward](fast-forward.md).
- **CONTROLS**: `TOUCH PAD`, `GAME BUTTONS` and `HOTKEYS`. See [Controls](controls.md).
- **COMPANION**: `LANGUAGE`, `TAB BAR`, `BATTLE HINTS`, `ONE-TAP MOVES`, `FOE IVS`, `CLICK SOUND`, `TWEAKS` and `HELP` (this manual).
- **SCREEN**: `STATUS BAR`, `ASPECT`, `SHADERS`, `OVERLAY`, `FPS / CPU`, `DARK MODE` and, with two screens, `SWAP SCREENS`. See [Screen and display](screen.md).
- **GAME**: `CHEATS`. See [Cheats](cheats.md).

Every option is explained in the [Settings reference](settings-reference.md). The top screen has its own Settings too, with more options and a search box.

### TWEAKS

`TWEAKS` holds small on / off preferences, all on to start with:

| Tweak | What it does |
| --- | --- |
| `PARTY ICONS MOVE` | Party icons bounce, like in the game. |
| `MAP CURSOR BLINK` | The MAP cursor pulses between two sizes. |
| `TAB ANIMATIONS` | Tabs slide in. Off, they switch at once. |
| `JUMP TO BATTLE` | A battle opens the `BATTLE` tab, then goes back to where you were. |

![The TWEAKS page, with its four switches and a line under each](shots/companion-settings-tweaks.png "SETTINGS > TWEAKS")

### LANGUAGE

The app's own text comes in English, Japanese, French, German, Italian and Spanish. `LANGUAGE` starts on `AUTO`, which follows the game's language; pick another and both screens switch at once.

This only changes PokeDaisy's words (buttons, labels, hints). Pokémon, move and item names stay as your game has them, so a French game shows its French names whatever you pick, and an English game keeps its English ones.

## Closing and restarting

At the very end of SETTINGS, under a line, are two buttons:

- `CLOSE GAME` returns to your library. With `RESUME GAMES` on (top-screen Settings, the default), the game picks up where you left it next time. With it off, anything since your last in-game save is lost, and the confirmation says so.
- `RESTART GAME` reboots the game and reads its save file again from storage. Anything you haven't saved in the game is lost. It's the fix when a save you copied in doesn't show up.

Both ask before they do anything.

On a device with two screens there's also `CLOSE COMPANION`. It hands the bottom screen back to Android while the game keeps running, so you can use another app down there. Press BACK on the game to bring the companion back.

> [!TIP]
> Not sure what a row does? `HELP` in the COMPANION group opens this manual right on the bottom screen.
