package com.pokedaisy.app.nds

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * [NdsMons] against Black 2 (US) RAM dumps. They hold the game's own code, so they're never committed: kept in
 * `~/tools/ndsdump/` (or `-DndsDumps=<dir>`), taken with the DS player's debug dump (`files/nds-dump`). Skipped
 * without them.
 */
class NdsMonsTest {

    private fun dump(name: String): ByteArray? {
        val dir = System.getProperty("ndsDumps") ?: (System.getProperty("user.home") + "/tools/ndsdump")
        return File(dir, name).takeIf { it.isFile }?.readBytes()
    }

    @Test
    fun partyAfterTheStarter() {
        val ram = dump("b2_tepig.bin")
        assumeTrue("no Black 2 dump", ram != null)
        val party = NdsMons.party(ram!!, 0x0221E3EC)
        assertEquals(1, party.size)
        val tepig = party[0]
        assertEquals(498, tepig.species)
        assertEquals(5, tepig.level)
        assertEquals(21, tepig.hp)
        assertEquals(21, tepig.maxHp)
        assertEquals(listOf(33, 39, 0, 0), tepig.moves.toList())   // Tackle, Tail Whip
        assertEquals(66, tepig.ability)                              // Blaze
    }

    @Test
    fun rivalBattleBattlers() {
        val ram = dump("b2_battle_hit.bin")
        assumeTrue("no Black 2 battle dump", ram != null)
        val b = NdsMons.battlers(ram!!)
        assertEquals(2, b.size)
        assertEquals(498, b[0].species)
        assertEquals(9, b[0].hp)
        assertEquals(21, b[0].maxHp)
        assertEquals(501, b[1].species)
        assertEquals(2, b[1].hp)
        assertEquals(67, b[1].ability)                               // Torrent
        assertEquals(listOf(33, 39, 0, 0), b[1].mon!!.moves.toList())
    }

    @Test
    fun theOneOnTheFieldComesFirst() {
        for ((name, lead) in listOf("b2_wild2.bin" to 498, "b2_switched.bin" to 504)) {
            val ram = dump(name)
            assumeTrue("no $name", ram != null)
            val party = NdsMons.party(ram!!, 0x0221E3EC)
            val sides = NdsMons.sides(ram, NdsMons.battlers(ram), party.map { it.pid }.toSet())
            assertEquals(name, 2, sides.player.size)
            assertEquals(name, lead, sides.player[0].species)          // Tepig, then the Patrat switched in
            assertEquals(name, 504, sides.foe.single().species)        // the wild Patrat
            assertEquals(name, 124, party[1].metLocation)               // caught on Route 19
            assertEquals(name, 117, party[0].metLocation)               // the starter: Aspertia City
        }
    }

    @Test
    fun trainerFlagsBeforeAndAfterTwoFights() {
        val game = NdsFeed.Game("b2", 0x0221E3EC, 0x0221EAE8, 0x02225846)
        val dumps = listOf("b2_tr1_before", "b2_tr1_after", "b2_tr2_before", "b2_tr2_after").map { dump("$it.bin") }
        assumeTrue("no trainer-flag dumps", dumps.all { it != null })
        val (b1, a1, b2, a2) = dumps.map { it!! }
        // Route 19: Youngster Terrell (164), then Lass Isabel (165); nobody else beaten yet, not even Cheren (156)
        assertEquals(false, NdsFeed.trainerBeaten(b1, game, 164))
        assertEquals(true, NdsFeed.trainerBeaten(a1, game, 164))
        assertEquals(false, NdsFeed.trainerBeaten(b2, game, 165))
        assertEquals(true, NdsFeed.trainerBeaten(a2, game, 165))
        assertEquals(true, NdsFeed.trainerBeaten(a2, game, 164))
        assertEquals(false, NdsFeed.trainerBeaten(a2, game, 156))
    }

    @Test
    fun gymTrainerFlagAndCherensBadge() {
        val game = NdsFeed.Game("b2", 0x0221E3EC, 0x0221EAE8, 0x02225846, 0x022266E8)
        val dumps = listOf("b2_gym1_before", "b2_gym1_after", "b2_cheren_before", "b2_cheren_after").map { dump("$it.bin") }
        assumeTrue("no gym dumps", dumps.all { it != null })
        val (gb, ga, cb, ca) = dumps.map { it!! }
        // a gym trainer (Youngster Pedro, 171) uses the trainer rule; Cheren (156) doesn't: his win is the badge
        assertEquals(false, NdsFeed.trainerBeaten(gb, game, 171))
        assertEquals(true, NdsFeed.trainerBeaten(ga, game, 171))
        assertEquals(0, NdsFeed.badges(cb, game))
        assertEquals(1, NdsFeed.badges(ca, game))
        assertEquals(false, NdsFeed.trainerBeaten(ca, game, 156))
    }

    @Test
    fun wildBattleOnlyInAWildBattle() {
        val game = NdsFeed.Game("b2", 0x0221E3EC, 0x0221EAE8, 0x02225846, 0x022266E8)
        for ((name, wild) in listOf("b2_wild_menu" to true, "b2_tr1_before" to false, "b2_tr1_after" to false)) {
            val ram = dump("$name.bin")
            assumeTrue("no $name", ram != null)
            assertEquals(name, wild, NdsFeed.wildBattle(ram!!, game))
        }
    }

    @Test
    fun bestBallFromTheBag() {
        val ram = dump("b2_wild_menu.bin")
        assumeTrue("no b2_wild_menu.bin", ram != null)
        assertEquals(4, NdsShortcuts.bestBall(ram!!, 0x0221D9E4))                 // 8 Poke Balls (and an X Speed)
        val taps = NdsShortcuts.ball(ram, 0x0221D9E4)!!
        assertEquals(4, taps.size)                                                // BAG, POKE BALLS, the first slot, USE
        assertEquals(64, taps[2].x)
        assertEquals(32, taps[2].y)
        // with Ultra Balls in the bag after the Poke Balls, they're thrown instead, from the second slot
        val more = ram.copyOf()
        val slot = 0x0221D9E4 - NdsMons.RAM_BASE + 4 * 2
        more[slot] = 2; more[slot + 1] = 0; more[slot + 2] = 3; more[slot + 3] = 0
        assertEquals(2, NdsShortcuts.bestBall(more, 0x0221D9E4))
        assertEquals(192, NdsShortcuts.ball(more, 0x0221D9E4)!![2].x)
    }

    @Test
    fun repelCounterCountsDown() {
        val game = NdsFeed.Game("b2", 0x0221E3EC, 0x0221EAE8, repel = 0x02226F11)
        val dumps = listOf("b2_rep0", "b2_rep1", "b2_rep2").map { dump("$it.bin") }
        assumeTrue("no repel dumps", dumps.all { it != null })
        assertEquals(listOf(0, 100, 92), dumps.map { NdsFeed.repelSteps(it!!, game) })   // none, a Repel used, 8 steps on
    }

    @Test
    fun platinumPartyAndMap() {
        // Platinum (US) on a 100% save: the save block's address at 0x02101D40, the party at +0xB4, the map at +0x1294
        val game = NdsFeed.Game("pt", 0xB4, 0x1294, base = 0x02101D40, gen4 = true)
        val inside = dump("pt_pc.bin")
        val outside = dump("pt_out.bin")
        assumeTrue("no Platinum dumps", inside != null && outside != null)
        val party = NdsMons.party(outside!!, NdsFeed.partyAddr(outside, game), gen4 = true)
        assertEquals(1, party.size)
        assertEquals(487, party[0].species)                                    // Giratina
        assertEquals(1, party[0].level)
        assertEquals(party[0].pid % 25, party[0].nature.toLong())
        fun zone(ram: ByteArray) = NdsFeed.zoneAddr(ram, game).let { a -> (ram[a - 0x02000000].toInt() and 0xFF) or ((ram[a - 0x02000000 + 1].toInt() and 0xFF) shl 8) }
        assertEquals(101, zone(inside!!))                                      // Hearthome's Pokemon Center
        assertEquals(86, zone(outside))                                        // Hearthome City
    }

    @Test
    fun soulSilverPartyMapAndNature() {
        // SoulSilver (US) on a finished save: the save block's address at 0x02111880, party +0xA8, map +0x1244
        val ss = rom("Pokémon SoulSilver Version.nds")
        val field = dump("ss_field1.bin")
        val pc = dump("ss_pc1.bin")
        assumeTrue("no SoulSilver ROM / dumps", ss != null && field != null && pc != null)
        val game = NdsFeed.Game("hgss", 0xA8, 0x1244, base = 0x02111880, gen4 = true)
        val addr = NdsFeed.partyAddr(field!!, game)
        val party = NdsMons.party(field, addr, gen4 = true)
        assertEquals(listOf(157, 196, 18, 62, 162, 20), party.map { it.species })
        assertEquals(List(6) { 100 }, party.map { it.level })
        fun zone(ram: ByteArray) = NdsFeed.zoneAddr(ram, game).let { a -> (ram[a - 0x02000000].toInt() and 0xFF) or ((ram[a - 0x02000000 + 1].toInt() and 0xFF) shl 8) }
        assertEquals(56, zone(field))                                          // Fuchsia City
        assertEquals(482, zone(pc!!))                                          // its Pokemon Center
        val base = ss!!.baseStats()!!
        assertEquals("Typhlosion", ss.speciesNames()!![157])
        assertEquals("Hardy", ss.natureNames()!![0])
        for (m in party) assertEquals(m.stats!!.toList(), NdsMons.stats(base[m.species], m.ivs, m.evs, m.level!!, m.nature).toList())
        val copy = field.copyOf()
        val target = (party[0].nature + 3) % 25
        val write = { a: Int, bytes: ByteArray -> System.arraycopy(bytes, 0, copy, a - NdsMons.RAM_BASE, bytes.size); true }
        assertEquals(true, NdsMons.setNature(copy, addr, 0, target, base[157], gen4 = true, write = write))
        val after = NdsMons.party(copy, addr, gen4 = true)
        assertEquals(6, after.size)
        assertEquals(target, after[0].nature)
        assertEquals(party[1].pid, after[1].pid)
    }

    @Test
    fun statsFormulaMatchesTheGameAndNatureChangeKeepsTheMonValid() {
        val b2 = rom("Pokémon Black Version 2.nds")
        val ram = dump("b2_field2.bin")
        assumeTrue("no Black 2 ROM / dump", b2 != null && ram != null)
        val base = b2!!.baseStats()!!
        val party = NdsMons.party(ram!!, 0x0221E3EC)
        assertEquals(3, party.size)
        for (m in party) {   // the games' own stats, computed again from base stats, IVs, EVs, level and nature
            assertEquals(m.stats!!.toList(), NdsMons.stats(base[m.species], m.ivs, m.evs, m.level!!, m.nature).toList())
        }
        assertEquals("Hardy", b2.natureNames()!![0])
        assertEquals("Tepig", b2.speciesNames()!![498])
        val copy = ram.copyOf()
        val tepig = party[0]
        val target = if (tepig.nature == 3) 13 else 3                            // Adamant (+atk -spa) or Jolly
        val write = { addr: Int, bytes: ByteArray -> System.arraycopy(bytes, 0, copy, addr - NdsMons.RAM_BASE, bytes.size); true }
        assertEquals(true, NdsMons.setNature(copy, 0x0221E3EC, 0, target, base[498], write = write))
        val after = NdsMons.party(copy, 0x0221E3EC)
        assertEquals(3, after.size)                                             // still decodes: checksum fixed
        assertEquals(target, after[0].nature)
        assertEquals(tepig.ivs.toList(), after[0].ivs.toList())
        assertEquals(tepig.hp, after[0].hp)
        assertEquals(NdsMons.stats(base[498], tepig.ivs, tepig.evs, tepig.level!!, target).toList(), after[0].stats!!.toList())
        assertEquals(party[1].nature, after[1].nature)                          // the others untouched
    }

    @Test
    fun gen4PidKeepsGenderAbilityShinyAndWurmple() {
        val (tid, sid) = 12345 to 54321
        val plain = 0x8A3F_51C7L                                                  // not shiny
        val lo = 0x51C7
        val shiny = ((tid xor sid xor lo xor 3).toLong() shl 16) or lo.toLong()  // shiny value 3
        for (pid in listOf(plain, shiny)) {
            fun sv(p: Long) = tid xor sid xor (p and 0xFFFF).toInt() xor (p ushr 16).toInt()
            for (n in 0 until 25) {
                val p = NdsMons.gen4Pid(pid, tid, sid, n)!!
                assertEquals(n.toLong(), p % 25)
                assertEquals(pid and 0xFF, p and 0xFF)                              // gender and ability slot
                assertEquals(sv(pid) < 8, sv(p) < 8)                                // shiny stays shiny, plain stays plain
                assertEquals((pid ushr 16) % 10 < 5, (p ushr 16) % 10 < 5)          // Wurmple's line
            }
        }
    }

    @Test
    fun platinumNatureChangeGivesANewPid() {
        val pt = rom("Pokémon Platinum Version.nds")
        val ram = dump("pt_out.bin")
        assumeTrue("no Platinum ROM / dump", pt != null && ram != null)
        val game = NdsFeed.Game("pt", 0xB4, 0x1294, base = 0x02101D40, gen4 = true)
        val addr = NdsFeed.partyAddr(ram!!, game)
        val base = pt!!.baseStats()!!
        val m = NdsMons.party(ram, addr, gen4 = true)[0]
        assertEquals("Giratina", pt.speciesNames()!![487])
        assertEquals("Quirky", pt.natureNames()!![24])
        assertEquals(m.stats!!.toList(), NdsMons.stats(base[487], m.ivs, m.evs, m.level!!, m.nature).toList())
        val copy = ram.copyOf()
        val target = (m.nature + 7) % 25
        val write = { a: Int, bytes: ByteArray -> System.arraycopy(bytes, 0, copy, a - NdsMons.RAM_BASE, bytes.size); true }
        assertEquals(true, NdsMons.setNature(copy, addr, 0, target, base[487], gen4 = true, write = write))
        val after = NdsMons.party(copy, addr, gen4 = true)[0]                   // decodes: new order, checksum, stream
        assertEquals(target, after.nature)
        assertEquals(m.pid and 0xFF, after.pid and 0xFF)
        assertEquals(m.species, after.species)
        assertEquals(m.ivs.toList(), after.ivs.toList())
        assertEquals(m.moves.toList(), after.moves.toList())
        assertEquals(m.hp, after.hp)
        assertEquals(m.level, after.level)
        assertEquals(NdsMons.stats(base[487], m.ivs, m.evs, m.level!!, target).toList(), after.stats!!.toList())
    }

    /** A retail ROM from [romDir] (`-DndsRoms=<dir>`, else the user's ROM folder), or null. */
    private fun rom(name: String): NdsRom? {
        val dir = System.getProperty("ndsRoms") ?: "E:/Thor_AYN_Roms/DS"
        return File(dir, name).takeIf { it.isFile }?.let(::NdsRom)
    }

    @Test
    fun movePpFromTheRom() {
        val b2 = rom("Pokémon Black Version 2.nds")
        assumeTrue("no Black 2 ROM", b2 != null)
        val pp = b2!!.movePp()!!
        assertEquals(35, pp[33])                                        // Tackle
        assertEquals(30, pp[39])                                        // Tail Whip
        assertEquals(5, pp[63])                                         // Hyper Beam
        val pt = rom("Pokémon Platinum Version.nds")
        if (pt != null) {
            val ppt = pt.movePp()!!
            assertEquals(35, ppt[33])
            assertEquals(5, ppt[63])
        }
    }

    @Test
    fun healPartyFillsPpAndFixesTheChecksum() {
        val ram = dump("b2_wild2.bin")
        assumeTrue("no b2_wild2.bin", ram != null)
        val base = IntArray(700) { 35 }.also { it[39] = 30 }            // Tackle 35, Tail Whip 30, the rest 35
        val copy = ram!!.copyOf()
        val before = NdsMons.party(copy, 0x0221E3EC)
        NdsMons.healParty(copy, 0x0221E3EC, base) { addr, bytes ->
            System.arraycopy(bytes, 0, copy, addr - NdsMons.RAM_BASE, bytes.size); true
        }
        val after = NdsMons.party(copy, 0x0221E3EC)
        assertEquals(before.size, after.size)                           // still decodes: the new checksum holds
        for ((b, a) in before.zip(after)) {
            assertEquals(b.species, a.species)
            assertEquals(b.moves.toList(), a.moves.toList())
            assertEquals(b.ivs.toList(), a.ivs.toList())
            for (k in 0 until 4) if (a.moves[k] != 0) assertEquals(base[a.moves[k]], a.pp[k])
            assertEquals(a.maxHp, a.hp)
        }
    }

    @Test
    fun healPartyFillsHpAndKeepsTheMonValid() {
        val ram = dump("b2_wild2.bin")
        assumeTrue("no b2_wild2.bin", ram != null)
        val copy = ram!!.copyOf()
        val before = NdsMons.party(copy, 0x0221E3EC)
        assertEquals(6, before[1].hp)                                   // the caught Patrat, hurt
        val healed = NdsMons.healParty(copy, 0x0221E3EC) { addr, bytes ->
            System.arraycopy(bytes, 0, copy, addr - NdsMons.RAM_BASE, bytes.size); true
        }
        assertEquals(2, healed)
        val after = NdsMons.party(copy, 0x0221E3EC)
        assertEquals(2, after.size)                                     // still decodes: the checksum block is untouched
        for ((b, a) in before.zip(after)) {
            assertEquals(b.species, a.species)
            assertEquals(a.maxHp, a.hp)
            assertEquals(0, a.status)
            assertEquals(b.level, a.level)
            assertEquals(b.maxHp, a.maxHp)
        }
    }
}
