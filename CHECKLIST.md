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
- [ ] Commit + push to your GitHub fork (waiting for your OK)

## Stage 2: More GBA games (PokeDaisy already has their guides; we add calc + bosses) <- next
- [ ] Unbound 2.1.1.1 (boss sets on hand)
- [ ] Emerald Imperium 1.3.1 (docs zip on hand)
- [ ] Sword & Shield Ultimate Plus (wiki; no trainer docs)
- [ ] SoulGold v1.2 (website data)

## Stage 3: Library
- [ ] Check what PokeDaisy's library already does; add DS / 3DS folders if missing

## Stage 4: DS inside the app
- [ ] melonDS core, two screens, swap button (game touch screen <-> companion)
- [ ] Blaze Black Reduxx, Blaze Black 2 Redux, Renegade Platinum, Sterling Silver packs

## Stage 5: 3DS
- [ ] Floating button + companion over Azahar's bottom screen
- [ ] Ancestral X, Sinking Sapphire, Rising Ruby, Y Nouveau packs
