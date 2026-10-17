package com.pokedaisy.app.companion.ui.theme

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * SETTINGS > BACKGROUND (both screens): what the companion draws behind its tabs. GAME ART is the game's own
 * party-menu backdrop ([GameBackdrop]); the others are plain colours, dark enough for the white windows and
 * light text, and RAINBOW a soft gradient to match a Rainbow Thor. Saved in the app's prefs, Compose state so
 * a pick shows at once.
 */
object CompanionBackground {
    enum class Choice(val key: String, val label: String, val colors: List<Color>) {
        GAME("game", "GAME ART", emptyList()),
        BLACK("black", "BLACK", listOf(Color(0xFF0C0D11))),
        NAVY("navy", "NAVY", listOf(Color(0xFF13213F))),
        PURPLE("purple", "PURPLE", listOf(Color(0xFF2A1840))),
        TEAL("teal", "TEAL", listOf(Color(0xFF0F3B3A))),
        RAINBOW(
            "rainbow", "RAINBOW",
            listOf(Color(0xFF7A3B52), Color(0xFF7A5A33), Color(0xFF5E7035), Color(0xFF2F6B60), Color(0xFF34507E), Color(0xFF5B3E80)),
        ),
    }

    private const val PREFS = "pokedaisy"
    private const val KEY = "companion_background"

    var choice by mutableStateOf(Choice.GAME)

    fun load(context: Context) {
        val key = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, Choice.GAME.key)
        choice = Choice.entries.firstOrNull { it.key == key } ?: Choice.GAME
    }

    fun set(context: Context, pick: Choice) {
        choice = pick
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, pick.key).apply()
    }

    fun byLabel(label: String) = Choice.entries.firstOrNull { it.label == label } ?: Choice.GAME

    /** The picked colour (or gradient); not called for GAME ART. */
    @Composable
    fun Backdrop(modifier: Modifier = Modifier.fillMaxSize()) {
        val c = choice.colors
        val fill = if (c.size > 1) Modifier.background(Brush.linearGradient(c)) else Modifier.background(c.firstOrNull() ?: Color.Black)
        Box(modifier.then(fill))
    }
}
