package com.pokedaisy.app.companion.ui

import com.pokedaisy.app.companion.GamePickActions
import com.pokedaisy.app.companion.data.BestEffort
import com.pokedaisy.app.companion.data.GameOption
import com.pokedaisy.app.companion.data.GamePickerView

/** WHICH GAME IS THIS? for screenshot tests: an Emerald-based ROM whose names are SoulGold's. */
object SamplePicker {
    val actions = GamePickActions(pick = {}, none = {}, keep = {}, seen = {})

    fun picker(
        miss: BestEffort.Miss? = null, likely: Boolean = true, declined: Boolean = false, current: String? = null,
    ) = GamePickerView(
        listOf(
            GameOption("SOULGOLD_V1_2", "Pokémon SoulGold v1.2", likely = likely, readsParty = false),
            GameOption("SOULGOLD", "Pokémon SoulGold v1.1", likely = likely, readsParty = false),
            GameOption("HEART_AND_SOUL", "Pokémon Heart and Soul", likely = false, readsParty = false),
            GameOption("LAZARUS", "Pokémon Lazarus", likely = false, readsParty = false),
            GameOption("EMERALD", "Pokémon Emerald", likely = false, readsParty = false),
            GameOption("EMERALD_ROGUE", "Emerald Rogue", likely = false, readsParty = false),
            GameOption("TMT2", "Too Many Types 2", likely = false, readsParty = false),
        ),
        current = current, miss = miss, declined = declined,
    )
}
