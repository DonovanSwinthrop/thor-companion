package com.pokedaisy.app.nds

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The RUN tab for DS games: which pack a ROM gets, and its state in the overlay's decoded form (the one Emerald
 * Imperium and SoulGold use, `dec: 1`): numbers only, the pack names them.
 */
object NdsFeed {

    /** Where a game keeps what the feed reads; [flags] = its event flags, [badges] = its badge byte (0: not mapped). */
    class Game(val pack: String, val party: Int, val zone: Int, val flags: Int = 0, val badges: Int = 0, val bag: Int = 0,
               val repel: Int = 0,
               /** Gen 4: the save data is on the heap; [party] and [zone] are offsets from the address stored here. */
               val base: Int = 0,
               val gen4: Boolean = false,
               /** Gen 4: the player's own trainer ID, an offset from [base] (0: use the party's IDs). */
               val tid: Int = 0,
               /** How many slots the pocket at [bag] has (Gen 5: the items pocket; Gen 4: the Poké Balls pocket). */
               val bagSlots: Int = 310)

    /** Where [game]'s party is now (its count is the u32 before it); 0 if the save isn't loaded. */
    fun partyAddr(ram: ByteArray, game: Game): Int = game.party + baseOf(ram, game)

    /** Where [game]'s bag pocket for balls is now (an address for Gen 5, an offset from [Game.base] for Gen 4). */
    fun bagAddr(ram: ByteArray, game: Game): Int = if (game.bag == 0) 0 else game.bag + baseOf(ram, game)

    /** Where [game]'s current map number is now; 0 if the save isn't loaded. */
    fun zoneAddr(ram: ByteArray, game: Game): Int = game.zone + baseOf(ram, game)

    private fun baseOf(ram: ByteArray, game: Game): Int {
        if (game.base == 0) return 0
        val v = u32(ram, game.base - NdsMons.RAM_BASE).toInt()
        return if (v in NdsMons.RAM_BASE until NdsMons.RAM_BASE + ram.size - 0x2000) v else Int.MIN_VALUE / 2
    }

    private fun u32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)

    /**
     * Platinum (US) and Renegade Platinum: the save block's address is at 0x02101D40 (the pointer Action Replay codes
     * start from); the party is at +0xB4 (count +0xB0, the general block's 0xA0 after a 0x14 header) and the current
     * map header number at +0x1294. Found on a 100% save: the party screen's Pokémon at +0xB4, and the map 101 -> 86
     * walking out of Hearthome's Pokémon Center (both Hearthome City in the ROM's header table).
     */
    private const val PT_BASE = 0x02101D40
    private const val PT_PARTY = 0xB4
    private const val PT_ZONE = 0x1294
    /** The player's trainer ID (u16, SID after it): found beside the party in Platinum's save block (pt_wild1 / pt_out). */
    private const val PT_TID = 0x8C
    /** The Poké Balls pocket (u16 item, u16 count; the battle bag's POKé BALLS page lists it in this order): found by a
     *  100% save's own bag, Poké / Premier / Nest / Great / Quick / Master (pt_wild3). */
    private const val PT_BALLS = 0xD00
    private const val PT_BALL_SLOTS = 15
    /** The repel's steps left (a byte in the save block): a Repel set it to 100, ten steps took it to 89 (pt_rep1 / 2). */
    private const val PT_REPEL = 0x8087

    /**
     * Gen 4 games whose battle screen the shortcuts were checked on: Platinum's RUN, BAG, POKé BALLS, the six ball slots,
     * the next-page arrow and USE sit where Black 2's do (to a DS pixel or two), so the same taps work.
     */
    private val GEN4_SHORTCUTS = setOf("pt", "rp")

    /**
     * HeartGold / SoulSilver (US): the save data's address is at 0x02111880 (the same block Action Replay codes reach
     * from 0x0211186C + 0xCFE0); the party is at +0xA8 (count +0xA4) and the current map header number at +0x1244.
     * Found on a finished SoulSilver save: its six Pokémon at +0xA8, and the map 56 -> 482 walking into Fuchsia
     * City's Pokémon Center (both Fuchsia City in the ROM's header table).
     */
    private const val HGSS_BASE = 0x02111880
    private const val HGSS_PARTY = 0xA8
    private const val HGSS_ZONE = 0x1244
    /** The player's trainer ID: SoulSilver's six party Pokémon all carry it (ss_field1). */
    private const val HGSS_TID = 0x84

    /** Black 2 (US) and the hacks built on it: the party and the current zone in the save data (RAM dumps). */
    private const val B2_PARTY = 0x0221E3EC
    private const val B2_ZONE = 0x0221EAE8

    /**
     * Black / White (US): the party at 0x022349B4 (count before it) and the zone 0x6FC after it, Black 2's layout.
     * Found on a finished Black save at the Gear Station (zone 68). Blaze Black shares the code and is taken to share them.
     */
    private const val B1_PARTY = 0x022349B4
    private const val B1_ZONE = 0x022350B0

    /**
     * Black 2 (US)'s event flags (the save's EventWork flags, mirrored in RAM). Trainer N's "beaten" flag is
     * 1488 + N: found by diffing dumps around two Route 19 fights (Youngster 164 -> flag 1652, Lass 165 -> 1653),
     * each the only flag that changed. The packs' doneFlags use the same rule.
     */
    private const val B2_FLAGS = 0x02225846
    private const val FLAG_BYTES = 0x180

    /**
     * Black 2's badges, a bit each (save 0x21104). Gym leaders don't set their trainer flag: Cheren's win set no
     * 1488 + 156, while this byte went 0 -> 1. The feed appends it after the event flags, so badge n is flag
     * [BADGE_FLAG_BASE] + n for the packs' doneFlags.
     */
    private const val B2_BADGES = 0x022266E8
    const val BADGE_FLAG_BASE = FLAG_BYTES * 8

    /** Black 2's items pocket (save 0x18400; balls live there in Gen 5): the battle shortcut's L reads it. */
    private const val B2_BAG = 0x0221D9E4

    /**
     * Black 2's repel step counter (a byte; the next one is the kind last used): a Repel set it 0 -> 100 and 8 steps
     * took it to 92 (dumps b2_rep0 / 1 / 2). INFINITE REPEL keeps it topped up.
     */
    private const val B2_REPEL = 0x02226F11

    /** The game a ROM is, by its header's game code and (for hacks sharing one) its file name. */
    fun gameFor(rom: File): Game? {
        val code = runCatching { rom.inputStream().use { s -> ByteArray(16).also { s.read(it) } } }.getOrNull()
            ?.copyOfRange(12, 16)?.toString(Charsets.US_ASCII) ?: return null
        val name = rom.name.lowercase()
        return when {
            code == "IRBO" && "blaze" in name -> Game("bbk", B1_PARTY, B1_ZONE)
            code == "IRBO" -> Game("b1", B1_PARTY, B1_ZONE)
            code == "IREO" && ("blaze" in name || "redux" in name) -> Game("bb2r", B2_PARTY, B2_ZONE, B2_FLAGS, B2_BADGES, B2_BAG, B2_REPEL)
            code == "IREO" -> Game("b2", B2_PARTY, B2_ZONE, B2_FLAGS, B2_BADGES, B2_BAG, B2_REPEL)
            code == "CPUE" && "renegade" in name -> Game("rp", PT_PARTY, PT_ZONE, bag = PT_BALLS, repel = PT_REPEL, base = PT_BASE, gen4 = true, tid = PT_TID, bagSlots = PT_BALL_SLOTS)
            code == "CPUE" -> Game("pt", PT_PARTY, PT_ZONE, bag = PT_BALLS, repel = PT_REPEL, base = PT_BASE, gen4 = true, tid = PT_TID, bagSlots = PT_BALL_SLOTS)
            code == "IPKE" || code == "IPGE" -> Game("hgss", HGSS_PARTY, HGSS_ZONE, base = HGSS_BASE, gen4 = true, tid = HGSS_TID)
            else -> null
        }
    }

    private fun mon(m: NdsMons.Mon, hp: Int? = null, maxHp: Int? = null, level: Int? = null, ability: Int? = null) = JSONObject()
        .put("sp", m.species).put("name", "").put("lv", level ?: m.level ?: 1)
        .put("hp", hp ?: m.hp ?: 0).put("maxHP", maxHp ?: m.maxHp ?: 0)
        .put("status", statusName(m.status)).put("pid", m.pid).put("egg", m.isEgg)
        .put("moves", JSONArray().apply { m.moves.filter { it != 0 }.forEach { put(JSONObject().put("id", it).put("name", "")) } })
        .put("item", m.item).put("itemName", "")
        .put("ab", ability ?: m.ability).put("abName", "")
        .put("nat", m.nature).put("ivs", JSONArray(m.ivs)).put("evs", JSONArray(m.evs))
        .put("met", m.metLocation).put("metLv", m.metLevel)
        .apply {
            m.stats?.let { st ->
                put("stats", JSONArray(st))
                put("bstats", JSONArray(listOf(st[1], st[2], st[3], st[4], st[5])))
            }
        }

    /** Gen 4 / 5 status: sleep turns in bits 0-2, then poison, burn, freeze, paralysis, bad poison. */
    private fun statusName(s: Int) = when {
        s and 7 != 0 -> "SLP"
        s and 0x80 != 0 -> "TOX"
        s and 0x08 != 0 -> "PSN"
        s and 0x10 != 0 -> "BRN"
        s and 0x20 != 0 -> "FRZ"
        s and 0x40 != 0 -> "PAR"
        else -> ""
    }

    private var frame = 0

    /** Black 2's first trainer flag: trainer N is beaten when event flag [TRAINER_FLAG_BASE] + N is set. */
    const val TRAINER_FLAG_BASE = 1488

    /** Whether [game]'s trainer [id] is beaten in [ram] (false where the flags aren't mapped). */
    fun trainerBeaten(ram: ByteArray, game: Game, id: Int): Boolean {
        if (game.flags == 0) return false
        val f = TRAINER_FLAG_BASE + id
        return (ram[game.flags - NdsMons.RAM_BASE + (f shr 3)].toInt() shr (f and 7)) and 1 == 1
    }

    /** The battle's two sides: Gen 5 by its labelled blocks, Gen 4 by its battle-slot blocks ([NdsMons.gen4Sides]). */
    fun sides(ram: ByteArray, game: Game, party: List<NdsMons.Mon>): NdsMons.Sides =
        if (game.gen4) NdsMons.gen4Sides(ram, party)
        else NdsMons.sides(ram, NdsMons.battlers(ram), party.map { it.pid }.toSet())

    /**
     * A wild battle: the foe's Pokémon carry the player's own ID (wild ones are made that way; a trainer's carry
     * theirs). "The player's ID" is any the party carries: the lead may be traded (a Platinum save's Giratina is).
     */
    private fun wild(ram: ByteArray, game: Game, sides: NdsMons.Sides, party: List<NdsMons.Mon>): Boolean {
        val ids = playerIds(ram, game, party)
        return sides.foe.isNotEmpty() && sides.foe.all { b -> b.mon == null || b.mon.otId in ids }
    }

    /** The player's trainer ID where the save holds it ([Game.tid]), else every ID the party carries. */
    private fun playerIds(ram: ByteArray, game: Game, party: List<NdsMons.Mon>): Set<Int> {
        if (game.tid != 0) {
            val b = baseOf(ram, game)
            val o = b + game.tid - NdsMons.RAM_BASE
            if (b > 0 && o >= 0 && o + 2 <= ram.size) return setOf((ram[o].toInt() and 0xFF) or ((ram[o + 1].toInt() and 0xFF) shl 8))
        }
        return party.map { it.otId }.toSet()
    }

    /** A battle (wild or trainer) is on in [ram]: HEAL PARTY waits for it to end (the battle keeps its own HP). */
    fun inBattle(ram: ByteArray, game: Game): Boolean {
        val party = NdsMons.party(ram, partyAddr(ram, game), game.gen4)
        return party.isNotEmpty() && sides(ram, game, party).foe.isNotEmpty()
    }

    /**
     * A wild battle is on in [ram] (the battle shortcuts' gate). Off in a Gen 4 game whose battle screen hasn't been
     * checked ([GEN4_SHORTCUTS]): the shortcuts press Black 2's positions.
     */
    fun wildBattle(ram: ByteArray, game: Game): Boolean {
        if (game.gen4 && game.pack !in GEN4_SHORTCUTS) return false
        val party = NdsMons.party(ram, partyAddr(ram, game), game.gen4)
        if (party.isEmpty()) return false
        return wild(ram, game, sides(ram, game, party), party)
    }

    /** Where [game]'s repel step counter (a byte) is now: an address for Gen 5, an offset from [Game.base] for Gen 4. */
    fun repelAddr(ram: ByteArray, game: Game): Int = if (game.repel == 0) 0 else game.repel + baseOf(ram, game)

    /** Steps left on [game]'s repel (0: none, or not mapped). */
    fun repelSteps(ram: ByteArray, game: Game): Int {
        val a = repelAddr(ram, game)
        val o = a - NdsMons.RAM_BASE
        return if (a == 0 || o < 0 || o >= ram.size) 0 else ram[o].toInt() and 0xFF
    }

    /** [game]'s badges as a bit field (0 where they aren't mapped). */
    fun badges(ram: ByteArray, game: Game): Int =
        if (game.badges == 0) 0 else ram[game.badges - NdsMons.RAM_BASE].toInt() and 0xFF

    private fun hex(ram: ByteArray, off: Int, len: Int) = buildString(len * 2) {
        for (i in off until off + len) append("%02x".format(ram[i].toInt() and 0xFF))
    }

    /** The state for [ram] (main RAM from 0x02000000), or null before a save is loaded (no party yet). */
    fun state(ram: ByteArray, game: Game): JSONObject? {
        val party = NdsMons.party(ram, partyAddr(ram, game), game.gen4)
        if (party.isEmpty()) return null
        val zoneOff = zoneAddr(ram, game) - NdsMons.RAM_BASE
        val zone = (ram[zoneOff].toInt() and 0xFF) or ((ram[zoneOff + 1].toInt() and 0xFF) shl 8)
        val sides = sides(ram, game, party)
        val inBattle = sides.foe.isNotEmpty()
        val trainer = inBattle && !wild(ram, game, sides, party)
        fun battler(b: NdsMons.Battler?) = b?.mon?.let { m ->
            mon(m, b.hp, b.maxHp, b.level, b.ability).apply { b.stages?.let { put("stages", JSONArray(it)) } }
        } ?: JSONObject.NULL
        val bat = JSONArray().put(battler(sides.player.firstOrNull())).put(battler(sides.foe.firstOrNull()))
            .put(JSONObject.NULL).put(JSONObject.NULL)
        return JSONObject()
            .put("v", 2).put("dec", 1).put("frame", frame++)
            .put("inBattle", if (inBattle) 1 else 0)
            .put("battleType", if (trainer) 8 else 0)
            .put("zone", zone).put("mapsec", 0).put("mapName", "")
            .put("partyCount", party.size)
            .put("partyDec", JSONArray().apply { party.forEach { put(mon(it)) } })
            .put("enemyDec", JSONArray().apply { if (trainer) sides.foe.forEach { b -> b.mon?.let { put(mon(it, b.hp, b.maxHp, b.level, b.ability)) } } })
            .put("battlersDec", bat)
            // unmapped flags: nothing counts as beaten, so Next starts at the first fight
            .put("flags", (if (game.flags != 0) hex(ram, game.flags - NdsMons.RAM_BASE, FLAG_BYTES) else "00".repeat(FLAG_BYTES)) +
                (if (game.badges != 0) hex(ram, game.badges - NdsMons.RAM_BASE, 1) else "00"))
            .put("trainerA", 0).put("trainerB", 0).put("mode", 0).put("outcome", 0)
    }
}
