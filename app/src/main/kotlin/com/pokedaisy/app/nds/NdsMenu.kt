package com.pokedaisy.app.nds

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pokedaisy.app.companion.i18n.tk
import com.pokedaisy.app.companion.ui.OptionListWindow
import com.pokedaisy.app.companion.ui.OptionRows
import com.pokedaisy.app.companion.ui.OptionTitleWindow
import com.pokedaisy.app.companion.ui.rememberGbaTextMetrics

/** What the DS pause menu offers, in its order. */
enum class NdsMenuItem(val label: String) {
    RESUME("RESUME"),
    SAVE_STATE("SAVE STATE"),
    LOAD_STATE("LOAD STATE"),
    FAST_FORWARD("FF"),
    HEAL_PARTY("HEAL PARTY"),
    TIME("GAME CLOCK"),
    SHORTCUTS("SHORTCUTS"),
    REPEL("INFINITE REPEL"),
    NATURE("NATURE"),
    SWAP("SWAP SCREENS"),
    CLOSE("CLOSE GAME"),
}

/**
 * The DS game's pause menu (BACK or L3): the OPTION look's title + list over the dimmed game, steered by the
 * D-pad ([selected], moved by [NdsActivity]) or by touch. [hasState] greys LOAD STATE out with no state saved.
 * The clock row shows the DS clock's [hour] as "< 14:00 >": LEFT / RIGHT step it, A or a tap adds an hour.
 */
@Composable
fun NdsMenu(title: String, items: List<NdsMenuItem>, selected: Int, fastForward: Boolean, hasState: Boolean, hour: Int, shortcuts: Boolean, repel: Boolean, onPick: (NdsMenuItem) -> Unit) {
    val m = rememberGbaTextMetrics()
    Box(
        Modifier.fillMaxSize().background(Color(0xB0000000))
            .clickable(interactionSource = MutableInteractionSource(), indication = null) { onPick(NdsMenuItem.RESUME) },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(0.6f)) {
            OptionTitleWindow(title, m, Modifier.fillMaxWidth())
            NdsList(
                rows = items.map { item ->
                    val value = when {
                        item == NdsMenuItem.FAST_FORWARD -> tk(if (fastForward) "ON" else "OFF")
                        item == NdsMenuItem.SHORTCUTS -> tk(if (shortcuts) "ON" else "OFF")
                        item == NdsMenuItem.REPEL -> tk(if (repel) "ON" else "OFF")
                        item == NdsMenuItem.TIME && hour >= 0 -> "< %02d:00 >".format(hour)
                        else -> null
                    }
                    Triple(tk(item.label), value) { onPick(item) }
                },
                selected = selected,
                enabled = { i -> items[i] != NdsMenuItem.LOAD_STATE || hasState },
            )
        }
    }
}

/**
 * The pause menu's rows in the OPTION list window, as many as fit the screen: the rest scroll, and the list follows the
 * D-pad's cursor ([selected]) so the white row stays in view.
 */
@Composable
private fun NdsList(rows: List<Triple<String, String?, () -> Unit>>, selected: Int, enabled: (Int) -> Boolean = { true }) {
    val m = rememberGbaTextMetrics()
    val rowH = m.rowHeight * 1.2f
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        // the window's own frame and the title above take some of the screen: about 3 rows' worth
        val fit = ((maxHeight - rowH * 3) / rowH).toInt().coerceIn(3, rows.size.coerceAtLeast(3))
        androidx.compose.runtime.LaunchedEffect(selected, fit) {
            val px = with(density) { rowH.toPx() }
            val top = (selected - fit + 1).coerceAtLeast(0) * px
            val bottom = selected * px
            if (scroll.value > bottom) scroll.animateScrollTo(bottom.toInt())
            else if (scroll.value < top) scroll.animateScrollTo(top.toInt())
        }
        OptionListWindow(m, Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().height(rowH * minOf(fit, rows.size)).verticalScroll(scroll)) {
                rows.forEachIndexed { i, (label, value, onClick) ->
                    com.pokedaisy.app.companion.ui.OptionLine(
                        label, value, selected = i == selected, m = m, height = rowH,
                        divider = i < rows.lastIndex, enabled = enabled(i), onClick = onClick,
                    )
                }
            }
        }
    }
}

/**
 * A list picked from in the pause menu (NATURE's party, then natures): [title] in the OPTION title window, the rows'
 * labels and values as the game names them, the D-pad's [selected] row white. A tap outside goes [onBack].
 */
@Composable
fun NdsPicker(title: String, rows: List<Pair<String, String?>>, selected: Int, onPick: (Int) -> Unit, onBack: () -> Unit) {
    val m = rememberGbaTextMetrics()
    Box(
        Modifier.fillMaxSize().background(Color(0xB0000000))
            .clickable(interactionSource = MutableInteractionSource(), indication = null) { onBack() },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(0.6f)) {
            OptionTitleWindow(title, m, Modifier.fillMaxWidth())
            NdsList(rows.mapIndexed { i, (label, value) -> Triple(label, value) { onPick(i) } }, selected)
        }
    }
}

/** The touch screen's RUN button: opens the RUN tab over the DS touch screen; BACK (the same place) closes it. */
@Composable
fun NdsRunButton(open: Boolean, onClick: () -> Unit) {
    val m = rememberGbaTextMetrics()
    Box(Modifier.padding(m.u * 2)) {
        com.pokedaisy.app.companion.ui.OptionButton(tk(if (open) "BACK" else "RUN TAB"), m, onClick, emphasis = !open)
    }
}
