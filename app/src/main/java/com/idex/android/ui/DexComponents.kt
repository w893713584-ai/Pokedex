package com.idex.android.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idex.android.R
import com.idex.android.data.AppLanguage
import kotlinx.coroutines.delay

object DexTheme {
    // Classic Retro Tokens
    val red = Color(0.70f, 0.075f, 0.105f)
    val ink = Color(0.12f, 0.17f, 0.13f)
    val lcd = Color(0.72f, 0.81f, 0.58f)
    val cream = Color(0.86f, 0.86f, 0.77f)
    val charcoal = Color(0.12f, 0.14f, 0.15f)
    val teal = Color(0.11f, 0.27f, 0.24f)

    // Anime Cel-Shaded Pokédex Palette
    val shellRed = Color(0xFFDC0A2D)
    val shellRedDark = Color(0xFFA0081F)
    val shellRedLight = Color(0xFFF2475E)
    val outline = Color(0xFF111111)
    val lensBlue = Color(0xFF28AAFD)
    val lensBlueCenter = Color(0xFF9BE0FF)
    val ledRed = Color(0xFFFF2A2A)
    val ledYellow = Color(0xFFFFDE00)
    val ledGreen = Color(0xFF32CB65)
    val bezelWhite = Color(0xFFE8E8E8)
    val screenOff = Color(0xFF1F2A24)
    val keyBlue = Color(0xFF3B8BE0)
    val keyDark = Color(0xFF2A2A2A)
    val screenBlack = Color(0xFF161616)

    val latin = FontFamily(Font(R.font.press_start_2p))
    val chinese = FontFamily(Font(R.font.fusion_pixel_zh))
    val japanese = FontFamily(Font(R.font.fusion_pixel_ja))

    fun family(l: AppLanguage) = when (l) {
        AppLanguage.EN -> latin
        AppLanguage.ZH -> chinese
        AppLanguage.JA -> japanese
    }
}

/**
 * Pixel text. Press Start 2P is optically large, so CJK fonts get +4sp like on iOS.
 * [latinOnly] forces Press Start 2P for numbers/units.
 */
@Composable
fun PixelText(
    text: String,
    size: Float,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    latinOnly: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val cjk = !latinOnly && language != AppLanguage.EN
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(
            fontFamily = if (latinOnly) DexTheme.latin else DexTheme.family(language),
            fontSize = (if (cjk) size + 4 else size).sp,
            lineHeight = lineHeight,
        ),
    )
}

/** Anime Cel-Shaded Pokédex Shell Surface with thick black outlines and crisp edge highlights. */
@Composable
fun ShellSurface(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(DexTheme.shellRed)
            .border(3.dp, DexTheme.outline)
    ) {
        // Cel-shaded diagonal shine overlay
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width * 0.35f, 0f)
                lineTo(0f, size.height * 0.45f)
                close()
            }
            drawPath(path, Color.White.copy(alpha = 0.06f))
        }
        // Inset border
        Box(
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .border(1.5.dp, DexTheme.shellRedLight.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        )
    }
}

@Composable
fun Screw() {
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color.Gray, Color.Black.copy(alpha = 0.8f))))
            .border(1.dp, DexTheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(7.dp, 1.5.dp)
                .rotate(-35f)
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(1.dp))
        )
    }
}

@Composable
fun Indicator(color: Color, size: Dp = 10.dp, lit: Boolean = true) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    if (lit) listOf(Color.White.copy(alpha = 0.95f), color, color.copy(alpha = 0.5f))
                    else listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.75f))
                )
            )
            .border(1.5.dp, DexTheme.outline, CircleShape)
    )
}

/** Signature Big Blue Eye Lens of the Pokédex */
@Composable
fun Lens(lit: Boolean, size: Dp = 64.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .border(3.dp, DexTheme.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        // Inner blue gradient
        Box(
            Modifier
                .fillMaxSize()
                .padding(size * 0.10f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        if (lit) listOf(Color(0xFFE8FFFF), DexTheme.lensBlueCenter, DexTheme.lensBlue, Color(0xFF0D6AA8))
                        else listOf(Color(0xFF8CE0FF), DexTheme.lensBlue, Color(0xFF095A92), Color(0xFF053252)),
                        center = Offset(size.value * 0.35f, size.value * 0.35f),
                    )
                )
                .border(2.dp, DexTheme.outline, CircleShape)
        )
        // White oval shine
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(size * 0.20f, size * 0.20f)
                .size(size * 0.30f, size * 0.16f)
                .rotate(-45f)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = if (lit) 0.95f else 0.75f))
        )
    }
}

/** Lens plus the three signature LEDs (Red, Yellow, Green) */
@Composable
fun ScannerLights(isScanning: Boolean, isSpeaking: Boolean, lensSize: Dp = 64.dp) {
    var active by remember { mutableIntStateOf(0) }
    val chasing = isScanning && !isSpeaking
    LaunchedEffect(chasing) {
        while (chasing) {
            delay(150)
            active = (active + 1) % 3
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Lens(lit = isSpeaking, size = lensSize)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = lensSize * 0.35f)) {
            Indicator(DexTheme.ledRed, size = 12.dp, lit = if (chasing) active == 0 else true)
            Indicator(DexTheme.ledYellow, size = 12.dp, lit = if (chasing) active == 1 else true)
            Indicator(DexTheme.ledGreen, size = 12.dp, lit = if (chasing) active == 2 else true)
        }
    }
}

/** Authentic Anime Directional Pad (✚) */
@Composable
fun DPad(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 100.dp,
) {
    val wingThickness = size * 0.34f
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        // Horizontal Wing
        Box(
            modifier = Modifier
                .size(width = size, height = wingThickness)
                .clip(RoundedCornerShape(4.dp))
                .background(DexTheme.keyDark)
                .border(2.5.dp, DexTheme.outline, RoundedCornerShape(4.dp))
        ) {
            // Left click area
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(size * 0.38f)
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onLeft),
                contentAlignment = Alignment.Center,
            ) {
                PixelText("◀", 7f, AppLanguage.EN, color = Color.White.copy(alpha = 0.6f))
            }
            // Right click area
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(size * 0.38f)
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onRight),
                contentAlignment = Alignment.Center,
            ) {
                PixelText("▶", 7f, AppLanguage.EN, color = Color.White.copy(alpha = 0.6f))
            }
        }

        // Vertical Wing
        Box(
            modifier = Modifier
                .size(width = wingThickness, height = size)
                .clip(RoundedCornerShape(4.dp))
                .background(DexTheme.keyDark)
                .border(2.5.dp, DexTheme.outline, RoundedCornerShape(4.dp))
        ) {
            // Up click area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(size * 0.38f)
                    .align(Alignment.TopCenter)
                    .clickable(onClick = onUp),
                contentAlignment = Alignment.Center,
            ) {
                PixelText("▲", 7f, AppLanguage.EN, color = Color.White.copy(alpha = 0.6f))
            }
            // Down click area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(size * 0.38f)
                    .align(Alignment.BottomCenter)
                    .clickable(onClick = onDown),
                contentAlignment = Alignment.Center,
            ) {
                PixelText("▼", 7f, AppLanguage.EN, color = Color.White.copy(alpha = 0.6f))
            }
        }

        // Center hub circle
        Box(
            modifier = Modifier
                .size(wingThickness * 0.85f)
                .clip(CircleShape)
                .background(Color(0xFF202020))
                .border(1.5.dp, DexTheme.outline, CircleShape)
        )
    }
}

/** Chunky anime push-button. */
@Composable
fun HardwareButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = DexTheme.keyDark,
    enabled: Boolean = true,
    contentDescription: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .offset(y = if (pressed) 2.dp else 0.dp)
            .shadow(if (pressed) 0.dp else 3.dp, shape)
            .clip(shape)
            .background(color)
            .border(2.5.dp, DexTheme.outline, shape)
            .clickable(interaction, null, enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(LocalContentColor provides DexTheme.cream.copy(alpha = if (enabled) 1f else 0.45f)) {
            content()
        }
    }
}

/** Round anime button (e.g. main black scan button, red power button) */
@Composable
fun AnimeRoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    color: Color = DexTheme.keyDark,
    enabled: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(size)
            .offset(y = if (pressed) 2.dp else 0.dp)
            .shadow(if (pressed) 0.dp else 4.dp, CircleShape)
            .clip(CircleShape)
            .background(color)
            .border(2.5.dp, DexTheme.outline, CircleShape)
            .clickable(interaction, null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Pill-shaped capsule button (Red / Blue camera & photo capsules) */
@Composable
fun AnimePillButton(
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 48.dp,
    height: Dp = 14.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(width, height)
            .offset(y = if (pressed) 1.5.dp else 0.dp)
            .shadow(if (pressed) 0.dp else 2.dp, RoundedCornerShape(7.dp))
            .clip(RoundedCornerShape(7.dp))
            .background(color)
            .border(2.dp, DexTheme.outline, RoundedCornerShape(7.dp))
            .clickable(interaction, null, onClick = onClick)
    )
}

/** Keypad button on the right half (blue square keys) */
@Composable
fun AnimeKeypadButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    language: AppLanguage = AppLanguage.EN,
    color: Color = DexTheme.keyBlue,
    label: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (label != null) {
            PixelText(label, 5f, language, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            Spacer(Modifier.height(2.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .offset(y = if (pressed) 2.dp else 0.dp)
                .shadow(if (pressed) 0.dp else 3.dp, RoundedCornerShape(5.dp))
                .clip(RoundedCornerShape(5.dp))
                .background(color)
                .border(2.dp, DexTheme.outline, RoundedCornerShape(5.dp))
                .clickable(interaction, null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            PixelText(title, 6f, language, color = Color.White)
        }
    }
}

/** Decode a bundled asset image without throwing on missing files. */
fun loadAssetBitmap(context: Context, path: String): ImageBitmap? {
    return try {
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/** Black OLED Data Screen for the Right Half */
@Composable
fun DarkDataPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DexTheme.outline)
            .padding(2.5.dp)
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(DexTheme.screenBlack)
        ) {
            CompositionLocalProvider(LocalContentColor provides DexTheme.lcd) {
                Column(Modifier.padding(12.dp), content = content)
            }
            ScanLines(Modifier.matchParentSize())
        }
    }
}

@Composable
fun ScanLines(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawRect(Color.Black.copy(alpha = 0.08f), Offset(0f, y), Size(size.width, 0.5.dp.toPx()))
            y += step
        }
    }
}

@Composable
fun SpeakerGrille(modifier: Modifier = Modifier, count: Int = 4, scale: Float = 1f) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy((4 * scale).dp)) {
        repeat(count) {
            Box(
                Modifier
                    .size((32 * scale).dp, (3 * scale).dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(DexTheme.outline)
            )
        }
    }
}

/** Vertical Mechanical Hinge Cylinder connecting the two panels */
@Composable
fun HingeCylinder(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(22.dp)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    listOf(
                        DexTheme.shellRedDark,
                        DexTheme.shellRed,
                        DexTheme.shellRedLight,
                        DexTheme.shellRed,
                        DexTheme.shellRedDark,
                    )
                )
            )
            .border(2.dp, DexTheme.outline)
    ) {
        // Top and bottom black end caps
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .align(Alignment.TopCenter)
                .background(DexTheme.outline)
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
                .align(Alignment.BottomCenter)
                .background(DexTheme.outline)
        )
    }
}

/** Corner-bracket reticle drawn over the viewfinder */
@Composable
fun Reticle(modifier: Modifier = Modifier, color: Color = DexTheme.ink) {
    Canvas(modifier) {
        val l = 16.dp.toPx()
        val w = 3.dp.toPx()
        listOf(
            Triple(0f, 0f, Offset(1f, 1f)), Triple(size.width, 0f, Offset(-1f, 1f)),
            Triple(0f, size.height, Offset(1f, -1f)), Triple(size.width, size.height, Offset(-1f, -1f)),
        ).forEach { (x, y, d) ->
            drawLine(color, Offset(x, y), Offset(x, y + d.y * l), w)
            drawLine(color, Offset(x, y), Offset(x + d.x * l, y), w)
        }
    }
}

/** Left Half Stepped Top Border Line (anime silhouette) */
@Composable
fun SteppedTopBar(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(26.dp)) {
        val w = size.width
        val stepX = w * 0.44f
        val path = Path().apply {
            moveTo(0f, size.height - 2.dp.toPx())
            lineTo(stepX, size.height - 2.dp.toPx())
            lineTo(stepX + 20.dp.toPx(), 4.dp.toPx())
            lineTo(w, 4.dp.toPx())
        }
        drawPath(path, DexTheme.outline, style = Stroke(width = 3.dp.toPx()))
    }
}
