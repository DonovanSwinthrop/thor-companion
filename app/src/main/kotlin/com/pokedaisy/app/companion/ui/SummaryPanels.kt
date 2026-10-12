package com.pokedaisy.app.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The summary screens' containers, while their new look is picked (the pages read as crowded):
 * each option renders in ui-preview's `*-look-*` shots. The chosen one stays, this knob goes.
 * - BANDS: one white window, each block headed by a blue title band (Emerald's summary headers).
 * - PANELS: one white window, each block a grey inset panel, the moves a card each.
 * - WINDOWS: no outer window - each block its own white OPTION window over the backdrop, red titles.
 */
internal enum class SummaryLook { CURRENT, BANDS, PANELS, WINDOWS }

internal var summaryLook = SummaryLook.BANDS

/** A block's title band: the help bar's blue with a darker line under it, white text and its shadow. */
@Composable
internal fun SectionBand(
    title: String,
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val u = m.u
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val px = u.toPx()
                drawPixelRoundRect(BandShade, radius = 2 * px, step = px)
                drawPixelRoundRect(OptionColors.hintBar, size = Size(size.width, size.height - px), radius = 2 * px, step = px)
            }
            .padding(start = u * 5, end = u * 5, top = u * 2, bottom = u * 3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GbaText(title, OptionColors.hintText, OptionColors.hintShadow, m, modifier = Modifier.weight(1f, fill = false))
        trailing()
    }
}

private val BandShade get() = if (OptionColors.dark) Color(0xFF073A60) else Color(0xFF00528A)

/** An inset panel: a 1-GBA-pixel grey line round [fill], pixel corners. */
@Composable
internal fun SummaryPanel(
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    fill: Color = OptionColors.listFill,
    padding: Dp = m.u * 4,
    content: @Composable BoxScope.() -> Unit,
) {
    val u = m.u
    Box(
        modifier
            .drawBehind {
                val px = u.toPx()
                drawPixelRoundRect(SeparatorDark, radius = 3 * px, step = px)
                drawPixelRoundRect(fill, Offset(px, px), Size(size.width - 2 * px, size.height - 2 * px), radius = 2 * px, step = px)
            }
            .padding(padding + u),
        content = content,
    )
}

/** A block's title in a window: red, like SETTINGS' group titles (Gen 1: a black band). */
@Composable
internal fun BlockTitle(title: String, m: GbaTextMetrics, modifier: Modifier = Modifier) {
    val band = OptionColors.groupTitleFill
    if (band == null) {
        GbaText(title, OptionColors.value, OptionColors.valueShadow, m, modifier = modifier)
    } else {
        GbaText(
            title, OptionColors.groupTitleText, OptionColors.groupTitleShadow, m,
            modifier = modifier
                .fillMaxWidth()
                .drawBehind { drawPixelRoundRect(band, radius = m.u.toPx() * 2, step = m.u.toPx()) }
                .padding(horizontal = m.u * 6, vertical = m.u * 2),
        )
    }
}

/** The bag list's dashed rule, between rows inside one window. */
@Composable
internal fun DashedRule(m: GbaTextMetrics, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(m.u * 2).drawBehind { drawRowDivider(OptionColors.divider, m.u.toPx(), 0f) })
}

/** The page's two columns in [look]: inside one white window, or side by side as windows of their own. */
@Composable
internal fun SummaryColumns(
    look: SummaryLook,
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
) {
    if (look == SummaryLook.WINDOWS) {
        Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            left(Modifier.weight(1f).fillMaxHeight())
            right(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        OptionListWindow(m, modifier.fillMaxSize(), fill = OptionColors.titleFill) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(m.u * 6)) {
                left(Modifier.weight(1f).fillMaxHeight())
                right(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/**
 * One block of a summary page in [look], [title] (null: none) over [content]. [fill]: the block
 * takes the height it's given and so does its content (a weighted child); else it wraps. [boxed]
 * false: the content draws its own containers (the move cards), so PANELS adds no panel round it.
 */
@Composable
internal fun SummaryBlock(
    look: SummaryLook,
    title: String?,
    m: GbaTextMetrics,
    modifier: Modifier = Modifier,
    /** The title's size: [m] unless set (the name band is bigger than the block titles). */
    titleMetrics: GbaTextMetrics = m,
    fill: Boolean = false,
    boxed: Boolean = true,
    titleTrailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val u = m.u
    val body = if (fill) Modifier.fillMaxWidth().fillMaxHeight() else Modifier.fillMaxWidth()
    when (look) {
        SummaryLook.BANDS -> Column(modifier) {
            if (title != null) {
                SectionBand(title, titleMetrics, trailing = titleTrailing)
                Spacer(Modifier.height(u * 4))
            }
            Column(body.padding(horizontal = u * 3), content = content)
        }
        SummaryLook.PANELS -> Column(modifier) {
            if (title != null) {
                Row(Modifier.padding(start = u * 3, bottom = u * 2), verticalAlignment = Alignment.CenterVertically) {
                    BlockTitle(title, titleMetrics)
                    titleTrailing()
                }
            }
            if (boxed) SummaryPanel(m, body) { Column(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), content = content) }
            else Column(body, content = content)
        }
        else -> OptionListWindow(m, modifier, fill = OptionColors.titleFill) {
            Column(body) {
                if (title != null) {
                    Row(Modifier.padding(bottom = u * 3), verticalAlignment = Alignment.CenterVertically) {
                        BlockTitle(title, titleMetrics)
                        titleTrailing()
                    }
                }
                Column(Modifier.fillMaxWidth().then(if (fill) Modifier.weight(1f) else Modifier), content = content)
            }
        }
    }
}
