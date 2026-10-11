package com.pokedaisy.app.companion.data

import com.pokedaisy.app.MgbaCore

/**
 * Builds a [SnapshotView] from the running core's memory. In-process replacement
 * for android-companion's UDP `telemetryFlow` — no sockets, no reconnect loop.
 *
 * [sample] must run on the emulator thread (EmulatorEngine.onSample) so bus
 * reads never race a frame. Detection (ROM code/size, struct search) is cached.
 */
class TelemetrySampler {

    init {
        resetNativeBagCache()
        RomItemText.use(null)
    }

    private var kind: GameKind? = null
    private var isEmeraldStruct = false
    private var structAddr: Long = -1
    private var nativeCfg: NativeConfig? = null
    // A Game Boy game's (Gen 1) addresses instead of [nativeCfg].
    private var gen1Cfg: Gen1Config? = null
    private var lastError: String? = null
    private var magicProbes = 0
    // Set instead of nativeCfg for a large BPRE ROM that isn't recognized as
    // Unbound (see detect()) - a different hack we have no addresses for.
    private var unsupportedHackLabel: String? = null
    // detect() re-runs while the QOLT probe is still undecided - don't
    // re-hash a 16 MB ROM on every retry.
    private var smallBpeeHashChecked = false
    private var smallBpreHashChecked = false
    // The ROM hashed as retail FireRed / Emerald: no QoL struct to wait for.
    private var retailHash = false

    // BEST EFFORT (BestEffort.kt) for an unsupported ROM of a Gen 3 base game: its header code and
    // SHA-1 (what the match is kept by), the match in use and the config it was made from (the
    // version flags below compare by identity, and a partial match reads through a trimmed copy).
    private var beCode: String? = null
    private var beSha1: String? = null
    private var beSize = 0L
    private var beRev = 0
    private var beLabel: String? = null
    private var beMatch: BestEffort.Match? = null
    private var baseCfg: NativeConfig? = null
    // Where the match came from and whether a party has confirmed it (BestEffortStore.Entry).
    private var beSource = BestEffortStore.Source.AUTO
    private var beVerified = false
    // Best effort chose it just now (a notice), it's only a guess from the ROM's names, or a party
    // read since doesn't hold with it (PICK AGAIN / KEEP; beSuggest: the game that is sure instead).
    private var beFresh = false
    private var beGuess = false
    private var beDoubt = false
    private var beSuggest: BestEffort.Candidate? = null
    private var beDeclined = false
    // The ROM's text (once per ROM) and the last look at every config, for the picker's order.
    private var beText: BestEffort.RomText? = null
    private var beVerdict: BestEffort.Verdict? = null
    private var beSamples = 0
    private var beGuessSamples = 0

    // The player's answers from the companion (any thread), taken on the next sample.
    private sealed interface PickAnswer {
        class Pick(val id: String) : PickAnswer
        object None : PickAnswer
        object Keep : PickAnswer
        object Seen : PickAnswer
    }
    private val answers = java.util.concurrent.ConcurrentLinkedQueue<PickAnswer>()

    /** WHICH GAME IS THIS?: read the ROM as candidate [id] ([BestEffort.candidate]). */
    fun pickGame(id: String) { answers += PickAnswer.Pick(id) }
    /** NONE OF THESE: no game - NOT SUPPORTED, until the player opens the picker again. */
    fun pickNone() { answers += PickAnswer.None }
    /** KEEP: the doubted match stays, as the player's own pick. */
    fun keepGame() { answers += PickAnswer.Keep }
    /** The match notice was seen. */
    fun bestEffortSeen() { answers += PickAnswer.Seen }

    /** [label]: the ROM isn't one detect() knows. A match kept for it ([BestEffortStore]) reads it anyway. */
    private fun unsupportedRom(reader: MemoryReader, code: String, size: Long, hash: String?, label: String) {
        unsupportedHackLabel = label
        if (!BestEffort.canTry(code)) return
        beCode = code
        beSize = size
        beLabel = label
        beRev = runCatching { reader.readCoreMemory(0x080000BCL, 1)[0].toInt() and 0xFF }.getOrDefault(0)
        beSha1 = hash ?: runCatching { sha1HexChunked(reader, 0x08000000L, size) }.getOrNull()
        beDeclined = BestEffortStore.declined(beSha1)
        BestEffortStore.load(beSha1)?.let { e ->
            BestEffortStore.matchOf(e)?.let(::useBestEffort)
            beSource = e.source
            beVerified = e.verified
            // Best effort's own choice no party has confirmed yet (a guess, or a row from before the
            // stricter checks): a guess again, which a party confirms or the timeout drops.
            beGuess = e.source == BestEffortStore.Source.AUTO && !e.verified
        }
        // Look at it on the first sample: a guess or a sure match shows up at once.
        beSamples = EVALUATE_EVERY
    }

    private fun useBestEffort(m: BestEffort.Match) {
        kind = m.candidate.kind
        nativeCfg = m.config
        baseCfg = m.candidate.cfg
        unsupportedHackLabel = null
        beMatch = m
        itemTextChosen = false
    }

    /** Back to NOT SUPPORTED (WHICH GAME IS THIS? on the companion). */
    private fun dropBestEffort() {
        beMatch = null
        baseCfg = null
        nativeCfg = null
        kind = if (BestEffort.isFireRedFamily(beCode)) GameKind.FIRERED else GameKind.EMERALD
        unsupportedHackLabel = beLabel
        beFresh = false
        beGuess = false
        beDoubt = false
        beSuggest = null
        itemTextChosen = false
        resetNativeBagCache()
    }

    private fun save(m: BestEffort.Match, source: BestEffortStore.Source, verified: Boolean) {
        beSource = source
        beVerified = verified
        beSha1?.let { BestEffortStore.save(it, m, source, verified) }
    }

    private fun takeAnswers(reader: MemoryReader) {
        while (true) {
            when (val a = answers.poll() ?: return) {
                is PickAnswer.Pick -> {
                    val c = BestEffort.candidate(a.id) ?: continue
                    val code = beCode ?: continue
                    // Its ROM-side parts checked as usual; picked, it's verified once a party holds with it.
                    val fit = verdict(reader, code)?.fits?.firstOrNull { it.candidate === c }
                    val m = fit?.match() ?: BestEffort.Match(c, false, BestEffort.Part.entries.toSet(), 0)
                    dropBestEffort()
                    useBestEffort(m)
                    beDeclined = false
                    save(m, BestEffortStore.Source.PICKED, verified = fit?.sure == true)
                    android.util.Log.i("pokedaisy", "best effort: picked ${c.id} off=${m.off} for sha1=$beSha1")
                }
                PickAnswer.None -> {
                    dropBestEffort()
                    beDeclined = true
                    beSha1?.let(BestEffortStore::decline)
                }
                PickAnswer.Keep -> beMatch?.let { m ->
                    beDoubt = false
                    beSuggest = null
                    save(m, BestEffortStore.Source.PICKED, verified = true)
                }
                PickAnswer.Seen -> beFresh = false
            }
        }
    }

    private fun verdict(reader: MemoryReader, code: String): BestEffort.Verdict? {
        val text = beText ?: runCatching {
            val t0 = System.nanoTime()
            BestEffort.romText(reader, code, beSize).also {
                android.util.Log.i("pokedaisy", "best effort: ROM text read in ${(System.nanoTime() - t0) / 1_000_000} ms")
            }
        }.getOrNull()?.also { beText = it } ?: return null
        return BestEffort.evaluate(reader, code, beSize, text).also { beVerdict = it }
    }

    /**
     * Every [EVALUATE_EVERY] samples, while there's nothing to read the ROM with yet or the match
     * hasn't been confirmed by a party: what best effort makes of the running game. A SURE match is
     * used (and kept) without asking; with no party yet, a guess from the ROM's names stands in until
     * one reads. A match a party contradicts goes - or, the player's own pick, asks.
     */
    private fun runBestEffort(reader: MemoryReader) {
        val code = beCode ?: return
        val unresolved = beMatch == null && !beDeclined
        val unverified = beMatch != null && !beVerified && !beDoubt
        if (beVerdict != null && !unresolved && !unverified) return
        if (beGuess && ++beGuessSamples > GUESS_SAMPLES) {
            // A guess no party ever confirmed (the RAM isn't the guessed game's): the player picks.
            android.util.Log.i("pokedaisy", "best effort: guess ${beMatch?.candidate?.id} never read a party, asking")
            dropBestEffort()
            return
        }
        if (++beSamples < EVALUATE_EVERY) return
        beSamples = 0
        val v = verdict(reader, code) ?: return
        val sure = v.sure
        // The player's pick (here or in the library's INFO) reads with its ROM-side parts as this ROM has them.
        beMatch?.takeIf { beSource == BestEffortStore.Source.PICKED }?.let { m ->
            val checked = v.fits.firstOrNull { it.candidate === m.candidate }?.match() ?: return@let
            if (checked.off != m.off || checked.full != m.full) { useBestEffort(checked); save(checked, beSource, beVerified) }
        }
        val current = beMatch
        // A confirmed match isn't judged again (that look was only for the picker's order).
        if (current != null && beVerified) return
        when {
            current == null -> if (!beDeclined) {
                val pick = sure ?: v.guess ?: return
                val m = pick.match()
                useBestEffort(m)
                beFresh = true
                beGuess = sure == null
                beGuessSamples = 0
                save(m, BestEffortStore.Source.AUTO, verified = sure != null)
                android.util.Log.i("pokedaisy", "best effort: ${if (sure != null) "sure" else "guess"} ${m.candidate.id} off=${m.off} for sha1=$beSha1")
            }
            !v.sawParty -> {}
            sure != null && sure.candidate.kind == current.candidate.kind -> {
                // Confirmed - by the very build, or a better one of the same game.
                val m = if (beSource == BestEffortStore.Source.AUTO) sure.match() else current
                if (m.candidate !== current.candidate) useBestEffort(m)
                beGuess = false
                save(m, beSource, verified = true)
            }
            beSource == BestEffortStore.Source.AUTO -> {
                // Best effort's own choice didn't hold: the sure one instead, else the player picks.
                dropBestEffort()
                sure?.let { useBestEffort(it.match()); beFresh = true; save(it.match(), BestEffortStore.Source.AUTO, verified = true) }
                    ?: beSha1?.let(BestEffortStore::forget)
            }
            else -> {
                val fit = v.fits.firstOrNull { it.candidate === current.candidate }
                if (sure == null && fit?.party != null && fit.shownHolds) {
                    save(current, beSource, verified = true)
                } else {
                    beDoubt = true
                    beSuggest = sure?.candidate
                }
            }
        }
    }

    /**
     * The QoL struct doesn't carry experience or IVs / EVs, but the party's own
     * RAM struct does (encrypted substructs). The QoL ROMs keep gPlayerParty at
     * the vanilla address (checked against FireRed/Emerald captures) - still, a
     * rebuild can move any global, so a slot only takes the RAM values when
     * that slot decodes (checksum) to the same species and level as the struct.
     * In battle, gEnemyParty (at retail's address too) gives the foes' IVs:
     * kept only while every foe it decodes is one of the struct's battlers.
     */
    private fun withPartyExp(reader: MemoryReader, t: Telemetry): Telemetry {
        val (partyAddr, enemyAddr) = when (kind) {
            GameKind.FIRERED -> NATIVE_FIRERED_REV1.playerParty to NATIVE_FIRERED_REV1.enemyParty
            GameKind.EMERALD -> NATIVE_EMERALD.playerParty to NATIVE_EMERALD_RETAIL.enemyParty
            else -> return t
        }
        val n = t.partyCount.coerceAtMost(PARTY_SIZE)
        if (n == 0) return t
        val raw = runCatching { reader.readCoreMemory(partyAddr, n * MON_STRUCT_SIZE) }.getOrNull() ?: return t
        val party = t.party.mapIndexed { i, mon ->
            val ram = if (i < n) decodePartyMon(raw, i * MON_STRUCT_SIZE) else null
            if (ram?.exp != null && ram.species == mon.species && ram.level == mon.level) {
                mon.copy(exp = ram.exp, personality = ram.personality, stats = ram.stats)
            } else mon
        }
        val foes = if (t.inBattle) runCatching {
            val e = reader.readCoreMemory(enemyAddr, PARTY_SIZE * MON_STRUCT_SIZE)
            (0 until PARTY_SIZE).mapNotNull { decodePartyMon(e, it * MON_STRUCT_SIZE)?.takeIf { m -> m.exp != null } }
        }.getOrNull().orEmpty() else emptyList()
        val battlers = listOf(t.battleMons[BATTLE_POS_OPPONENT_LEFT], t.battleMons[BATTLE_POS_OPPONENT_RIGHT])
            .filter { it.species != 0 }
        val foesOk = battlers.isNotEmpty() && battlers.all { b -> foes.any { it.species == b.species && it.level == b.level } }
        return t.copy(party = party, battleFoes = if (foesOk) foes else emptyList())
    }

    /** Money isn't in the QoL struct either - see [findStructMoney]. */
    private fun withMoney(reader: MemoryReader, t: Telemetry): Telemetry {
        moneyCfg?.let { return t.copy(money = readMoney(reader, it)) }
        val (cfg, money) = findStructMoney(reader, kind, t.mapGroup, t.mapNum) ?: return t
        moneyCfg = cfg
        return t.copy(money = money)
    }
    private var moneyCfg: NativeConfig? = null

    /**
     * The map's size / type and the player's gender, for the region map's
     * player icon - not in the QoL struct either. gMapHeader sits at the
     * retail address in both QoL builds; it's trusted only while it names
     * the struct's own map section. The gender comes from the save blocks
     * [withMoney] found.
     */
    private fun withMapExtras(reader: MemoryReader, t: Telemetry): Telemetry {
        val header = when (kind) {
            GameKind.FIRERED -> NATIVE_FIRERED_REV1.mapHeader
            GameKind.EMERALD -> NATIVE_EMERALD.mapHeader
            else -> return t
        }
        val mapsec = runCatching { reader.readCoreMemory(header + 0x14, 1)[0].toInt() and 0xFF }.getOrNull()
        val shaped = if (mapsec == t.regionMapSectionId) t.withMapShape(readMapShape(reader, header)) else t
        return moneyCfg?.let { shaped.copy(playerGender = readPlayerGender(reader, it), trainerCard = readTrainerCard(reader, it)) } ?: shaped
    }

    /** The latest snapshot, tagged with the game it came from (null while still detecting). */
    fun sample(reader: MemoryReader): SnapshotView {
        val s = sampleUntagged(reader)
        // An unsupported ROM has no game look to borrow - the app's own backdrop.
        return s.copy(
            game = if (s.unsupported) null else kind ?: if (s.connected) activeGame else null,
            bestEffort = beMatch?.let { m ->
                BestEffortView(
                    m.candidate.title, m.full, m.off, beFresh,
                    report = beSha1?.takeIf { beFresh && !beGuess }?.let { BestEffortReport(it.lowercase(), beSize, beCode.orEmpty(), beRev, m.candidate.id, m.full, m.off) },
                    id = m.candidate.id,
                    picked = beSource == BestEffortStore.Source.PICKED,
                    guess = beGuess,
                    doubt = beDoubt,
                    suggest = beSuggest?.title,
                )
            },
            gamePicker = beCode?.let { code -> gamePicker(code) },
        )
    }

    /** The picker's list: the last look's order, every config of the base game (before one, in the list's
     * own order) - one row per title (two builds of SoulGold v1.2 share one), the one in use where it's one. */
    private fun gamePicker(code: String): GamePickerView {
        val v = beVerdict
        val current = beMatch?.candidate?.id
        val all = v?.fits?.map { f -> GameOption(f.candidate.id, f.candidate.title, v.likely(f), f.party != null) }
            ?: BestEffort.candidatesFor(code).map { GameOption(it.id, it.title, likely = false, readsParty = false) }
        val options = all.groupBy { it.title }.values.map { same -> same.firstOrNull { it.id == current } ?: same.first() }
        return GamePickerView(options, current = current, miss = if (beMatch == null) v?.miss else null, declined = beDeclined)
    }

    private fun sampleUntagged(reader: MemoryReader): SnapshotView {
        try {
            if (kind == null) detect(reader)
            if (beCode != null) {
                takeAnswers(reader)
                runBestEffort(reader)
            }
            if (kind != null && !itemTextChosen) {
                itemTextChosen = true
                PokedexSource.romChanged()
                nativeCfg?.language?.let { com.pokedaisy.app.companion.i18n.L10n.applyGameLanguage(it) }
                RomItemText.use(itemTable(reader))
            }
            activeGame = kind ?: GameKind.FIRERED
            val base = baseCfg ?: nativeCfg
            soulGoldV12 = base === NATIVE_SOULGOLD_V1_2 || base === NATIVE_SOULGOLD_V1_2B || base === NATIVE_SOULGOLD_V1_2C
            amethystV141 = base === NATIVE_AMETHYST_V1_4_1
            ultraViolet = base === NATIVE_ULTRA_VIOLET
            mariomonV13 = base === NATIVE_MARIOMON_V1_3 || base === NATIVE_MARIOMON_V1_2_2 || base === NATIVE_MARIOMON_V1_2_1
            romLanguage = nativeCfg?.language ?: 'E'
            romGameCode = nativeCfg?.gameCode.orEmpty()
            // Quetzal picks each kind of name's language from its own options (a few bytes a sample).
            quetzalNames = nativeCfg?.takeIf { it.quetzalPlacesDefault >= 0 }
                ?.let { runCatching { readQuetzalNames(reader, it) }.getOrNull() } ?: QuetzalNames()

            val telemetry = when (kind) {
                // English Unbound leaves nativeCfg null (see detect()); its French translation sets its own.
                GameKind.UNBOUND -> readNativeTelemetry(reader, nativeCfg ?: NATIVE_UNBOUND_WITH_DEX)
                GameKind.YELLOW -> readGen1Telemetry(reader, gen1Cfg ?: GEN1_YELLOW)
                else -> {
                    unsupportedHackLabel?.let { return SnapshotView(connected = false, error = it, unsupported = true) }
                    val cfg = nativeCfg
                    if (cfg != null) {
                        readNativeTelemetry(reader, cfg)
                    } else {
                        if (structAddr < 0 || !magicOk(reader, structAddr)) {
                            structAddr = MgbaCore.pkFindMagic(QOLT_MAGIC)
                        }
                        if (structAddr < 0) {
                            return SnapshotView(
                                connected = false,
                                error = if (isEmeraldStruct || kind == GameKind.FIRERED)
                                    "waiting for gQolTelemetry (QoL ROM) — vanilla ${romLabel()} native read not wired yet"
                                else "no telemetry source",
                            )
                        }
                        withMapExtras(reader, withMoney(reader, withPartyExp(reader, decodeTelemetry(reader.readCoreMemory(structAddr, TELEMETRY_SIZE)))))
                    }
                }
            }
            lastError = null
            return buildSnapshotView(telemetry)
        } catch (e: TelemetryDecodeException) {
            lastError = e.message
            return SnapshotView(connected = false, error = e.message)
        } catch (e: Exception) {
            return SnapshotView(connected = false, error = e.message ?: e.toString())
        }
    }

    private var itemTextChosen = false

    /** Where the detected game's item descriptions are ([RomItemText]): its config's
     * table when the ROM has it there, else (the QoL builds, whose gItems moves with
     * every rebuild) the vanilla table found by its shape. */
    private fun itemTable(reader: MemoryReader): ItemDescTable? {
        if (kind == GameKind.YELLOW || unsupportedHackLabel != null) return null
        val cfg = if (kind == GameKind.UNBOUND) nativeCfg ?: NATIVE_UNBOUND_WITH_DEX else nativeCfg
        val t = cfg?.itemDescs
        if (t != null && RomItemText.matchesRom(reader, t)) return t
        if (cfg != null && t == null) return null
        val size = runCatching { MgbaCore.pkRomSize() }.getOrDefault(0L)
        return RomItemText.findVanillaItems(reader, size)
    }

    private fun detect(reader: MemoryReader) {
        if (runCatching { MgbaCore.pkPlatform() }.getOrDefault(0) == 1) return detectGameBoy()
        val code = runCatching { MgbaCore.pkRomCode() }.getOrNull().orEmpty()
        val size = runCatching { MgbaCore.pkRomSize() }.getOrDefault(0L)

        // LeafGreen / Ruby / Sapphire / the other Emeralds: retail only (no
        // QoL build to probe for), read natively straight away. LeafGreen looks
        // like FireRed, the rest like Emerald (same species / item / map-section ids).
        if (code in OTHER_RETAIL_CODES) {
            val rev = runCatching { reader.readCoreMemory(0x080000BCL, 1)[0].toInt() and 0xFF }.getOrDefault(-1)
            val cfg = otherRetailConfig(code, rev)
            kind = if (code.startsWith("BPR") || code.startsWith("BPG")) GameKind.FIRERED else GameKind.EMERALD
            when {
                size > 0x1000000L -> unsupportedRom(reader, code, size, null,
                    "unrecognized $code-based ROM hack (${size / (1024 * 1024)} MB) - no known RAM addresses for this build")
                cfg == null -> unsupportedRom(reader, code, size, null, "${gameLabel(code)} revision $rev isn't supported yet")
                else -> nativeCfg = withCheckedTables(reader, cfg)
            }
            return
        }

        // Pokémon Ultra Violet: FireRed 1.0 under a game code of its own (PUVV) - by its hash.
        if (code == ULTRA_VIOLET_CODE) {
            val hash = runCatching { sha1HexChunked(reader, 0x08000000L, size) }.getOrNull()
            kind = GameKind.FIRERED
            if (hash == ULTRA_VIOLET_V1_22_SHA1) nativeCfg = NATIVE_ULTRA_VIOLET
            else unsupportedRom(reader, code, size, hash, "unrecognized Ultra Violet build (sha1 ${hash?.take(12) ?: "unknown"}…)")
            return
        }

        // Not FireRed/Emerald-based at all (another GBA game): nothing here
        // knows its RAM - say so rather than read it as FireRed.
        // [CompanionSupport] makes the same call from the file at import.
        if (code.length == 4 && code != "BPRE" && code != "BPEE") {
            kind = GameKind.FIRERED
            // A Gen 3 base game's code under another letter (a regional build nothing maps) may still read as one.
            unsupportedRom(reader, code, size, null, "game code $code isn't a supported Pokémon game")
            return
        }

        // Not every BPRE hack expands past 16 MB either (Orange Islands is exactly 16 MB, like
        // retail and the FireRed QoL build): hash once, like the small-BPEE check below. An
        // unknown hash falls through to the QoL / retail probe unchanged.
        if (code == "BPRE" && size <= 0x1000000L && !smallBpreHashChecked) {
            smallBpreHashChecked = true
            val hash = runCatching { sha1HexChunked(reader, 0x08000000L, size) }.getOrNull()
            if (hash == ORANGE_ISLANDS_UNVERSIONED_SHA1 || hash == ORANGE_ISLANDS_V5_7_BETA_SHA1) {
                kind = GameKind.ORANGE_ISLANDS
                nativeCfg = NATIVE_ORANGE_ISLANDS
                return
            }
            // Recharged Yellow: a 16 MB rebuild of pokefirered, one config per release.
            rechargedYellowConfig(hash)?.let {
                kind = GameKind.RECHARGED_YELLOW
                nativeCfg = it
                return
            }
            retailHash = hash in RETAIL_SHA1S
        }

        // A big (>16 MB) BPRE ROM isn't unique to Unbound - other FireRed-based
        // hacks (Gaia, Radical Red, ...) share the same game code and also
        // expand past the vanilla 16 MB size for their added content. Size and
        // code alone can't tell them apart, and assuming Unbound would apply
        // its (wrong) RAM addresses to a different hack's memory layout,
        // producing silently wrong/garbage telemetry instead of an honest
        // "not supported" - so confirm with an exact whole-ROM hash instead of
        // guessing. One-time cost per launch (a few hundred ms at most to read
        // and hash up to 32 MB), not worth caching more cleverly than "only
        // runs once, since detect() itself is only called while kind == null".
        if (code == "BPRE" && size > 0x1000000L) {
            // Chunked, not one big readCoreMemory(size) call: the native
            // pkReadBytes bridge caps a single read at 16 MB (len > 1<<24 ->
            // null), so a whole-ROM read here would silently fail for any
            // ROM past that (i.e. exactly the large hacks this check exists
            // for) and the hash would always come back null.
            val hash = runCatching { sha1HexChunked(reader, 0x08000000L, size) }.getOrNull()
            when (hash) {
                UNBOUND_V2_1_1_1_SHA1 -> kind = GameKind.UNBOUND
                UNBOUND_V2_1_1_1_FR_SHA1 -> { kind = GameKind.UNBOUND; nativeCfg = NATIVE_UNBOUND_FR }
                GAIA_V3_2_SHA1 -> { kind = GameKind.GAIA; nativeCfg = NATIVE_GAIA_V3_2 }
                RADICAL_RED_V4_1_SHA1 -> { kind = GameKind.RADICAL_RED; nativeCfg = NATIVE_RADICAL_RED_V4_1 }
                ODYSSEY_V4_1_1_SHA1 -> { kind = GameKind.ODYSSEY; nativeCfg = NATIVE_ODYSSEY }
                AMETHYST_V1_3_0_SHA1 -> { kind = GameKind.AMETHYST; nativeCfg = NATIVE_AMETHYST }
                AMETHYST_V1_4_1_SHA1 -> { kind = GameKind.AMETHYST; nativeCfg = NATIVE_AMETHYST_V1_4_1 }
                CELIA_V1_1_4_SHA1 -> { kind = GameKind.CELIA; nativeCfg = NATIVE_CELIA }
                SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_SHA1, SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_CASUAL_SHA1,
                SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_PERFORMANCE_SHA1, SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_CASUAL_PERFORMANCE_SHA1,
                -> { kind = GameKind.SWORD_SHIELD; nativeCfg = NATIVE_SWORD_SHIELD }
                else -> {
                    kind = GameKind.FIRERED
                    unsupportedRom(reader, code, size, hash, "unrecognized FireRed-based ROM hack " +
                        "(BPRE, ${size / (1024 * 1024)} MB, sha1 ${hash?.take(12) ?: "unknown"}…) " +
                        "- no known RAM addresses for this build")
                    // Full hash, not just the 12-char prefix in the UI-facing
                    // message above - needed to add a new *_SHA1 constant.
                    // This is the LIVE-BUS hash (see RADICAL_RED_V4_1_SHA1's
                    // comment on why it can differ from a plain `shasum` on
                    // the file - RTC/GPIO registers mapped into the ROM's
                    // address space), so don't substitute a file-hash here.
                    android.util.Log.i("pokedaisy", "unrecognized BPRE hack: full sha1=$hash size=$size")
                }
            }
            return
        }

        // Same idea for Emerald-based hacks (BPEE) - see the BPRE block above
        // for why a hash, not a guess. Without this, ANY large BPEE ROM fell
        // through to the vanilla-Emerald native path below unconditionally
        // (no struct to probe for, no disambiguation at all) - silently wrong
        // addresses for a real hack, exactly the failure mode the BPRE check
        // was already written to avoid.
        if (code == "BPEE" && size > 0x1000000L) {
            val hash = runCatching { sha1HexChunked(reader, 0x08000000L, size) }.getOrNull()
            when (hash) {
                HEART_AND_SOUL_V2_0_6_SHA1 -> { kind = GameKind.HEART_AND_SOUL; nativeCfg = NATIVE_HEART_AND_SOUL }
                LAZARUS_V2_0_SHA1 -> { kind = GameKind.LAZARUS; nativeCfg = NATIVE_LAZARUS }
                ROWE_V2_1_9_1_SHA1 -> { kind = GameKind.ROWE; nativeCfg = NATIVE_ROWE }
                EMERALD_ROGUE_V2_2_1_EX_SHA1 -> { kind = GameKind.EMERALD_ROGUE; nativeCfg = NATIVE_EMERALD_ROGUE }
                TMT2_V1_5_2_SHA1 -> { kind = GameKind.TMT2; nativeCfg = NATIVE_TMT2 }
                SOULGOLD_V1_1_4_SHA1 -> { kind = GameKind.SOULGOLD; nativeCfg = NATIVE_SOULGOLD }
                SOULGOLD_V1_2_SHA1 -> { kind = GameKind.SOULGOLD; nativeCfg = NATIVE_SOULGOLD_V1_2 }
                SOULGOLD_V1_2B_SHA1 -> { kind = GameKind.SOULGOLD; nativeCfg = NATIVE_SOULGOLD_V1_2B }
                SOULGOLD_V1_2C_SHA1 -> { kind = GameKind.SOULGOLD; nativeCfg = NATIVE_SOULGOLD_V1_2C }
                GLAZED_V9_2_0_SHA1 -> { kind = GameKind.GLAZED; nativeCfg = NATIVE_GLAZED }
                IMPERIUM_V1_3_1_SHA1 -> { kind = GameKind.IMPERIUM; nativeCfg = NATIVE_IMPERIUM }
                QUETZAL_V9_0_ALPHA_SHA1 -> { kind = GameKind.QUETZAL; nativeCfg = NATIVE_QUETZAL }
                QUETZAL_V9_0_ALPHA_ES_SHA1 -> { kind = GameKind.QUETZAL; nativeCfg = NATIVE_QUETZAL_ES }
                MARIOMON_V1_5_2_Anniversary_SHA1 -> { kind = GameKind.MARIOMON; nativeCfg = NATIVE_MARIOMON_V1_5_2 }
                MARIOMON_V1_5_1_Anniversary_SHA1 -> { kind = GameKind.MARIOMON; nativeCfg = NATIVE_MARIOMON_V1_5_1 }
                MARIOMON_V1_3_SHA1 -> { kind = GameKind.MARIOMON; nativeCfg = NATIVE_MARIOMON_V1_3 }
                MARIOMON_V1_2_2_SHA1 -> { kind = GameKind.MARIOMON; nativeCfg = NATIVE_MARIOMON_V1_2_2 }
                MARIOMON_V1_2_1_SHA1 -> { kind = GameKind.MARIOMON; nativeCfg = NATIVE_MARIOMON_V1_2_1 }
                else -> {
                    kind = GameKind.EMERALD
                    unsupportedRom(reader, code, size, hash, "unrecognized Emerald-based ROM hack " +
                        "(BPEE, ${size / (1024 * 1024)} MB, sha1 ${hash?.take(12) ?: "unknown"}…) " +
                        "- no known RAM addresses for this build")
                    android.util.Log.i("pokedaisy", "unrecognized BPEE hack: full sha1=$hash size=$size")
                }
            }
            return
        }

        // Not every BPEE hack expands past 16 MB (Emerald Seaglass is exactly
        // 16 MB, same as retail) - hash those too, once, before the QoL/
        // vanilla probe below. An unknown hash here is NOT an unsupported
        // hack: retail Emerald and emerald-qol are 16 MB as well, so fall
        // through unchanged.
        if (code == "BPEE" && !smallBpeeHashChecked) {
            smallBpeeHashChecked = true
            val hash = runCatching { sha1HexChunked(reader, 0x08000000L, size) }.getOrNull()
            if (hash == EMERALD_SEAGLASS_V3_0_SHA1) {
                kind = GameKind.EMERALD_SEAGLASS
                nativeCfg = NATIVE_EMERALD_SEAGLASS
                return
            }
            retailHash = hash in RETAIL_SHA1S
        }

        // FireRed / Emerald: the QoL struct's "QOLT" magic isn't written until
        // ~1 s of frames have run, so a -1 here early doesn't mean "no struct".
        // Probe a few times before falling back to native-RAM reads - unless the
        // ROM hashed as retail, which has no struct (the probes held every retail
        // FireRed / Emerald launch, resumed states included, ~6 s on LOADING).
        val emerald = code == "BPEE"
        val addr = if (retailHash) -1L else MgbaCore.pkFindMagic(QOLT_MAGIC)
        if (addr >= 0) {
            kind = if (emerald) GameKind.EMERALD else GameKind.FIRERED
            isEmeraldStruct = emerald
            structAddr = addr
            return
        }
        if (!retailHash && ++magicProbes < 6) return   // stay undecided; retry next tick

        // Gave up on the struct — read native RAM.
        isEmeraldStruct = emerald
        kind = if (emerald) GameKind.EMERALD else GameKind.FIRERED
        val rev = runCatching { reader.readCoreMemory(0x080000BCL, 1)[0].toInt() and 0xFF }.getOrDefault(1)
        val cfg = when {
            emerald -> NATIVE_EMERALD_RETAIL
            rev >= 1 -> NATIVE_FIRERED_REV1
            // NATIVE_FIRERED_REV0 is the hacks' base too: only retail gets the card.
            else -> NATIVE_FIRERED_REV0.copy(trainerCard = TRAINER_CARD_FIRERED)
        }
        nativeCfg = withCheckedTables(reader, cfg)
    }

    /** An unrecognised same-size hack lands on the retail configs too: no DEX
     * tab, and no HERE / NEXT BOSS guide pages, unless the retail tables are
     * really where the config expects them. */
    private fun withCheckedTables(reader: MemoryReader, cfg: NativeConfig): NativeConfig {
        val checked = cfg.pokedex?.takeIf { !pokedexMatchesRom(reader, it) }?.let { cfg.copy(pokedex = null) } ?: cfg
        return checked.guideTables?.takeIf { !guideTablesMatchRom(reader, it) }
            ?.let { checked.copy(guideTables = null, enemyParty = 0) } ?: checked
    }

    private fun magicOk(reader: MemoryReader, addr: Long): Boolean = runCatching {
        String(reader.readCoreMemory(addr, 4), Charsets.US_ASCII) == "QOLT"
    }.getOrDefault(false)

    /**
     * Fast, 2-byte peek at battleActiveBattler/battleInputState — unlike
     * [sample], not throttled to ~1 Hz, for driving the bottom-screen touch
     * battle control (see PLAN.md Phase 5). Returns null before
     * [sample] has resolved which game/path is active, or on a pre-v3 ROM
     * (structSize too small to have these fields at all).
     */
    /** The detected game's verified gMain and its inBattle offset (its native config), or null
     * (not detected yet, or the QoL struct path) - for SMART FF's menu watch ([FfMenuWatch]). */
    val knownGMain: Pair<Long, Long>?
        get() = (if (kind == GameKind.UNBOUND) NATIVE_UNBOUND else nativeCfg)?.let { it.gMain to it.inBattleOff }

    /** A Game Boy / Color cart, by the whole ROM's SHA1 (read from the cart: it's bank-switched). */
    private fun detectGameBoy() {
        val size = runCatching { MgbaCore.pkRomSize() }.getOrDefault(0L)
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        var off = 0L
        while (off < size) {
            val len = minOf(1L shl 20, size - off).toInt()
            digest.update(MgbaCore.pkRomRead(off, len) ?: break)
            off += len
        }
        val hash = if (off == size && size > 0) digest.digest().joinToString("") { "%02x".format(it) } else null
        when {
            hash == YELLOW_SHA1 && GAME_BOY_SUPPORT -> { kind = GameKind.YELLOW; gen1Cfg = GEN1_YELLOW }
            else -> {
                kind = GameKind.FIRERED
                unsupportedHackLabel = "this Game Boy game isn't supported yet (sha1 ${hash?.take(12) ?: "unknown"}…)"
            }
        }
    }

    /** The detected game's gPartyMenu + gPlayerParty when its config has them (SoulGold), or null. */
    val knownPartyMenu: Pair<Long, Long>?
        get() = nativeCfg?.takeIf { it.partyMenu != 0L }?.let { it.partyMenu to it.playerParty }

    /** The open battle menu's cursor from the last fast sample, for a Gen 1 game ([readGen1BattleInput]); -1 otherwise. */
    @Volatile var battleMenuCursor = -1
        private set

    fun sampleBattleInputFast(reader: MemoryReader): Pair<Int, Int>? {
        if (kind == GameKind.YELLOW) {
            val (state, cursor) = runCatching { readGen1BattleInput(reader, gen1Cfg ?: GEN1_YELLOW) }.getOrNull() ?: return null
            battleMenuCursor = cursor
            return 0 to state
        }
        // Unbound's `kind` is decided directly in detect() without ever
        // populating nativeCfg (see the GameKind.UNBOUND branch there) -
        // sample() has its own matching special case for the same reason.
        if (kind == GameKind.UNBOUND) return readNativeBattleInputFast(reader, NATIVE_UNBOUND)
        val cfg = nativeCfg
        if (cfg != null) return readNativeBattleInputFast(reader, cfg) // Tier B
        val addr = structAddr
        if (addr < 0) return null
        return runCatching {
            val raw = reader.readCoreMemory(addr + BATTLE_INPUT_STATE_OFFSET, 2)
            (raw[0].toInt() and 0xFF) to (raw[1].toInt() and 0xFF)
        }.getOrNull()
    }

    private fun romLabel() = if (isEmeraldStruct) "Emerald" else "FireRed"

    companion object {
        private val QOLT_MAGIC = "QOLT".toByteArray(Charsets.US_ASCII)
        // Fixed byte offset of battleActiveBattler within QolTelemetryData
        // (right after the v2 icon-table block) — stable across future
        // struct versions since new fields are always appended at the end.
        private const val BATTLE_INPUT_STATE_OFFSET = 460L
        // Pokémon Unbound v2.1.1.1 - the only version this project's Unbound
        // support (native-RAM addresses, gen3mon decode, icon fetch, etc.) has
        // ever been built or verified against. A different Unbound version
        // would need re-verifying the same way, same as any other hack.
        //
        // Equals a plain `shasum` on the file (b4776b82a4c7915d0fadeaa27e01
        // 3523f99dfd94, also what CLAUDE.md's project notes pin) now that
        // sha1HexChunked masks the RTC/GPIO hole (see its own comment) -
        // wasn't always true, see git history if this needs re-deriving on an
        // older checkout.
        /** Retail games read natively with no QoL build: LeafGreen, Ruby, Sapphire. */
        val OTHER_RETAIL_CODES = setOf("BPGE", "AXVE", "AXPE") + EMERALD_LOCALIZED_CODES +
            RETAIL_PORTS.keys.map { it.dropLast(1) }

        /** The config for [code] at header revision [rev], or null if that revision isn't mapped. */
        fun otherRetailConfig(code: String, rev: Int): NativeConfig? = RETAIL_PORTS["$code$rev"]?.invoke() ?: when (code) {
            "BPGE" -> when (rev) { 0 -> NATIVE_LEAFGREEN_REV0; 1 -> NATIVE_LEAFGREEN_REV1; else -> null }
            // Revs 1 and 2 share every address (both built from pokeruby); rev 0 wasn't checked.
            "AXVE" -> if (rev == 1 || rev == 2) NATIVE_RUBY else null
            "AXPE" -> if (rev == 1 || rev == 2) NATIVE_SAPPHIRE else null
            // Each European Emerald had one release (rev 0).
            "BPES" -> if (rev == 0) NATIVE_EMERALD_ES else null
            "BPED" -> if (rev == 0) NATIVE_EMERALD_DE else null
            "BPEF" -> if (rev == 0) NATIVE_EMERALD_FR else null
            "BPEI" -> if (rev == 0) NATIVE_EMERALD_IT else null
            "BPEJ" -> if (rev == 0) NATIVE_EMERALD_JA else null
            else -> null
        }

        fun gameLabel(code: String) = when (code) {
            "BPGE" -> "LeafGreen"
            "AXVE" -> "Ruby"
            "AXPE" -> "Sapphire"
            "BPES", "BPED", "BPEF", "BPEI", "BPEJ" -> "Emerald (${code.last()})"
            else -> RETAIL_PORT_CODE_TITLES[code] ?: code
        }

        const val UNBOUND_V2_1_1_1_SHA1 = "b4776b82a4c7915d0fadeaa27e013523f99dfd94"
        // Its French fan translation (the same release's code and RAM, French text) - host-side
        // masked hash (GPIO bytes zero, so a plain `shasum`).
        const val UNBOUND_V2_1_1_1_FR_SHA1 = "0ce2a880aa097f1dce4e1db8ee513d0e82d15859"
        // Pokémon Gaia v3.2 - see NATIVE_GAIA_V3_2 in NativeReader.kt for the
        // verification this address reuse is based on.
        const val GAIA_V3_2_SHA1 = "d5b1e77975fcda831e0e9a7b527906bf3f40ecd0"
        // Pokémon Radical Red v4.1 - equals a plain `shasum` on the file now
        // that sha1HexChunked masks the RTC/GPIO hole (see its own comment) -
        // this hack maps a real-time-clock feature into the classic
        // 0x080000C4-C9 GPIO hole a few official carts and several modern
        // hacks use, and reading through it unmasked returns live,
        // NOT-necessarily-stable-across-a-session hardware state instead of
        // static ROM data (confirmed 2026-09-22 via Heart and Soul: matched a
        // file hash on a truly fresh boot, came out completely different
        // after resuming a session with real elapsed play time).
        const val RADICAL_RED_V4_1_SHA1 = "964f951a0fdaf209e4ea1344883ef0d557bb3a80"
        // Pokemon Odyssey (English) v4.1.1 - confirmed live on-device 2026-09-22.
        const val ODYSSEY_V4_1_1_SHA1 = "8745ddbdbfadf6abaf66de4e9055923b62eb4668"
        // Emerald-based (BPEE) hacks - confirmed live on-device 2026-09-22 via
        // the full-hash debug log in the BPEE `else` branch above.
        const val HEART_AND_SOUL_V2_0_6_SHA1 = "79ee6df0869c1773c8c6a5f764afc1f8d833d8bb"
        const val LAZARUS_V2_0_SHA1 = "7dcdc7e280bc4631487e13dd37e6e0cea04adea6"
        const val ROWE_V2_1_9_1_SHA1 = "81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77"
        // Emerald Rogue v2.2.1-EX - confirmed live on-device 2026-09-22; equals
        // a plain `shasum` on the file (7600af1fe08444c850c3c1227fd7dfd81336ae8e).
        const val EMERALD_ROGUE_V2_2_1_EX_SHA1 = "7600af1fe08444c850c3c1227fd7dfd81336ae8e"
        // Pokemon Amethyst v1.3.0 - a BPRE (FireRed-based) hack, unlike the
        // other two above - confirmed live on-device 2026-09-22; equals a
        // plain `shasum` on the file (00e70c0384a5f1698588034201fd5b849d3542e2).
        const val AMETHYST_V1_3_0_SHA1 = "00e70c0384a5f1698588034201fd5b849d3542e2"
        // Pokemon Amethyst v1.4.1 - host-side masked hash (GPIO bytes zero, so a plain `shasum`).
        const val AMETHYST_V1_4_1_SHA1 = "91291aade04b4b111cd03ae7b6e2ff460e1edd8a"
        // Retail FireRed rev 0 / rev 1 and Emerald (USA, Europe): pret's sha1s, which equal the
        // masked hash (their GPIO bytes are zero).
        val RETAIL_SHA1S = setOf(
            "41cb23d8dccc8ebd7c649cd8fbb58eeace6e2fdc",
            "dd5945db9b930750cb39d00c84da8571feebf417",
            "f3ae088181bf583e55daf962a92bb46f4f1d07b7",
        )
        // Emerald Seaglass v3.0 - GPIO-hole-masked hash computed host-side from
        // the file (its 0x080000C4-C9 bytes are zero, so it also equals a plain
        // `shasum`). NOT yet confirmed via the on-device live-bus log.
        const val EMERALD_SEAGLASS_V3_0_SHA1 = "b9f4d332d30fc88c379f9e037f9eae3b2755ead4"
        // Pokemon Celia's Stupid Romhack v1.1.4 - same host-side masked hash as
        // Seaglass (GPIO bytes are zero, so it equals a plain `shasum`).
        const val CELIA_V1_1_4_SHA1 = "3cbd5a2e72ce60cfae1e0c28d83e01f855c51537"
        // Pokemon Too Many Types 2 v1.5.2 - host-side masked hash (GPIO bytes zero).
        const val TMT2_V1_5_2_SHA1 = "3fa8e61ec1727cc51540c54677623f736a446e1b"
        // Pokémon SoulGold v1.1.4 (BPEE, 32 MB) - host-side masked hash (GPIO bytes zero).
        const val SOULGOLD_V1_1_4_SHA1 = "ea5d369cc8a31cbf1cfacb7c9470ea670f08957b"
        // Pokémon SoulGold v1.2 - host-side masked hash (GPIO bytes zero).
        const val SOULGOLD_V1_2_SHA1 = "805d880ee229fb6dc3ce03d7b03baf48f0d759d0"
        // A second build released as SoulGold v1.2 (its title screen says v1.2 too) - masked hash
        // (GPIO bytes zero).
        const val SOULGOLD_V1_2B_SHA1 = "5d6a036260fdbde96f85b5c1b92d0256d3aebafb"
        // A third build released as SoulGold v1.2 ("v1.2" on its title screen too) - masked hash
        // (GPIO bytes zero).
        const val SOULGOLD_V1_2C_SHA1 = "2be7c99aa473308debe1d03ff2015bab7d9addb5"
        // Pokémon Glazed 9.2.0 (BPEE, 32 MB) - host-side masked hash (GPIO bytes zero).
        const val GLAZED_V9_2_0_SHA1 = "e10105d8544469c6a11ca2cf510289981df3c3b0"
        // Pokémon Emerald Imperium v1.3.1 (BPEE, 32 MB) - host-side masked hash (GPIO bytes zero).
        const val IMPERIUM_V1_3_1_SHA1 = "1d20091c4d936f5eb122db8780554dd0829ffb63"
        // Pokémon Quetzal English Alpha 9 v0 (BPEE, 32 MB) - host-side masked hash (GPIO bytes zero).
        const val QUETZAL_V9_0_ALPHA_SHA1 = "d0658315da1e8827f66f15c3d3a000fe747e163e"
        // Pokémon Quetzal Spanish Alpha 9 v0 (BPEE, 32 MB) - host-side masked hash (GPIO bytes zero).
        const val QUETZAL_V9_0_ALPHA_ES_SHA1 = "fe346b5b0eb022e3a103f81e7dcefba6f1dc542f"
        // Pokémon Orange Islands (BPRE rev 0, 16 MB - the size of retail, so checked by hash before
        // the QoL / retail probe): Beta 5.7, and a build with no version on it that differs from it
        // in 80 bytes of map text and data (a typo Beta 5.7 doesn't have - likely an earlier beta).
        // Both host-side masked hashes (GPIO bytes zero).
        const val ORANGE_ISLANDS_V5_7_BETA_SHA1 = "d2e3800e69e44c9649c3d9a15dc1cd12c1d90501"
        const val ORANGE_ISLANDS_UNVERSIONED_SHA1 = "8bac897de515f88e6ce92d0712ce2c018a394b37"
        // Pokémon Ultra Violet v1.22 (16 MB, FireRed rev 0 code under the game code PUVV) - host-side
        // masked hash (GPIO bytes zero, so a plain `shasum`).
        const val ULTRA_VIOLET_V1_22_SHA1 = "3b6c51d8fb6b513bdd01b7bda3ed8965bc985d58"
        const val ULTRA_VIOLET_CODE = "PUVV"
        // Pokémon Sword and Shield Ultimate Plus v1.2.1.2 (BPRE, 32 MB, CFRU) - host-side masked hash
        // (GPIO bytes zero).
        const val SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_SHA1 = "afe4c8e0f9c7186860fcea5f25d8318f5d7929b9"
        // Its three patched releases: the same tables and RAM (the Casual patch changes some item prices and
        // trainers, the Performance patch some code), so the same config. Masked hashes (GPIO bytes zero).
        const val SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_CASUAL_SHA1 = "daa2f89934d2c0447d5271dabeba86bfba04e1fd"
        const val SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_PERFORMANCE_SHA1 = "4858cc3d7b335bb5eed553861d7a4a17bad9ce2c"
        const val SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_CASUAL_PERFORMANCE_SHA1 = "932323d1fa3732d8673d1319fa86ed4df41087b9"

        /** Hacks under a game code of their own that detect() reads as their base game (by hash). */
        val HACK_GAME_CODES = setOf(ULTRA_VIOLET_CODE)
        // Pokémon Recharged Yellow (BPRE rev 0, 16 MB, a pokefirered rebuild): four releases, each
        // its own build (NATIVE_RECHARGED_YELLOW_*). Host-side masked hashes (GPIO bytes zero).
        const val RECHARGED_YELLOW_V1_8_0_SHA1 = "be5033598e91d0e80fa93e551406f02a7876d7c2"
        const val RECHARGED_YELLOW_V1_9_0_SHA1 = "ac30d1ed80e75104a3fb92f71e2b9a51c26e80c8"
        const val RECHARGED_YELLOW_V1_9_4_SHA1 = "7d9fae6cf31c07bc7fa5cf7afe137ad5385cefb1"
        const val RECHARGED_YELLOW_V1_9_7_SHA1 = "ed60654d7525ba005bfc8f799bf0ecba2ef08a2e"

        /** Recharged Yellow's config for a release's hash; null for any other ROM. */
        fun rechargedYellowConfig(hash: String?): NativeConfig? = when (hash) {
            RECHARGED_YELLOW_V1_8_0_SHA1 -> NATIVE_RECHARGED_YELLOW_V1_8_0
            RECHARGED_YELLOW_V1_9_0_SHA1 -> NATIVE_RECHARGED_YELLOW_V1_9_0
            RECHARGED_YELLOW_V1_9_4_SHA1 -> NATIVE_RECHARGED_YELLOW_V1_9_4
            RECHARGED_YELLOW_V1_9_7_SHA1 -> NATIVE_RECHARGED_YELLOW_V1_9_7
            else -> null
        }

        // Super Mariomon (Too Many Productions; BPEE, 32 MB): every release the user has, host-side
        // masked hashes (GPIO bytes zero, so a plain `shasum`).
        const val MARIOMON_V1_2_1_SHA1 = "43dd208b23eade69aa3384301e934ecafbf6c2b3"
        const val MARIOMON_V1_2_2_SHA1 = "08d1c275c0ba4d559b33427292702d61ad49fb5c"
        const val MARIOMON_V1_3_SHA1 = "043512c2497871c018df606fb90ffbd4c81430f4"
        const val MARIOMON_V1_5_1_Anniversary_SHA1 = "831fe0f97301f17b24a9cd4a64ef0520835c4dde"
        const val MARIOMON_V1_5_2_Anniversary_SHA1 = "06e79d12afa578db97c23c1f7ba771fe2f8fe905"

        // Pokémon Yellow (USA, Europe) - the Game Boy cart (pret/pokeyellow builds it byte for byte).
        const val YELLOW_SHA1 = "cc7d03262ebfaf2f06772c1a480c7d9d5f4a38e1"

        /** The Game Boy / Color carts the companion reads ([CompanionSupport] checks imports against it). */
        val SUPPORTED_GB_SHA1S = setOf(YELLOW_SHA1)

        /**
         * Game Boy / Color (Pokémon Yellow): the library, folder scan, frontend launch and
         * archives take .gb / .gbc (RomArchive.ROM_EXTENSIONS) and a Yellow cart is read by the
         * Gen 1 reader. False turns all of it off again (then drop the manifest's .gb / .gbc
         * VIEW patterns too): a cart that arrives anyway gets the "not supported" notice.
         */
        const val GAME_BOY_SUPPORT = true

        /** Every hack detect() has RAM addresses for - the >16 MB ones it
         * hashes, plus Seaglass, Orange Islands, Recharged Yellow and Ultra Violet (16 MB). [CompanionSupport] checks imports against it. */
        val SUPPORTED_HACK_SHA1S = setOf(
            UNBOUND_V2_1_1_1_SHA1, GAIA_V3_2_SHA1, RADICAL_RED_V4_1_SHA1, ODYSSEY_V4_1_1_SHA1,
            AMETHYST_V1_3_0_SHA1, AMETHYST_V1_4_1_SHA1, CELIA_V1_1_4_SHA1, HEART_AND_SOUL_V2_0_6_SHA1, LAZARUS_V2_0_SHA1,
            ROWE_V2_1_9_1_SHA1, EMERALD_ROGUE_V2_2_1_EX_SHA1, TMT2_V1_5_2_SHA1, EMERALD_SEAGLASS_V3_0_SHA1,
            SOULGOLD_V1_1_4_SHA1, SOULGOLD_V1_2_SHA1, SOULGOLD_V1_2B_SHA1, SOULGOLD_V1_2C_SHA1, GLAZED_V9_2_0_SHA1, IMPERIUM_V1_3_1_SHA1,
            QUETZAL_V9_0_ALPHA_SHA1, QUETZAL_V9_0_ALPHA_ES_SHA1, UNBOUND_V2_1_1_1_FR_SHA1,
            ORANGE_ISLANDS_V5_7_BETA_SHA1, ORANGE_ISLANDS_UNVERSIONED_SHA1,
            ULTRA_VIOLET_V1_22_SHA1, SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_SHA1, SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_CASUAL_SHA1,
            SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_PERFORMANCE_SHA1, SWORD_SHIELD_ULTIMATE_PLUS_V1_2_1_2_CASUAL_PERFORMANCE_SHA1,
            RECHARGED_YELLOW_V1_8_0_SHA1, RECHARGED_YELLOW_V1_9_0_SHA1, RECHARGED_YELLOW_V1_9_4_SHA1,
            RECHARGED_YELLOW_V1_9_7_SHA1,
            MARIOMON_V1_2_1_SHA1, MARIOMON_V1_2_2_SHA1, MARIOMON_V1_3_SHA1, MARIOMON_V1_5_1_Anniversary_SHA1,
            MARIOMON_V1_5_2_Anniversary_SHA1,
        )
    }
}

// The classic GBA cart RTC/rumble GPIO hole: 3 memory-mapped u16 registers
// (data/direction/control) a handful of official carts and several modern
// hacks map into what's otherwise plain ROM address space. Reading through
// it returns LIVE hardware state, not static ROM bytes - and unlike what the
// RADICAL_RED_V4_1_SHA1/UNBOUND_V2_1_1_1_SHA1 comments originally assumed,
// that state is NOT necessarily stable across a session (confirmed
// 2026-09-22: Heart and Soul's live-bus hash matched a plain file hash on a
// truly fresh boot, then came out completely different after resuming a
// session with real elapsed play time - almost certainly the RTC clock
// itself having ticked forward). Masking these 6 bytes out before hashing
// makes detection stable regardless of how long the game's been running,
// instead of only working on the very first boot.
/** Samples (~1 a second) between best effort's looks while it has something to settle. */
private const val EVALUATE_EVERY = 5
/** How long a guess from the ROM's names stands without a party to confirm it (~10 minutes). */
private const val GUESS_SAMPLES = 600

private val GPIO_HOLE_START = 0x080000C4L
private const val GPIO_HOLE_SIZE = 6

private fun sha1HexChunked(reader: MemoryReader, baseAddr: Long, size: Long): String {
    val chunkSize = 1 shl 20 // 1 MB - comfortably under pkReadBytes' 16 MB cap
    val digest = java.security.MessageDigest.getInstance("SHA-1")
    var off = 0L
    while (off < size) {
        val len = minOf(chunkSize.toLong(), size - off).toInt()
        val chunk = reader.readCoreMemory(baseAddr + off, len)
        val holeStart = GPIO_HOLE_START - (baseAddr + off)
        if (holeStart in 0 until len) {
            val holeEnd = minOf(len.toLong(), holeStart + GPIO_HOLE_SIZE)
            for (i in holeStart until holeEnd) chunk[i.toInt()] = 0
        }
        digest.update(chunk)
        off += len
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/**
 * A QoL ROM's money, read from its save blocks, with the config that found
 * it. The FireRed QoL build moves gSaveBlock1Ptr/2Ptr (0x03005018 in dist's
 * build vs retail's 0x03005008) and a rebuild can move them again, so each
 * candidate must hold the struct's own map ([mapGroup]/[mapNum], i.e.
 * SaveBlock1.location) before its money is trusted.
 */
internal fun findStructMoney(reader: MemoryReader, game: GameKind?, mapGroup: Int, mapNum: Int): Pair<NativeConfig, Long>? {
    val candidates = when (game) {
        GameKind.FIRERED -> listOf(
            NATIVE_FIRERED_REV1.copy(saveBlock1Ptr = 0x03005018L, saveBlock2Ptr = 0x0300501CL),
            NATIVE_FIRERED_REV1,
        )
        GameKind.EMERALD -> listOf(NATIVE_EMERALD.copy(trainerCard = TRAINER_CARD_EMERALD))
        else -> return null
    }
    for (cfg in candidates) {
        val loc = runCatching {
            val sb1 = saveBlock1(reader, cfg)
            if (sb1 in 0x02000000L until 0x04000000L) reader.readCoreMemory(sb1 + cfg.saveBlock1PosOff + 4, 2) else null
        }.getOrNull() ?: continue
        if ((loc[0].toInt() and 0xFF) != mapGroup || (loc[1].toInt() and 0xFF) != mapNum) continue
        return cfg to (readMoney(reader, cfg) ?: continue)
    }
    return null
}
