---
title: Supported games
summary: Which games the companion can read, how to check your ROM, and what happens with a hack or version PokeDaisy doesn't know.
section: help
order: 1
keywords: [compatibility, compatible, rom hack, hacks, version, sha1, sha-1, checksum, unsupported, best effort, partially supported, request]
---

Any GBA or Game Boy game plays in PokeDaisy. "Supported" is about the companion on the bottom screen: to show your party, bag, map and battles, it has to know exactly which game you're running and where that game keeps its data. A game it doesn't know still plays fine on the top screen; the companion shows a notice instead of your party.

## What's supported

**The original Game Boy Advance games:**

- Pokémon FireRed, LeafGreen, Emerald, Ruby and Sapphire.
- The English (USA / Europe) releases and the Spanish, German, French, Italian and Japanese ones (LeafGreen in German isn't on the list yet), every revision.

**Game Boy:**

- Pokémon Yellow (USA / Europe), drawn in the game's own look.

**ROM hacks**, each in the versions listed:

| Hack | Version |
| --- | --- |
| Pokémon Unbound | 2.1.1.1, and its French translation |
| Pokémon Radical Red | 4.1 |
| Pokémon Gaia | 3.2 |
| Pokémon Odyssey | 4.1.1 |
| Pokémon Heart and Soul | 2.0.6 |
| Pokémon Amethyst | 1.3.0 and 1.4.1 |
| Celia's Stupid Romhack | 1.1.4 |
| Pokémon Orange Islands | Beta 5.7, and an older beta |
| Pokémon Ultra Violet | 1.22 |
| Sword and Shield Ultimate Plus | 1.2.1.2, plus its three patches |
| Pokémon Recharged Yellow | 1.8.0, 1.9.0, 1.9.4 and 1.9.7 |
| Pokémon Lazarus | 2.0 |
| Emerald Seaglass | 3.0 |
| Too Many Types 2 | 1.5.2 |
| Pokémon Glazed | 9.2.0 |
| Emerald Imperium | 1.3.1 |
| Pokémon Emerald Kaizo | 2.0 |
| Pokémon Quetzal | English Alpha 9 v0, Spanish Alpha 9 v0 |
| Super Mariomon | 1.2.1 to 1.3, 1.5.1 and 1.5.2 |
| Emerald Rogue | 2.2.1-EX |
| Pokémon SoulGold | 1.1.4, and three builds of 1.2 |
| Pokémon R.O.W.E. | 2.1.9.1 Experimental |

Not every game gets every tab yet. Some hacks have the guide's live pages only, a few have no guide, and battle panes in many hacks haven't been checked in every kind of battle. The full table, tab by tab, is on the [compatibility page](https://pokedaisy.web.app/compatibility/) and in the [README](https://github.com/lidor30/pokedaisy#supported-games).

## Support is for the exact version

PokeDaisy recognises a game by its file, using the ROM's SHA-1 checksum (a fingerprint of every byte). So "Radical Red" means Radical Red 4.1, the exact file that version's patch produces. An older or newer version of a hack moves things around in memory, and reading it with the wrong map would show wrong names, wrong sprites or nonsense.

That's why the version matters:

- If you patched a hack yourself, make sure you used the right patch on the right base ROM (most hacks want a clean, unmodified US FireRed or Emerald).
- If the hack has a newer version than the one listed, it may not be recognised until it's added. [Best effort](#best-effort-for-games-pokedaisy-doesnt-know) may still read it.

## Check your ROM before you play

The website's [ROM check](https://pokedaisy.web.app/compatibility/) tells you whether a file is supported. Pick the ROM and it runs the same checks the app does, right in your browser. **The ROM never leaves your device**: nothing is uploaded.

In the app, the library tags games the companion can't read as `NOT SUPPORTED`, and ones it reads only in part as `PARTIALLY SUPPORTED`. A game's `INFO` (long-press it in the library) tells you more.

## Best effort for games PokeDaisy doesn't know

When you play a ROM PokeDaisy doesn't recognise, but its header says it's built on FireRed, LeafGreen, Emerald, Ruby or Sapphire, the companion doesn't give up. It tries reading the game as each game it knows, by itself, while you play, and checks what it would show against the ROM: species, move and item names, the map, your party.

What happens next depends on how well a game fits:

- **A clear match.** A different build of a supported game is then fully supported. A hack that shares a supported game's memory gets party, bag, map and battles. Anything that didn't check out (the Pokédex, the guide, item descriptions, sprites) is switched off, so you get no sprite rather than a wrong one. The library tags it `PARTIALLY SUPPORTED`.
- **A likely match before you have a party.** The companion uses its best guess and confirms it once it can read a Pokémon.
- **No clear winner.** The companion asks you (see below).

The match is remembered for that ROM. To undo it, long-press the game in the library, open `INFO` and tap `FORGET MATCH`.

## WHICH GAME IS THIS?

When more than one game could fit, or none fits clearly, the companion shows **WHICH GAME IS THIS?** in place of your party. The likely ones come first, tagged `LIKELY`. Pick the game your ROM is a version of, or the base game the hack was built on.

- If you're not sure, play on: once you have a Pokémon in your party, PokeDaisy checks again by itself.
- `NONE OF THESE` tells it the ROM isn't any of them. The companion then shows its `NOT SUPPORTED` page.
- After you pick, PokeDaisy checks the pick once it can read your party. If it doesn't fit, a `WRONG GAME?` notice lets you `PICK AGAIN` or `KEEP` it.

You can open the picker again any time:

- On the companion: `SETTINGS` > `PLAYS AS` (at the top, in the `GAME` group, only for a ROM read this way).
- In the library: long-press the game, open `INFO` and tap `PICK A GAME`.
- From the match notice: `WRONG GAME?`.

## NOT SUPPORTED and ASK FOR SUPPORT

When the companion can't read a game at all, every tab except `SETTINGS` shows a `NOT SUPPORTED` page. The game itself keeps running normally on the top screen; you lose the companion, not the game.

Want it supported? Tap `ASK FOR SUPPORT`, on that page or in the game's library `INFO`. It opens a GitHub issue form filled in with the file's name, game code, size and SHA-1, so the right version can be found. It never sends the ROM itself.

No GitHub account? The [feedback page](https://pokedaisy.web.app/feedback/) takes game requests too.

> [!TIP]
> Before you ask, run the file through the [ROM check](https://pokedaisy.web.app/compatibility/). If it's a supported hack in a different version, getting the listed version is the quickest fix.

## Sharing a best effort match (optional)

When best effort finds a match for a ROM, the notice may ask **SHARE THIS MATCH?** Sharing helps a future release support that ROM properly. It's entirely up to you: nothing is sent unless you tap `SHARE`, and `NO THANKS` sends nothing.

The notice lists every value it would send, and that's all of it: the ROM's SHA-1, size, game code and revision, the game it was read as and how well it fit, and the app version. No file name, no account and no device details. It goes to PokeDaisy's own database, where it can't be read back by anyone through the app or the site. If you're offline, it's sent the next time the app starts.
