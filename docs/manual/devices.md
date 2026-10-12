---
title: Screens and devices
summary: How PokeDaisy uses two screens, one screen or a phone held upright, and the settings that move the game and companion around.
section: start
order: 3
keywords: [dual screen, two screens, thor, ayn, retroid, duo, anbernic, rg ds, single screen, pocket 6, phone, portrait, landscape, rotate, rotation, tablet, side panel, display, bottom screen, top screen]
---

PokeDaisy is at its best with two screens: the game on top, the companion below. It also works on handhelds with one screen and on phones, where the companion moves beside or under the game instead. This page covers each setup and the settings that go with it.

## Dual-screen handhelds

| Device | How it runs |
| --- | --- |
| AYN Thor | What PokeDaisy was made on. The companion is laid out for its bottom screen. |
| Retroid Pocket Duo Lite | Its bottom screen is squarer (4:3), so a few screens rearrange to fit: the Pokémon summary puts its types beside the icon, and `STATES` uses flatter screenshots. |
| Retroid Pocket Duo | Should work the same way; not yet tried on real hardware. |
| Anbernic RG DS | Should work with `SWAP SCREENS` on (see below); not yet tried on real hardware. |

The game always opens on the device's main display, with the companion on the other one, even if you start it from the bottom screen.

Taps on the bottom screen go to the companion, and the device's `BACK` button works as the companion's back (tap it to close whatever's open there). Hold `BACK` to leave the game.

### SWAP SCREENS

Some devices treat their bottom screen as the main display, which would put the game below and the companion on top. `SWAP SCREENS` trades them, so the game moves to the other screen and the companion takes the main one.

You'll find it in the `SCREEN` group of Settings on the top screen and of the companion's `SETTINGS`. It only shows on a device with a second screen. Flipped from the companion, it swaps right away; flipped from the top-screen Settings, the game picks it up when you go back to it.

### CLOSE COMPANION

Want the bottom screen for something else while you play, like a video or a chat app? At the very bottom of the companion's `SETTINGS`, under `CLOSE GAME` and `RESTART GAME`, tap `CLOSE COMPANION`. The game keeps running and the bottom screen is free for other apps. Press `BACK` on the game to bring the companion back.

It's there on two-screen devices while `SWAP SCREENS` is off.

## Single-screen handhelds

On a handheld with one screen, like the Retroid Pocket 6, the companion is a panel on the right side of the game's screen.

- **Open it:** tap `BACK`, or tap the small tab at the bottom of the screen's right edge. The panel slides in over the game.
- **Close it:** with the panel open, `BACK` works as the companion's back. Once there's nothing left to go back from, the next `BACK` closes the panel. There's also a close tab right outside the panel's bottom-left corner.
- **Lock it beside the game:** tap the padlock tab on the panel's left edge. The game moves over to make room, and both stay on screen as you play. Tap the padlock again and the panel goes back to sliding over the game. A locked panel stays open when you press `BACK` with nothing to go back from, and it comes back the next time you start a game.
- **Resize it:** drag the padlock tab sideways. The panel starts at half the screen, which shows the game at exactly 4x on a 1080p screen. While you drag, it snaps when the game lands close to a whole-number size, so the pixels stay even.

While the panel is locked, the open and close tabs turn see-through so they don't get in the way. The touch controls stay on the game's side whenever the panel is open.

Hold `BACK` to leave the game, as always.

> [!TIP]
> On a controller, an unbound **HOME** or guide button works like a `BACK` tap, so you can open and close the panel without reaching for the screen.

## Phones held upright

Hold a phone upright and the game sits across the top of the screen, with the companion docked below it. It's always open: there's nothing to slide in.

- **Change its height:** drag the grip on the companion's top edge up or down. It snaps to the default size (the shape of the Thor's bottom screen) and to the largest size (everything under the game).
- **Step through sizes:** tap the grip to go from small, to the default, to everything under the game, and round again.
- **Touch controls:** when there's enough room between the game and the companion, the on-screen buttons go in that gap. When there isn't, they lie over the game as usual.

The `COMPANION` setting (in the `SCREEN` group of Settings on single-screen devices) picks where the companion sits on a phone held upright:

- `AT BOTTOM` (the default): along the bottom of the screen, with the touch controls in any gap above it.
- `UNDER GAME`: right under the game, with the touch controls at the bottom of the screen.

![The library on a phone held upright](shots/phone-library.png "The library on a phone")

## Rotating the device

On a device with one screen, PokeDaisy follows the way you hold it. Turn it sideways and you get the side panel; turn it upright and you get the docked companion. It switches as you rotate, mid-game.

With a second screen, PokeDaisy stays in landscape.

## Where things are on each screen

- **Top screen (or your only screen):** the library, Settings, the manual and the game itself.
- **Companion:** the game's tabs and its own `SETTINGS`, which holds the options you're likely to change mid-game. Things that need typing, like a RetroAchievements sign-in or cheat codes, live in the top-screen Settings, since the bottom screen can't host a keyboard well.

[The companion](companion.md) explains the tabs, and the [settings reference](settings-reference.md) lists which setting is on which screen.
