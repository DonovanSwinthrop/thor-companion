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
  - [x] Unbound Items tab (843 item balls / hidden items / gifts from the ROM's map data; a search, no "by now" yet) and Dex tab (stats, abilities,
        egg groups, evolutions, level-up moves from the ROM; checked in a browser)
  - [x] Unbound Dex TMs and egg moves from the ROM: TM list 0x09A42B2A (TM01-120 + HM01-08), compatibility 0x09650484 (16-byte bit field
        per species), egg moves 0x099F1374 (20000 + species markers); checked in a browser (Charizard 58 TMs incl. Fly, 16 egg moves)
  - [ ] Unbound Dex: tutor moves (not stored as the usual bit field; a 64-move tutor list sits at 0x082AB0B0), move accuracy / PP /
        descriptions, ability descriptions; "by now" for Items
- [x] Emerald Imperium 1.3.1 (docs zip on hand)
  - [x] Decoded feed: the app sends PokeDaisy's own decoding (party, foes, battlers, flags) for the expansion games,
        since their memory isn't Radical Red's (RrOverlay.decodedState, RrOverlayDecodedTest on the RAM fixtures)
  - [x] Pack (packs/imperium): 132 boss fights from Boss Battles.xlsx (19 caps, rival by your starter's type),
        trainer ids from the ROM, wild by place from the routes sheet, the game's stats / abilities / evolutions
  - [x] Checked in the browser on the Imperium fixtures: Route 101, next fight, team, a live wild battle
- [x] Sword & Shield Ultimate Plus: pack built from the ROM alone (packs/rom_pack.py): 1240/1242 species, the 8 gyms
      in badge order with caps (Milo 20 ... Raihan 48), Hop / Bede / Marnie and 130+ notable trainers, wild by place,
      the hack's own stats and move powers in the damage engine. RUN tab checked in the app on your ROM
  - [x] Its trainers' natures / EVs: not in the ROM (records hold IV, level, species, item, moves only); needs the hack's own docs
  - [ ] Default moves for trainers without custom moves (needs the learnset reader; Unbound's reader exists now)
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
- [x] DS trainer-beaten flags / badge count for Black 2 / BB2R (see below); [ ] still to do for Black / Blaze Black and Gen 4
- [x] DS player reads the party and map in Black, Blaze Black (party 0x022349B4, zone +0x6FC; zone seen only at the Gear Station),
      Platinum / Renegade Platinum (save pointer 0x02101D40) and HeartGold / SoulSilver (0x02111880); RUN tab pages checked
      on all five (screenshots in thor-companion-SCREENS); Gen 4 battles in the RUN tab: Claude A's commit 3f2e73a, checked
      on a wild Platinum battle
- [ ] Sterling Silver pack (both ROMs on E: are plain SoulSilver)
- [x] The DS RUN button no longer translates as "flee" (own label key RUN TAB)
- [x] Plain Black 2 pack (packs/gen5, same builder; caps from its own leaders)
- [x] Black / Blaze Black packs `b1` / `bbk` (packs/gen5, same builder): bosses, caps, rivals, names, zones, wild tables (BW1's zone table is Black 2's: wild +0x14, place +0x1A; Route 1 = zone 317). DS player needs IRBO detection first
- [x] Gen 4 packs from the ROM (packs/gen4: gen4rom.py text / NARCs, build_pack.py): Platinum `pt`, Renegade Platinum `rp`,
      HeartGold / SoulSilver `hgss` (bosses by trainer class, rivals by starter, wild tables, zone -> place). Need A's Gen 4
      party / zone reader before the RUN tab can use them. The "Sterling Silver" ROMs on E: are plain SoulSilver (one has a level cap patch only)
- [x] DS pause menu: HEAL PARTY (HP + status + PP, outside a battle; PP from the ROM's own move table) and TIME +1 HOUR (DS clock); heal checked on
      Black 2 RAM (6/18 -> 18/18, the Pokemon still valid)
  - [x] DS pause menu GAME CLOCK row: shows the hour, LEFT / RIGHT step it either way (was TIME +1 HOUR)
  - [x] DS BATTLE SHORTCUTS: R runs from wild battles (taps the game's RUN), L = best ball, both checked in Black 2 on the emulator
  - [x] Black 2 / BB2R RUN tab Next follows the game: trainer flags (1488 + id, 0x02225846) and gym leaders by badge (0x022266E8)
  - [x] DS INFINITE REPEL (Black 2 / BB2R), checked in the emulator; auto-run parked (no reliable field flag)
  - [x] DS NATURE: change a party Pokemon's nature, stats recomputed: Black, Blaze Black, Black 2, BB2R (nature byte), Platinum,
        Renegade Platinum, HeartGold, SoulSilver (new PID keeping gender / ability / shininess); checked in the game's own summary
        on Platinum and SoulSilver
  - [x] DS HEAL PARTY waits for Gen 4 battles too (NdsFeed.inBattle)
  - [x] Gen 4 gym badges in the feed (Platinum base+0x96, HGSS Johto base+0x8E; Claude A) and doneFlags for the gym leaders in the pt / rp / hgss packs
        (badge n = flag 3072 + n; HGSS in game order, Jasmine before Pryce): the RUN tab's Next follows the badges (checked in a browser with fed
        badge bytes: Platinum Roark -> Gardenia -> Fantina ... -> Elite Four; SoulSilver Falkner ... Jasmine, Pryce, Clair, Janine)
  - [x] DS battle shortcuts in Platinum / Renegade Platinum (R run, L best ball with the bag resolved from RAM; Claude A + B)
  - [x] INFINITE REPEL blocks every wild encounter (Platinum, Black / White, HGSS and hacks: NdsEncounters), Platinum's step counter by offset;
        live on Platinum: ON = no battle, OFF = the counter cleared and a battle within ~25 steps
  - [x] DS LOAD STATE: a state saved with INFINITE REPEL on, loaded with it off, gets its map's rates back and its step counter cleared
        (a .repel mark beside the state); live on Platinum: battle within 11 steps
  - [x] Gen 4 story bosses in the RUN tab's Next (Elite Four, Champion, rivals, Galactic / Rockets, HGSS Kanto leaders): doneAny from event
        flags / variables (Claude A's map, B's overlay + pack wiring); live: Platinum and SoulSilver finished saves read Champion
  - [x] DS GAME CLOCK: the game's clock and day / night follow (Platinum 17:00 -> 22:00: Pokétch and night tint)
  - [ ] Black / Blaze Black trainer flags: CUT for the release (needs an early-game Black save to find them)
  - [x] Overlay fixes (outside git): Next tab no longer crashes when no starter is owned; team-helper names in full; damage over 999% shows 999+;
        Team page says the PC isn't read on DS games
  - [x] Five tests that only failed on a Windows checkout fixed (File.renameTo over an existing file in SaveBackups / LibraryOrder /
        BestEffortStore; CRLF in PerformanceGuards / SiteDataExport tests)
  - [x] Manual: DS RUN tab section, NATURE for all eight games
- [x] GBA QUICK MENU HEAL tile: full HP + no status, outside a battle (PartyHeal; not R.O.W.E. / Quetzal layouts)
- [x] GBA HEAL checked in a real game (Emerald): greyed in battle ("Not during a battle"), healed on the field, kept after an in-game save
      and a restart; R = run in one press at Emerald's action menu
- [x] Two screens: the top screen is just the game (the HUD shows on one-screen devices only); new WILD companion tab (this map's wild
      Pokemon, every one named, Repel steps, INFINITE REPEL = WILD ENCOUNTERS OFF), on the default bar; checked in Radical Red and Emerald
- [x] Debug builds write the test walker's state for every GBA game (map, x / y, battle, party)
- [ ] GBA extras still to do: change time (AUTO-RUN and WILD ENCOUNTERS OFF already exist)
- [ ] Touching the bottom screen on the test device sent keys to the launcher behind it: check on the Thor
- [x] Pause menu (BACK / L3) in the app's look: save / load state, fast forward 3x (or hold R2), swap screens,
      close; checked on the test device (state saved, loaded, game ran on; FF ~2.4x on the slow test device)
- [ ] Blaze Black Reduxx, Blaze Black 2 Redux, Renegade Platinum, Sterling Silver packs

## Stage 5: 3DS
- [ ] Floating button + companion over Azahar's bottom screen
- [ ] Ancestral X, Sinking Sapphire, Rising Ruby, Y Nouveau packs
