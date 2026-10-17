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
    class Game(val pack: String, val party: Int, val zone: Int, val flags: Int = 0, val badges: Int = 0)

    /** Black 2 (US) and the hacks built on it: the party and the current zone in the save data (RAM dumps). */
    private const val B2_PARTY = 0x0221E3EC
    private const val B2_ZONE = 0x0221EAE8

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

    /** The game a ROM is, by its header's game code and (for hacks sharing one) its file name. */
    fun gameFor(rom: File): Game? {
        val code = runCatching { rom.inputStream().use { s -> ByteArray(16).also { s.read(it) } } }.getOrNull()
            ?.copyOfRange(12, 16)?.toString(Charsets.US_ASCII) ?: return null
        val name = rom.name.lowercase()
        return when {
            code == "IREO" && ("blaze" in name || "redux" in name) -> Game("bb2r", B2_PARTY, B2_ZONE, B2_FLAGS, B2_BADGES)
            code == "IREO" -> Game("b2", B2_PARTY, B2_ZONE, B2_FLAGS, B2_BADGES)
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

    /** [game]'s badges as a bit field (0 where they aren't mapped). */
    fun badges(ram: ByteArray, game: Game): Int =
        if (game.badges == 0) 0 else ram[game.badges - NdsMons.RAM_BASE].toInt() and 0xFF

    private fun hex(ram: ByteArray, off: Int, len: Int) = buildString(len * 2) {
        for (i in off until off + len) append("%02x".format(ram[i].toInt() and 0xFF))
    }

    /** The state for [ram] (main RAM from 0x02000000), or null before a save is loaded (no party yet). */
    fun state(ram: ByteArray, game: Game): JSONObject? {
        val party = NdsMons.party(ram, game.party)
        if (party.isEmpty()) return null
        val zoneOff = game.zone - NdsMons.RAM_BASE
        val zone = (ram[zoneOff].toInt() and 0xFF) or ((ram[zoneOff + 1].toInt() and 0xFF) shl 8)
        val battlers = NdsMons.battlers(ram)
        val sides = NdsMons.sides(ram, battlers, party.map { it.pid }.toSet())
        val inBattle = sides.foe.isNotEmpty()
        val playerTid = party[0].otId
        val trainer = sides.foe.any { b -> b.mon != null && b.mon.otId != playerTid }
        fun battler(b: NdsMons.Battler?) = b?.mon?.let { mon(it, b.hp, b.maxHp, b.level, b.ability) } ?: JSONObject.NULL
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
