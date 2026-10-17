# Thor Companion: build checklist

Our fork of PokeDaisy (github.com/DonovanSwinthrop/thor-companion). It keeps PokeDaisy's app ID
(`com.pokedaisy.app`) so Cocoon launches it with no setup. Each stage ends with an APK to try on the Thor.

## Stage 0: Get ready
- [x] Check docs: Radical Red (all 4 sheets + text dumps), Imperium docs zip, Unbound boss sets, Ancestral X data
- [ ] Re-download 3 doc zips that were cleaned off the PC (needed for Stage 4, not before):
      Drayano's mods docs (Blaze Black, Renegade Platinum, Sinking Sapphire), Blaze Black 2 Redux 1.4.1, Sterling Silver
- [x] Test Cocoon launching our app (passed on the test device)
- [x] Fork PokeDaisy to your GitHub, keep its app ID
- [x] Build tools: JDK 17, Android SDK 34, NDK 28.2, CMake 3.22, mGBA source
- [x] Build the unmodified app (arm64 for the Thor + x86_64 for the PC test device)
- [x] Run it on the test device with a second screen, Radical Red loaded

## Stage 1: The overlay (Radical Red) - done, ready to try on the Thor
- [x] Copy our overlay (calc, lines, theory craft, bosses, dex, items) into the app (scripts/sync_overlay.sh; kept off GitHub)
- [x] Teach the overlay to take live data from the app (small add to rr-web.js)
- [x] Read Radical Red's memory in the app (port of thor/state.js: party, battle, route, flags, PC boxes) - written, not tested yet
- [x] NUZLOCKE tab on the bottom screen, first in the tab bar, only for Radical Red 4.1; a battle stays on it - written, not tested yet
- [x] Mode switch NUZLOCKE / PLAYTHROUGH in the overlay's settings (tested in a browser on your save)
- [x] App side: tab name follows the mode (NUZLOCKE / PLAYTHROUGH), remembered between launches
- [x] Hide PC-only settings in the app's overlay (transparency, keyboard hotkeys, pausing, close overlay)
      Playthrough = everything, minus "route used" / dead Pokemon / graveyard
- [x] Team helper (both modes): pick up to 3 Pokemon you want to use -> best partners to support them,
      from what you own and what you can catch by now
- [x] Manual page (docs/manual/nuzlocke.md). NEW tag: n/a, the tab has no settings row
- [x] Overlay shows on the NUZLOCKE tab with live game data (fixed: blank page from a WebView height bug)
- [x] Your real save on the test device: Route, Next (Left Guard, cap 47), Team (all 6 + 17 boxed) all read correctly
- [x] Live battle in the app: Rocket Hideout guards double battle read right (foes, items, turn order, damage, 'if both attack'), Lines planner runs
- [x] Opens on the NUZLOCKE tab by default when Radical Red starts
- [x] Don't feed the overlay until the save is loaded (checked: title screen says 'Waiting for the game')
- [x] Matches the PC overlay: same addresses and code; checked against your save (route, next fight, team, battle)
- [x] APK on the drive: E:\Thor_AYN_Roms\Apps\Thor-Companion (PokeDaisy fork).apk, listed in Thor Setup
- [x] Pushed to your fork, branch nuzlocke-tab

## Extras you asked for
- [x] Tab renamed RUN (fits next to BATTLE's chip in a battle)
- [x] Companion BACKGROUND setting on both screens: GAME ART, BLACK, NAVY, PURPLE, TEAL, RAINBOW
- [x] First time you open RUN it asks Nuzlocke or Playthrough; stays until changed in its settings
- [x] Commit + push the extras + Unbound app changes

## Stage 2: More GBA games (PokeDaisy already has their guides; we add calc + bosses)
- [x] Unbound 2.1.1.1 (boss sets on hand)
  - [x] Data check: boss sets x3 difficulties (Dynamic Calc), Unbound dex/moves, PokeDaisy's Unbound ids/maps/wild/boss flags
  - [x] Map what the overlay needs from a game (data.js / savedata.js shapes)
  - [x] Boss teams matched to the game's trainer records: ~87 of ~95 per difficulty (the rest are legendary
        encounters, not trainers). packs/unbound/match_trainers.py -> trainers.json. Recheck Rival 4 (Gible)
  - [x] Wild Pokemon read from the ROM: 59 places, 481 tables (packs/unbound/read_wild.py -> wild.json)
  - [x] Pack assembled (packs/unbound/build_pack.py): 1263/1267 species identified by stats + types, Unbound's own
        stats and moves in the damage engine, 3 difficulties (picker in settings + first open), rival by your starter
  - [x] Overlay loads a pack per game (game-select.js, ub-select.js); each game keeps its own settings
  - [x] App: RUN tab + live feed for Unbound (same memory layout as Radical Red); checked on PokeDaisy's Unbound RAM
        dump (party, map, next fight) and in the app on your ROM
  - [ ] Unbound live battle on a real fight (the battle dump had no foe)
  - [ ] Unbound Dex + Items tabs (needs learnsets / item locations from the ROM; until then hidden, answers at
        current levels instead of trained to the cap)
- [x] Emerald Imperium 1.3.1 (docs zip on hand)
  - [x] Decoded feed: the app sends PokeDaisy's own decoding (party, foes, battlers, flags) for the expansion games,
        since their memory isn't Radical Red's (RrOverlay.decodedState, RrOverlayDecodedTest on the RAM fixtures)
  - [x] Pack (packs/imperium): 132 boss fights from Boss Battles.xlsx (19 caps, rival by your starter's type),
        trainer ids from the ROM, wild by place from the routes sheet, the game's stats / abilities / evolutions
  - [x] Checked in the browser on the Imperium fixtures: Route 101, next fight, team, a live wild battle
- [x] Sword & Shield Ultimate Plus: pack built from the ROM alone (packs/rom_pack.py): 1240/1242 species, the 8 gyms
      in badge order with caps (Milo 20 ... Raihan 48), Hop / Bede / Marnie and 130+ notable trainers, wild by place,
      the hack's own stats and move powers in the damage engine. RUN tab checked in the app on your ROM
  - [ ] Its trainers' natures / EVs (the ROM only stores them for custom sets) and default moves for trainers without
        custom moves (needs the learnset reader)
- [x] SoulGold v1.2 (your ROM is v1.2b)
  - [x] Pack from the ROM (packs/soulgold): Normal + Hard boss teams, Johto gyms (Chuck / Jasmine / Pryce by how
        many of the others you've beaten), League, Kanto, the rival by your starter, Rockets; beaten by badge / Hall
        of Fame flags; wild by place and time of day
  - [x] Checked in the browser on the SoulGold battle fixture: live wild battle with damage, next fight, route
  - [ ] Check it in the app on your ROM and save (and Imperium's)

## Stage 3: Library
- [x] Check what PokeDaisy's library already does: GBA / GB only, 32 MB cap
- [x] `.nds` files listed (up to 512 MB) and opened in the DS player
- [ ] 3DS games: they stay in Azahar (Stage 5)

## Stage 4: DS inside the app
- [x] melonDS core in the app (third_party/melonDS, the core only; free BIOS, no BIOS files needed; JIT on)
- [x] DS player (NdsActivity): top screen on the main display, touch screen on the second; R3 / SELECT + R swaps;
      the game's .sav found and backed up like the GBA saves, written every ~10 s and on leaving
- [x] Checked on the test device: Platinum boots, 3D intro, keys reach the game, full speed (59.9 fps)
- [ ] Try it on the Thor (arm64 JIT)
- [x] Keyboard / quick taps reach the game (a press since the last frame counts), keys let go when the window loses focus
- [x] DS Pokemon reader (NdsMons): party, battlers with live HP, which one is on the field (checked on Black 2 RAM:
      a rival battle, a wild battle with two in the party, a switch); dumps kept on this PC only (they hold game code)
- [x] Load the ROM off the main thread (a 512 MB ROM froze the slow test device long enough for "isn't responding")
- [x] BB2R pack from the ROM (packs/gen5/build_pack.py): 686 species, moves, abilities, items, 63 places' wild tables,
      gyms / Elite Four / Iris with Redux's Normal caps, Hugh's battles by your starter, Colress / Bianca / Ghetsis
- [x] RUN button on the touch screen (bottom right): the RUN tab over the DS touch screen, BACK to return. Checked on
      the test device with Black 2 RAM: live wild battle (damage, turn order), Route 19's table, dupes, met places
- [ ] DS trainer-beaten flags / badge count (Next starts at the first fight until then): needs one dump right after
      beating a single trainer, and one after a gym
- [ ] Packs for plain Black 2, Platinum / Renegade Platinum, HGSS / Sterling Silver (Gen 4 readers: party address,
      battle blocks, zone), Black / Blaze Black
- [x] The DS RUN button no longer translates as "flee" (own label key RUN TAB)
- [x] Plain Black 2 pack (packs/gen5, same builder; caps from its own leaders)
- [x] Black / Blaze Black packs `b1` / `bbk` (packs/gen5, same builder): bosses, caps, rivals, names, zones. Wild tables empty (zone -> wild file link not found); DS player needs IRBO detection first
- [x] Gen 4 packs from the ROM (packs/gen4: gen4rom.py text / NARCs, build_pack.py): Platinum `pt`, Renegade Platinum `rp`,
      HeartGold / SoulSilver `hgss` (bosses by trainer class, rivals by starter, wild tables, zone -> place). Need A's Gen 4
      party / zone reader before the RUN tab can use them. The "Sterling Silver" ROMs on E: are plain SoulSilver (one has a level cap patch only)
- [x] DS pause menu: HEAL PARTY (HP + status, outside a battle; PP not yet) and TIME +1 HOUR (DS clock); heal checked on
      Black 2 RAM (6/18 -> 18/18, the Pokemon still valid)
  - [x] DS pause menu GAME CLOCK row: shows the hour, LEFT / RIGHT step it either way (was TIME +1 HOUR)
  - [ ] DS cheat menu still to do: infinite repel (needs the repel counter's address), auto-run, heal PP
- [x] GBA QUICK MENU HEAL tile: full HP + no status, outside a battle (PartyHeal; not R.O.W.E. / Quetzal layouts)
- [ ] GBA extras still to do: change time (AUTO-RUN and WILD ENCOUNTERS OFF already exist); HEAL to check in a real game
- [ ] Touching the bottom screen on the test device sent keys to the launcher behind it: check on the Thor
- [x] Pause menu (BACK / L3) in the app's look: save / load state, fast forward 3x (or hold R2), swap screens,
      close; checked on the test device (state saved, loaded, game ran on; FF ~2.4x on the slow test device)
- [ ] Blaze Black Reduxx, Blaze Black 2 Redux, Renegade Platinum, Sterling Silver packs

## Stage 5: 3DS
- [ ] Floating button + companion over Azahar's bottom screen
- [ ] Ancestral X, Sinking Sapphire, Rising Ruby, Y Nouveau packs
