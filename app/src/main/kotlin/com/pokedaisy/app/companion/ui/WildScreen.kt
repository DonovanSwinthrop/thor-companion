package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pokedaisy.app.companion.CompanionSettings
import com.pokedaisy.app.companion.ItemUse
import com.pokedaisy.app.companion.data.SnapshotView
import com.pokedaisy.app.companion.data.gameCase
import com.pokedaisy.app.companion.data.speciesName
import com.pokedaisy.app.companion.data.spriteAsset
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.i18n.tr

/**
 * WILD: this map's wild Pokémon by method (icon, name, levels, how often, a ball once caught), what the
 * HUD's WILD panel shows over the game on one screen. With a second screen the HUD stays off (the top screen is the
 * game's), so this tab is where it lives - with the steps a working REPEL has left, which the HUD shows by its balls, and
 * INFINITE REPEL (the user's call: no random encounters from here), which is SETTINGS > WILD ENCOUNTERS OFF by another name.
 * Offered only where the game has ROM tables for its wild Pokémon ([rememberWildHere] non-null).
 */
@Composable
fun WildScreen(snapshot: SnapshotView, wild: List<HudWild>, itemUse: ItemUse?, settings: CompanionSettings?, modifier: Modifier = Modifier) {
    val m = rememberGbaTextMetrics()
    val small = rememberGbaTextMetrics(textScale = 1f)
    val u = m.u
    val repel = itemUse?.repelVar?.let { snapshot.progress?.variable(it) } ?: 0
    val species = wild.distinctBy { it.species }
    // Settings aren't Compose state: the switch keeps its own copy, read again each time the tab opens.
    val repelSwitch = settings?.takeIf { it.wildEncountersSupported }
    var infinite by remember(repelSwitch) { mutableStateOf(repelSwitch?.wildEncounters == 0) }
    Column(modifier.fillMaxSize()) {
        OptionTitleWindow(tk("WILD POKéMON"), m, trailing = gameCase(snapshot.location.mapSecName).uppercase().replace('É', 'é'))
        Spacer(Modifier.height(u * 4))
        if (repelSwitch != null || repel > 0) {
            OptionListWindow(m, Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(u * 2)) {
                    if (repelSwitch != null) {
                        OptionLine(
                            tk("INFINITE REPEL"), if (infinite) tk("ON") else tk("OFF"), selected = false, m,
                            divider = repel > 0,
                        ) {
                            repelSwitch.setWildEncounters(if (infinite) 100 else 0)
                            infinite = repelSwitch.wildEncounters == 0
                        }
                    }
                    // The game's own REPEL, while one works.
                    if (repel > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u * 4)) {
                        ItemIcon(spriteAsset("items", REPEL_ITEM), u * 24)
                        GbaText(tr("REPEL"), OptionColors.label, OptionColors.labelShadow, m, Modifier.weight(1f))
                        GbaText(tr("{0} steps", repel), OptionColors.value, OptionColors.valueShadow, m)
                    }
                }
            }
            Spacer(Modifier.height(u * 4))
        }
        OptionListWindow(m, Modifier.fillMaxWidth().weight(1f)) {
            val scroll = rememberScrollState()
            Column(
                Modifier.fillMaxSize().dpadScroll(scroll, PixelRoundedShape(u * 2), m.lineHeight * 3).verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(u),
            ) {
                if (wild.isEmpty()) GbaText(tr("No wild POKéMON on this map."), OptionColors.label, OptionColors.labelShadow, m)
                wild.groupBy { it.method }.forEach { (method, list) ->
                    GbaText(tr(method), OptionColors.value, OptionColors.valueShadow, small, Modifier.padding(top = u * 2))
                    list.forEach { w -> WildRow(w, m, small) }
                }
            }
        }
        if (species.isNotEmpty()) {
            Spacer(Modifier.height(u * 2))
            BackdropText(tr("{0} of {1} caught here.", species.count { it.caught }, species.size), small, Modifier.padding(start = u * 4))
        }
    }
}

@Composable
private fun WildRow(w: HudWild, m: GbaTextMetrics, small: GbaTextMetrics) {
    val u = m.u
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // Every one named, seen or not (the user's call, 2026-10-19: no ??? here).
        Box(Modifier.size(u * 24), contentAlignment = Alignment.Center) { SpeciesIcon(spriteAsset("pokemon", w.species), u * 24) }
        Spacer(Modifier.width(u * 4))
        GbaText(speciesName(w.species), OptionColors.label, OptionColors.labelShadow, m, Modifier.weight(1f))
        if (w.caught) {
            PixelArt(PixelIcons.pokeBall, CAUGHT_BALL, Modifier.size(u * 9))
            Spacer(Modifier.width(u * 6))
        }
        val lv = if (w.minLevel == w.maxLevel) "${w.minLevel}" else "${w.minLevel}-${w.maxLevel}"
        GbaText(tr("Lv{0}", lv), OptionColors.label, OptionColors.labelShadow, small, Modifier.width(u * 44))
        Box(Modifier.width(u * 38), contentAlignment = Alignment.CenterEnd) {
            GbaText("${w.percent}%", OptionColors.value, OptionColors.valueShadow, small)
        }
    }
}

/** The plain REPEL's icon (the game doesn't keep which kind is working), as the HUD's counter has it. */
private const val REPEL_ITEM = 86
