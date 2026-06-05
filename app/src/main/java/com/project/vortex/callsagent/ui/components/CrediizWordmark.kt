package com.project.vortex.callsagent.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.project.vortex.callsagent.ui.theme.CrediizBlue
import com.project.vortex.callsagent.ui.theme.CrediizGreen
import com.project.vortex.callsagent.ui.theme.CrediizInk

/**
 * The Crediiz wordmark lockup: "Cred" + two brand pillars (the two "i"s,
 * blue then green) + "z", rendered as a single typographic unit.
 *
 * Why a [Canvas] + [rememberTextMeasurer] instead of a [androidx.compose.foundation.layout.Row]
 * of Text + custom shapes: the two pillars must sit exactly on the text
 * baseline and align their "dot" with the x-height of the surrounding
 * lowercase letters. Measuring the text fragments and drawing everything
 * in one pass gives pixel-accurate baseline control that a Row of
 * baseline-aligned children can't guarantee across fonts and densities.
 *
 * The pillar geometry is expressed as fractions of the font size (see the
 * `*_RATIO` constants) so the mark scales cleanly to any [fontSize] — splash
 * uses a large size, login a smaller one.
 *
 * @param textColor color of the "Cred"/"z" letters. The pillars are always
 *   the brand blue/green; only the surrounding text recolors, so the mark
 *   works on both light surfaces ([CrediizInk]) and dark ones (white).
 */
@Composable
fun CrediizWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 44.sp,
    textColor: Color = CrediizInk,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val style = remember(fontSize, textColor) {
        TextStyle(
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
        )
    }

    // Measure the two text fragments that flank the pillars. Both share the
    // same style, so they share line height and baseline.
    val leadLayout = remember(style) { measurer.measure("Cred", style) }
    val tailLayout = remember(style) { measurer.measure("z", style) }

    val f = with(density) { fontSize.toPx() }

    // Pillar metrics, all derived from the font size.
    val pillarWidth = f * PILLAR_WIDTH_RATIO
    val bodyHeight = f * BODY_HEIGHT_RATIO
    val headDiameter = f * HEAD_DIAMETER_RATIO
    val headGap = f * HEAD_GAP_RATIO
    val pillarAdvance = pillarWidth + f * PILLAR_SIDE_BEARING_RATIO
    val flankGap = f * FLANK_GAP_RATIO

    val leadWidth = leadLayout.size.width.toFloat()
    val tailWidth = tailLayout.size.width.toFloat()
    val lineHeight = leadLayout.size.height.toFloat()
    val baselineY = leadLayout.firstBaseline

    // Horizontal layout: Cred | pillar1 | pillar2 | z
    val pillarsStartX = leadWidth + flankGap
    val pillar1CenterX = pillarsStartX + pillarAdvance / 2f
    val pillar2CenterX = pillarsStartX + pillarAdvance + pillarAdvance / 2f
    val tailStartX = pillarsStartX + pillarAdvance * 2f + flankGap
    val totalWidth = tailStartX + tailWidth

    val widthDp = with(density) { totalWidth.toDp() }
    val heightDp = with(density) { lineHeight.toDp() }

    Canvas(modifier = modifier.size(widthDp, heightDp)) {
        drawText(leadLayout, topLeft = Offset.Zero)
        drawText(tailLayout, topLeft = Offset(tailStartX, 0f))

        drawPillar(pillar1CenterX, baselineY, pillarWidth, bodyHeight, headDiameter, headGap, CrediizBlue)
        drawPillar(pillar2CenterX, baselineY, pillarWidth, bodyHeight, headDiameter, headGap, CrediizGreen)
    }
}

/**
 * Draws one brand pillar: a capsule "body" rising from the baseline plus a
 * detached circular "dot" above it, echoing a lowercase "i".
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPillar(
    centerX: Float,
    baselineY: Float,
    width: Float,
    bodyHeight: Float,
    headDiameter: Float,
    headGap: Float,
    color: Color,
) {
    // Body: a vertical capsule sitting on the baseline.
    drawRoundRect(
        color = color,
        topLeft = Offset(centerX - width / 2f, baselineY - bodyHeight),
        size = Size(width, bodyHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(width / 2f),
    )
    // Dot: a circle floating above the body.
    val headCenterY = baselineY - bodyHeight - headGap - headDiameter / 2f
    drawCircle(
        color = color,
        radius = headDiameter / 2f,
        center = Offset(centerX, headCenterY),
    )
}

// Pillar geometry as a fraction of the font size. Tuned to align the dot near
// the cap height and the body with the lowercase x-height; adjust here if the
// pillars read too thin/thick or too tall against the letters.
private const val PILLAR_WIDTH_RATIO = 0.17f
private const val BODY_HEIGHT_RATIO = 0.52f
private const val HEAD_DIAMETER_RATIO = 0.185f
private const val HEAD_GAP_RATIO = 0.055f
private const val PILLAR_SIDE_BEARING_RATIO = 0.085f
private const val FLANK_GAP_RATIO = 0.03f
