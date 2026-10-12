---
title: Bag
summary: Every pocket of your bag with icons and descriptions, which Pokémon can use a TM, and using an item from the bottom screen.
section: companion
order: 3
keywords: [items, inventory, cube, pockets, tm, hm, description, sort, use item, berries, key items]
---

The bag tab shows everything you're carrying, pocket by pocket, as the game has it right now. It's handy for a quick count before a long cave, or for reading what an item does without digging through the game's menus.

The tab's name follows the game: `BAG` in most games, `CUBE` in Unbound, `INVENTORY` in R.O.W.E. and `POCKETS` in Super Mariomon. The screen itself is drawn like that game's bag.

## Pockets and sorting

The pockets run down the left like folder tabs. `ALL` shows everything at once; the others are the game's own pockets (items, balls, TMs and HMs, berries, key items, and whatever extra pockets a hack adds). Only pockets with something in them get a tab.

Each row has the item's name and how many you have. TMs and HMs show the move they teach after their number, the way the game's TM case does.

The button under the pockets changes the order. Each tap moves to the next: the game's own order, by name, by type, or by count (most first).

![The bag tab with its pocket tabs on the left and the item list on the right](shots/companion-bag.png "The bag tab")

## Item details

Tap an item to open its description at the bottom of the screen, with the item's icon. Tap another item to switch, or the same one to close it.

![SODA SPRAY selected in the bag, its icon and description in a box along the bottom](shots/companion-bag-item.png "An item's description")

Descriptions are read from your own ROM, in your game's words and language. That means a hack's changed items describe themselves correctly. If an item has no text here, the game's description couldn't be found in that ROM.

## TMs: who can learn it

Pick a TM or HM and the description box also shows your whole party, each Pokémon's icon tagged:

- `ABLE`: it can learn this move.
- `NOT ABLE`: it can't.
- `LEARNED`: it already knows it.
- `BEST`: of the ones that can, the Pokémon it suits most.

Under the icons, a line names the best fit and, if its moves are full, which move it should forget for this one. The reasoning is the same as the `LEARN` page in a Pokémon's summary (see [Party and summaries](party.md)).

`BEST` and the forget suggestion follow `BATTLE HINTS` in SETTINGS: with hints off you still see who's `ABLE`, without the recommendation.

> [!NOTE]
> The TM party row works in FireRed, LeafGreen, Ruby, Sapphire and Emerald (the original games) so far.

## Using an item from the companion

In the English FireRed (rev 1) and Emerald, the description box of an item that keeps wild Pokémon away has a `USE` button. Tap it and the item is used as if you'd picked it from the game's bag: one leaves your bag, and the game counts the steps down as usual.

It only works while you're walking around. If a menu, a conversation or a battle is open, it tells you so (close the game's menus first). If the last one is still working, it says that too, and nothing is used.

> [!TIP]
> Other items and other games don't have `USE` yet. For everything else, use the item in the game as normal; the bag tab updates by itself.
