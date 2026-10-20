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
    fun blackPartyWithPidZeroMonsAndZone() {
        // Black (US), a finished save at the Gear Station: three event Pokemon with PID 0 lead the party
        val ram = dump("b1_station.bin")
        assumeTrue("no Black dump", ram != null)
        val party = NdsMons.party(ram!!, 0x022349B4)
        assertEquals(listOf(381, 245, 639, 521, 612), party.map { it.species })
        assertEquals(68, (ram[0x2350B0].toInt() and 0xFF) or ((ram[0x2350B1].toInt() and 0xFF) shl 8))
    }

    @Test
    fun blackTrainerFlagsAndBadges() {
        // the finished Black save: Route 3's / Nacrene's early trainers beaten (Youngster Jimmy 1, Waiter Maxwell 9),
        // Ace Trainer Lou 605 not; gym leaders (Lenora 21) and the Elite Four (Shauntal 228) set no trainer flag;
        // all eight badges, sent after the event flags
        val ram = dump("b1_station.bin")
        assumeTrue("no Black dump", ram != null)
        for (name in listOf("black.nds", "blaze_black.nds")) {
            val game = NdsFeed.gameFor(File.createTempFile("bw1", name).apply {
                deleteOnExit(); writeBytes(ByteArray(12) + "IRBO".toByteArray())
            })!!
            assertEquals(listOf(true, true, false, false, false), listOf(1, 9, 605, 21, 228).map { NdsFeed.trainerBeaten(ram!!, game, it) })
            assertEquals(0xFF, ram!![0x0223CDB0 - 0x02000000].toInt() and 0xFF)
        }
    }

    @Test
    fun gen4TrainerFlags() {
        // Platinum's finished save: the journal says "Beat Allison at Route 212" (trainer 96; 736-738 her rematches);
        // scripted fights (Elite Four 261, the rival 470) never set a trainer flag. SoulSilver: trainer 4 beaten, the
        // rival's first fight (1) scripted.
        val pt = dump("pt_after.bin")
        val ss = dump("ss_field1.bin")
        assumeTrue("no Gen 4 dumps", pt != null && ss != null)
        fun game(code: String) = NdsFeed.gameFor(File.createTempFile("gen4", ".nds").apply {
            deleteOnExit(); writeBytes(ByteArray(12) + code.toByteArray())
        })!!
        val plat = game("CPUE")
        assertEquals(listOf(true, true, false, false), listOf(96, 736, 261, 470).map { NdsFeed.trainerBeaten(pt!!, plat, it) })
        val hgss = game("IPGE")
        assertEquals(listOf(true, false), listOf(4, 1).map { NdsFeed.trainerBeaten(ss!!, hgss, it) })
    }

    @Test
    fun blackZoneFollowsTheMap() {
        // the same save, walked from the Gear Station's platform (68) up the escalator into its hall (66)
        val platform = dump("bw_gear.bin")
        val hall = dump("bw_out4.bin")
        assumeTrue("no Black dumps", platform != null && hall != null)
        fun zone(ram: ByteArray) = (ram[0x2350B0].toInt() and 0xFF) or ((ram[0x2350B1].toInt() and 0xFF) shl 8)
        assertEquals(68, zone(platform!!))
        assertEquals(66, zone(hall!!))
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

    @Test
    fun platinumWildBattleBlocks() {
        // Platinum (US), 100% save, a wild Snubbull (Lv 19) against the Giratina (Lv 1); then one hit each way
        val game = NdsFeed.Game("pt", 0xB4, 0x1294, base = 0x02101D40, gen4 = true, tid = 0x8C)   // the save's own ID: the Giratina is traded
        val before = dump("pt_wild1.bin")
        val after = dump("pt_wild2.bin")
        assumeTrue("no Platinum battle dumps", before != null && after != null)
        for ((ram, foeHp, atkStage) in listOf(Triple(before!!, 56, -1), Triple(after!!, 55, -3))) {
            val party = NdsMons.party(ram, NdsFeed.partyAddr(ram, game), gen4 = true)
            val sides = NdsMons.gen4Sides(ram, party)
            assertEquals(487, sides.player.single().species)                  // Giratina, slot 0
            val foe = sides.foe.single()
            assertEquals(209, foe.species)                                     // Snubbull, slot 1
            assertEquals(19, foe.level)
            assertEquals(56, foe.maxHp)
            assertEquals(foeHp, foe.hp)                                         // 56, then 55 after the hit
            assertEquals(22, foe.ability)                                       // Intimidate
            assertEquals(atkStage, sides.player.single().stages!![0])          // Intimidate, then a stat-lowering move
            assertEquals(true, NdsFeed.inBattle(ram, game))
            assertEquals(true, NdsFeed.wildBattle(ram, game))                  // Platinum's battle screen takes the shortcuts
            val state = NdsFeed.state(ram, game)!!
            assertEquals(1, state.getInt("inBattle"))
            assertEquals(0, state.getInt("battleType"))                        // wild: the foe carries the player's ID
            assertEquals(209, state.getJSONArray("battlersDec").getJSONObject(1).getInt("sp"))
        }
        // after the battle (the Giratina fainted, back on the field): no battle left over
        dump("pt_after.bin")?.let { ram -> assertEquals(false, NdsFeed.inBattle(ram, game)) }
    }

    @Test
    fun platinumBestBallFromItsOwnPocket() {
        // a wild Ralts; the bag's Poke Balls pocket: Poke x1, Premier x1, Nest x49, Great x4, Quick x253, Master x1
        val game = NdsFeed.Game("pt", 0xB4, 0x1294, bag = 0xD00, base = 0x02101D40, gen4 = true, tid = 0x8C, bagSlots = 15)
        val ram = dump("pt_wild3.bin")
        assumeTrue("no pt_wild3.bin", ram != null)
        val bag = NdsFeed.bagAddr(ram!!, game)
        assertEquals(2, NdsShortcuts.bestBall(ram, bag, game.bagSlots))         // Ultra (7th; Master never thrown)
        val taps = NdsShortcuts.ball(ram, bag, game.bagSlots)!!
        assertEquals(5, taps.size)                                                // BAG, POKE BALLS, next page, slot, USE
        assertEquals(64, taps[3].x)                                               // page 2's first slot: checked on
        assertEquals(32, taps[3].y)                                               // screen ("used one Ultra Ball!")
        assertEquals(true, NdsFeed.wildBattle(ram, game))
    }

    @Test
    fun platinumRepelCounter() {
        // field, before any Repel; a Repel just used; about ten steps on
        val game = NdsFeed.Game("pt", 0xB4, 0x1294, repel = 0x8087, base = 0x02101D40, gen4 = true, tid = 0x8C)
        val dumps = listOf("pt_after", "pt_rep1", "pt_rep2").map { dump("$it.bin") }
        assumeTrue("no Platinum repel dumps", dumps.all { it != null })
        assertEquals(listOf(0, 100, 89), dumps.map { NdsFeed.repelSteps(it!!, game) })
    }

    @Test
    fun platinumEncounterRatesGoToZeroAndBack() {
        // Platinum on map 356 (encounter file 150): its table is loaded at 0x022A15A8, grass rate 30
        val pt = rom("Pokémon Platinum Version.nds")
        val ram = dump("pt_rep2.bin")
        assumeTrue("no Platinum ROM / dump", pt != null && ram != null)
        val game = NdsFeed.Game("pt", 0xB4, 0x1294, base = 0x02101D40, gen4 = true, tid = 0x8C)
        val copy = ram!!.copyOf()
        val write = { a: Int, bytes: ByteArray -> System.arraycopy(bytes, 0, copy, a - NdsMons.RAM_BASE, bytes.size); true }
        val enc = NdsEncounters(pt!!, game)
        assertEquals(true, enc.supported)
        val zoneAt = NdsFeed.zoneAddr(copy, game) - NdsMons.RAM_BASE
        val zone = (copy[zoneAt].toInt() and 0xFF) or ((copy[zoneAt + 1].toInt() and 0xFF) shl 8)
        assertEquals(356, zone)
        fun rate() = (copy[0x2A15A8].toInt() and 0xFF)
        assertEquals(30, rate())
        assertEquals(true, enc.block(copy, zone, write))
        assertEquals(0, rate())
        assertEquals(true, enc.block(copy, zone, write))           // found again with its rate already 0
        enc.restore(copy, zone, write)
        assertEquals(30, rate())
        // nothing else in RAM changed but the three rates
        val diff = (ram.indices).count { ram[it] != copy[it] }
        assertEquals(0, diff)
    }

    @Test
    fun black2EncounterRatesGoToZeroAndBack() {
        // Black 2 on Route 19 (map 437, encounter file 128): rates at 0x0223B7C0, then the slots
        val b2 = rom("Pokémon Black Version 2.nds")
        val ram = dump("b2_wild2.bin")
        val town = dump("b2_tepig.bin")                              // Aspertia City: an empty table
        assumeTrue("no Black 2 ROM / dumps", b2 != null && ram != null && town != null)
        val game = NdsFeed.Game("b2", 0x0221E3EC, 0x0221EAE8)
        val copy = ram!!.copyOf()
        val write = { a: Int, bytes: ByteArray -> System.arraycopy(bytes, 0, copy, a - NdsMons.RAM_BASE, bytes.size); true }
        val enc = NdsEncounters(b2!!, game)
        assertEquals(true, enc.supported)
        val zone = NdsFeed.zone(copy, game)
        assertEquals(437, zone)
        fun rates() = (0 until 8).map { copy[0x23B7C0 + it].toInt() and 0xFF }
        assertEquals(listOf(1, 0, 1, 10, 1, 50, 1, 128), rates())
        assertEquals(true, enc.block(copy, zone, write))
        assertEquals(listOf(0, 0, 0, 0, 0, 50, 1, 128), rates())          // fishing and the flag kept
        enc.restore(copy, zone, write)
        assertEquals(listOf(1, 0, 1, 10, 1, 50, 1, 128), rates())
        assertEquals(0, ram.indices.count { ram[it] != copy[it] })
        // a town with no wild Pokemon: nothing found, nothing written
        val t = town!!.copyOf()
        assertEquals(false, enc.block(t, NdsFeed.zone(t, game)) { _, _ -> error("wrote to a town") })
    }

    @Test
    fun gen4BadgesOnFinishedSaves() {
        // both saves have every badge; the byte follows the trainer card's money
        val pt = NdsFeed.Game("pt", 0xB4, 0x1294, badges = 0x96, base = 0x02101D40, gen4 = true, tid = 0x8C)
        val hgss = NdsFeed.Game("hgss", 0xA8, 0x1244, badges = 0x8E, base = 0x02111880, gen4 = true, tid = 0x84)
        val p = dump("pt_out.bin")
        val ss = dump("ss_field1.bin")
        assumeTrue("no Platinum / SoulSilver dumps", p != null && ss != null)
        assertEquals(0xFF, NdsFeed.badges(p!!, pt))
        assertEquals(0xFF, NdsFeed.badges(ss!!, hgss))
        assertEquals(true, NdsFeed.state(p, pt)!!.getString("flags").endsWith("ff"))
    }

    @Test
    fun soulSilverRepelAndBalls() {
        // field before a Repel (5 poked into the bag), the Repel just used, a bike ride on; the bag's balls: one Master
        val dumps = listOf("ssr1", "ssr2", "ssr3").map { dump("$it.bin") }
        assumeTrue("no SoulSilver repel dumps", dumps.all { it != null })
        val game = NdsFeed.gameFor(File.createTempFile("hgss", ".nds").apply { deleteOnExit(); writeBytes(ByteArray(12) + "IPGE".toByteArray()) })!!
        assertEquals(listOf(0, 100, 77), dumps.map { NdsFeed.repelSteps(it!!, game) })
        val ram = dumps[0]!!
        val o = NdsFeed.bagAddr(ram, game) - NdsMons.RAM_BASE
        assertEquals(listOf(1, 1, 0), listOf(o, o + 2, o + 4).map { (ram[it].toInt() and 0xFF) or ((ram[it + 1].toInt() and 0xFF) shl 8) })
        assertEquals(null, NdsShortcuts.bestBall(ram, NdsFeed.bagAddr(ram, game), game.bagSlots))   // a Master is never thrown
    }

    @Test
    fun soulSilverEncounterRatesGoToZeroAndBack() {
        // SoulSilver in a town by the water (map 56, encounter file 100: surf only, so no grass slots to match on)
        val ss = rom("Pokémon SoulSilver Version.nds")
        val ram = dump("ssr3.bin")
        assumeTrue("no SoulSilver ROM / dump", ss != null && ram != null)
        val game = NdsFeed.gameFor(File.createTempFile("hgss", ".nds").apply { deleteOnExit(); writeBytes(ByteArray(12) + "IPGE".toByteArray()) })!!
        val copy = ram!!.copyOf()
        val write = { a: Int, bytes: ByteArray -> System.arraycopy(bytes, 0, copy, a - NdsMons.RAM_BASE, bytes.size); true }
        val enc = NdsEncounters(ss!!, game)
        assertEquals(true, enc.supported)
        val zone = NdsFeed.zone(copy, game)
        assertEquals(56, zone)
        fun rates() = (0 until 6).map { copy[0x2A1CA0 + it].toInt() and 0xFF }
        assertEquals(listOf(0, 15, 0, 25, 50, 75), rates())
        assertEquals(true, enc.block(copy, zone, write))
        assertEquals(listOf(0, 0, 0, 25, 50, 75), rates())                // the rods kept
        assertEquals(true, enc.block(copy, zone, write))
        enc.restore(copy, zone, write)
        assertEquals(listOf(0, 15, 0, 25, 50, 75), rates())
        assertEquals(0, ram.indices.count { ram[it] != copy[it] })
    }

    @Test
    fun soulSilverTrainerBattles() {
        // Route 15: a Bird Keeper's Doduo (Lv 39), then another's Noctowl (Lv 48), against the Typhlosion; the field between
        val game = NdsFeed.gameFor(File.createTempFile("hgss", ".nds").apply { deleteOnExit(); writeBytes(ByteArray(12) + "IPGE".toByteArray()) })!!
        val dumps = listOf("ss_trainer1", "ss_trainer2", "ssg0").map { dump("$it.bin") }
        assumeTrue("no SoulSilver battle dumps", dumps.all { it != null })
        for ((ram, foe) in listOf(dumps[0]!! to Triple(84, 39, 78), dumps[1]!! to Triple(164, 48, 156))) {
            val party = NdsMons.party(ram, NdsFeed.partyAddr(ram, game), gen4 = true)
            val sides = NdsFeed.sides(ram, game, party)
            assertEquals(157, sides.player.single().species)
            val f = sides.foe.single()
            assertEquals(foe, Triple(f.species, f.level, f.maxHp))
            assertEquals(true, NdsFeed.inBattle(ram, game))
            assertEquals(8, NdsFeed.state(ram, game)!!.getInt("battleType"))  // a trainer's: their own ID
            assertEquals(foe.first, NdsFeed.state(ram, game)!!.getJSONArray("enemyDec").getJSONObject(0).getInt("sp"))
        }
        assertEquals(false, NdsFeed.inBattle(dumps[2]!!, game))
        // the walker's wild Muk (Lv 30) on the same route: wild, so the R / L shortcuts take it
        dump("ss_wild1.bin")?.let { ram ->
            val party = NdsMons.party(ram, NdsFeed.partyAddr(ram, game), gen4 = true)
            assertEquals(89 to 30, NdsFeed.sides(ram, game, party).foe.single().let { it.species to it.level })
            assertEquals(true, NdsFeed.wildBattle(ram, game))
            assertEquals(0, NdsFeed.state(ram, game)!!.getInt("battleType"))
        }
        dumps.forEach { assertEquals(false, NdsFeed.wildBattle(it!!, game)) }
    }

    @Test
    fun gen4Position() {
        // SoulSilver on Route 15: up, up, then left (each hold went 1 to 3 tiles); Platinum in a Pokemon Center
        val hgss = NdsFeed.gameFor(File.createTempFile("hgss", ".nds").apply { deleteOnExit(); writeBytes(ByteArray(12) + "IPGE".toByteArray()) })!!
        val dumps = (0..3).map { dump("ss_pos$it.bin") }
        assumeTrue("no SoulSilver position dumps", dumps.all { it != null })
        assertEquals(listOf(1169 to 435, 1169 to 433, 1169 to 430, 1167 to 430), dumps.map { NdsFeed.position(it!!, hgss) })
        assertEquals(435, NdsFeed.state(dumps[0]!!, hgss)!!.getInt("y"))
        dump("pt_pc.bin")?.let { ram ->
            val pt = NdsFeed.Game("pt", 0xB4, 0x1294, base = 0x02101D40, gen4 = true)
            assertEquals(12 to 4, NdsFeed.position(ram, pt))
        }
        // Black 2 on a route: up, up, left, left; then a gym inside
        val b2 = NdsFeed.gameFor(File.createTempFile("bw2", ".nds").apply { deleteOnExit(); writeBytes(ByteArray(12) + "IREO".toByteArray()) })!!
        val walk = (0..4).map { dump("b2p$it.bin") }
        if (walk.all { it != null }) assertEquals(listOf(60 to 697, 60 to 696, 60 to 694, 59 to 694, 58 to 694), walk.map { NdsFeed.position(it!!, b2) })
        dump("b2_gym1_before.bin")?.let { assertEquals(15 to 19, NdsFeed.position(it, b2)) }
        // Black at the Gear Station: down, down, left
        val b1 = NdsFeed.gameFor(File.createTempFile("bw1", ".nds").apply { deleteOnExit(); writeBytes(ByteArray(12) + "IRBO".toByteArray()) })!!
        val station = (0..3).map { dump("b1_pos$it.bin") }
        if (station.all { it != null }) assertEquals(listOf(11 to 15, 11 to 16, 11 to 17, 10 to 17), station.map { NdsFeed.position(it!!, b1) })
    }

    @Test
    fun gen4EventVariables() {
        // story fights move a variable on (pret's scripts): the finished saves read past each one
        fun game(code: String, name: String = "x") = NdsFeed.gameFor(File.createTempFile(name, ".nds").apply {
            deleteOnExit(); writeBytes(ByteArray(12) + code.toByteArray()) })!!
        fun v(st: org.json.JSONObject, id: Int): Int {
            val h = st.getString("vars"); val i = (id - 0x4000) * 4
            return Integer.parseInt(h.substring(i + 2, i + 4) + h.substring(i, i + 2), 16)
        }
        dump("pt_after.bin")?.let { ram ->
            val st = NdsFeed.state(ram, game("CPUE", "plat"))!!
            assertEquals(0x120 * 4, st.getString("vars").length)
            assertEquals(14, v(st, 0x4055))   // Distortion World: past the Cyrus fight (10) and Giratina
            assertEquals(2, v(st, 0x407B))    // Route 209 gate: rival beaten
            assertEquals(6, v(st, 0x407C))    // Pastoria: rival (2), the gym, the lakes
        }
        dump("ss_field1.bin")?.let { ram ->
            val st = NdsFeed.state(ram, game("IPGE", "hgss"))!!
            assertEquals(0x170 * 4, st.getString("vars").length)
            assertEquals(4, v(st, 0x4073))    // Cherrygrove: the first rival fight
            assertEquals(2, v(st, 0x4075))    // Azalea: the second
            assertEquals(10, v(st, 0x40AC))   // the Rocket Hideout done (Petrel 4, Ariana with Lance 9)
        }
    }
}
