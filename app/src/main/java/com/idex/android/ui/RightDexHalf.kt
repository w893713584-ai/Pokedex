package com.idex.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.idex.android.DexViewModel
import com.idex.android.audio.DeviceSound
import com.idex.android.data.AppLanguage
import java.util.Locale

@Composable
fun RightDexHalf(
    vm: DexViewModel,
    onClose: (() -> Unit)? = null,
    onPickPhoto: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pokemon = vm.selected ?: vm.catalog.pokemon(25)!!
    val isSpeaking by vm.audio.isSpeaking.collectAsState()
    val isPlayingCry by vm.audio.isPlayingCry.collectAsState()
    val audioActive = isSpeaking || isPlayingCry

    BoxWithConstraints(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                // Top bar: Status title & close button (if single-pane)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    PixelText(
                        text = vm.entryStatusTitle,
                        size = 8f,
                        language = vm.language,
                        color = DexTheme.outline,
                    )
                    if (onClose != null) {
                        IconButton(
                            onClick = {
                                vm.audio.play(DeviceSound.BUTTON)
                                onClose()
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = vm.text("Close", "关闭", "閉じる"),
                                tint = DexTheme.outline,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // 1. Black OLED Data Screen
                DarkDataPanel(modifier = Modifier.fillMaxWidth()) {
                    // Header: Sprite + ID + Name + Genus
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val sprite = remember(pokemon.id) {
                            loadAssetBitmap(context, pokemon.spriteAsset)
                        }
                        if (sprite != null) {
                            Image(
                                bitmap = sprite,
                                contentDescription = pokemon.name(vm.language),
                                filterQuality = FilterQuality.None,
                                modifier = Modifier
                                    .size(86.dp)
                                    .padding(end = 8.dp)
                                    .clickable { vm.playCry() },
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                PixelText(
                                    text = "#${pokemon.number}",
                                    size = 8f,
                                    language = vm.language,
                                    color = DexTheme.lcd.copy(alpha = 0.75f),
                                    latinOnly = true,
                                )
                                PixelText(
                                    text = pokemon.region(vm.language),
                                    size = 6f,
                                    language = vm.language,
                                    color = DexTheme.lcd.copy(alpha = 0.75f),
                                )
                            }
                            Spacer(Modifier.height(3.dp))
                            PixelText(
                                text = pokemon.name(vm.language),
                                size = 11f,
                                language = vm.language,
                                color = DexTheme.lcd,
                            )
                            Spacer(Modifier.height(3.dp))
                            PixelText(
                                text = pokemon.genus(vm.language),
                                size = 6.5f,
                                language = vm.language,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Type capsules
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        pokemon.typeNames(vm.language).forEach { typeName ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .border(1.dp, DexTheme.lcd.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                                    .background(DexTheme.lcd.copy(alpha = 0.12f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                PixelText(
                                    text = typeName,
                                    size = 6f,
                                    language = vm.language,
                                    color = DexTheme.lcd,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Metrics: Height, Weight, Total Stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            PixelText(
                                text = vm.text("HT", "身高", "たかさ"),
                                size = 5.5f,
                                language = vm.language,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                            Spacer(Modifier.height(1.dp))
                            PixelText(
                                text = String.format(Locale.US, "%.1f m", pokemon.height),
                                size = 7f,
                                language = vm.language,
                                color = DexTheme.lcd,
                                latinOnly = true,
                            )
                        }
                        Column {
                            PixelText(
                                text = vm.text("WT", "体重", "おもさ"),
                                size = 5.5f,
                                language = vm.language,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                            Spacer(Modifier.height(1.dp))
                            PixelText(
                                text = String.format(Locale.US, "%.1f kg", pokemon.weight),
                                size = 7f,
                                language = vm.language,
                                color = DexTheme.lcd,
                                latinOnly = true,
                            )
                        }
                        Column {
                            PixelText(
                                text = vm.text("TOT", "能力值", "合計"),
                                size = 5.5f,
                                language = vm.language,
                                color = Color.White.copy(alpha = 0.55f),
                            )
                            Spacer(Modifier.height(1.dp))
                            PixelText(
                                text = "${pokemon.totalStats}",
                                size = 7f,
                                language = vm.language,
                                color = DexTheme.lcd,
                                latinOnly = true,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Content: Description or Stats
                    if (!vm.showStats) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(82.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            PixelText(
                                text = pokemon.description(vm.language),
                                size = if (vm.language == AppLanguage.EN) 7.5f else 10.5f,
                                language = vm.language,
                                color = DexTheme.lcd,
                                lineHeight = if (vm.language == AppLanguage.EN) 14.sp else 18.sp,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(82.dp),
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            pokemon.stats.forEach { stat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PixelText(
                                        text = stat.title(vm.language),
                                        size = 5.5f,
                                        language = vm.language,
                                        color = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.width(58.dp),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.White.copy(alpha = 0.15f)),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(fraction = (stat.value / 255f).coerceIn(0f, 1f))
                                                .height(4.dp)
                                                .background(DexTheme.lcd),
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    PixelText(
                                        text = "${stat.value}",
                                        size = 5.5f,
                                        language = vm.language,
                                        color = DexTheme.lcd,
                                        latinOnly = true,
                                        modifier = Modifier.width(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 2. 2×5 Blue Anime Keypad
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Row 1: CRY · READ · STOP · INFO · STATS
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AnimeKeypadButton(
                            title = "CRY",
                            onClick = { vm.playCry() },
                            language = vm.language,
                            label = vm.text("叫声", "叫声", "なきごえ"),
                            color = if (isPlayingCry) DexTheme.ledYellow else DexTheme.keyBlue,
                            modifier = Modifier.weight(1f),
                        )
                        AnimeKeypadButton(
                            title = "READ",
                            onClick = {
                                vm.audio.play(DeviceSound.BUTTON)
                                vm.speak()
                            },
                            language = vm.language,
                            label = vm.text("朗读", "朗读", "読上げ"),
                            color = if (isSpeaking) DexTheme.ledGreen else DexTheme.keyBlue,
                            modifier = Modifier.weight(1f),
                        )
                        AnimeKeypadButton(
                            title = "STOP",
                            onClick = {
                                vm.audio.play(DeviceSound.BUTTON)
                                vm.audio.stopSpeaking()
                            },
                            language = vm.language,
                            label = vm.text("停止", "停止", "停止"),
                            modifier = Modifier.weight(1f),
                        )
                        AnimeKeypadButton(
                            title = "INFO",
                            onClick = {
                                vm.audio.play(DeviceSound.BUTTON)
                                vm.showStats = false
                            },
                            language = vm.language,
                            label = vm.text("介绍", "介绍", "せつめい"),
                            color = if (!vm.showStats) Color(0xFF1E5BB0) else DexTheme.keyBlue,
                            modifier = Modifier.weight(1f),
                        )
                        AnimeKeypadButton(
                            title = "STATS",
                            onClick = {
                                vm.audio.play(DeviceSound.BUTTON)
                                vm.showStats = true
                            },
                            language = vm.language,
                            label = vm.text("能力", "能力", "のうりょく"),
                            color = if (vm.showStats) Color(0xFF1E5BB0) else DexTheme.keyBlue,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Row 2: ◀ PREV · ▶ NEXT · 151 BROWSE · SETTINGS · CLOSE/SAMPLE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AnimeKeypadButton(
                            title = "◀",
                            onClick = { vm.step(-1) },
                            language = vm.language,
                            label = vm.text("上一只", "上一只", "前へ"),
                            modifier = Modifier.weight(1f),
                        )
                        AnimeKeypadButton(
                            title = "▶",
                            onClick = { vm.step(1) },
                            language = vm.language,
                            label = vm.text("下一只", "下一只", "次へ"),
                            modifier = Modifier.weight(1f),
                        )
                        AnimeKeypadButton(
                            title = "LIST",
                            onClick = {
                                vm.audio.play(DeviceSound.BUTTON)
                                vm.showingCatalog = true
                            },
                            language = vm.language,
                            label = vm.text("图鉴列表", "图鉴列表", "図鑑一覧"),
                            modifier = Modifier.weight(1f),
                        )
                        AnimeKeypadButton(
                            title = "SET",
                            onClick = {
                                vm.audio.play(DeviceSound.BUTTON)
                                vm.showingSettings = true
                            },
                            language = vm.language,
                            label = vm.text("设置", "设置", "設定"),
                            modifier = Modifier.weight(1f),
                        )
                        if (onClose != null) {
                            AnimeKeypadButton(
                                title = "CLOSE",
                                onClick = {
                                    vm.audio.play(DeviceSound.BUTTON)
                                    onClose()
                                },
                                language = vm.language,
                                label = vm.text("关闭", "关闭", "閉じる"),
                                color = DexTheme.ledRed,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            AnimeKeypadButton(
                                title = "PHOTO",
                                onClick = {
                                    vm.audio.play(DeviceSound.BUTTON)
                                    onPickPhoto()
                                },
                                language = vm.language,
                                label = vm.text("相册", "相册", "写真"),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 3. Two White Function Buttons + Blinking Yellow LED
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HardwareButton(
                            onClick = { vm.cycleLanguage() },
                            color = Color(0xFFF2F2F2),
                            contentDescription = "Cycle Language",
                        ) {
                            PixelText(
                                text = "LANG: ${vm.language.name}",
                                size = 6.5f,
                                language = AppLanguage.EN,
                                color = DexTheme.outline,
                            )
                        }

                        HardwareButton(
                            onClick = { vm.cycleVoiceStyle() },
                            color = Color(0xFFF2F2F2),
                            contentDescription = "Cycle Voice Style",
                        ) {
                            PixelText(
                                text = "VOICE: ${vm.voiceStyleId.uppercase()}",
                                size = 6.5f,
                                language = AppLanguage.EN,
                                color = DexTheme.outline,
                            )
                        }
                    }

                    // Yellow Blinking LED
                    val transition = rememberInfiniteTransition(label = "audioBlink")
                    val blinkAlpha by transition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 350, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "blinkAlpha",
                    )
                    Indicator(
                        color = DexTheme.ledYellow,
                        size = 14.dp,
                        lit = if (audioActive) (blinkAlpha > 0.6f) else false,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 4. Two Dark LCD Bars: SEEN Count & IDENTIFIED/CONFIDENCE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DexTheme.outline)
                            .padding(2.dp)
                            .clickable {
                                vm.audio.play(DeviceSound.BUTTON)
                                vm.showingCatalog = true
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(DexTheme.screenBlack)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = "Pokédex List",
                                    tint = DexTheme.lcd,
                                    modifier = Modifier.size(11.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                PixelText(
                                    text = vm.text(
                                        "SEEN ${vm.seenIds.size}/${vm.catalog.entries.size} ▶",
                                        "已发现 ${vm.seenIds.size}/${vm.catalog.entries.size} ▶",
                                        "発見 ${vm.seenIds.size}/${vm.catalog.entries.size} ▶"
                                    ),
                                    size = 5.5f,
                                    language = vm.language,
                                    color = DexTheme.lcd,
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DexTheme.outline)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(DexTheme.screenBlack)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelText(
                                text = vm.scanStatusTitle,
                                size = 6f,
                                language = vm.language,
                                color = DexTheme.lcd,
                            )
                        }
                    }
                }
            }

            // Bottom Silph Co. imprint
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Screw()
                PixelText(
                    text = "SILPH CO. · FIELD UNIT 001",
                    size = 5f,
                    language = AppLanguage.EN,
                    color = DexTheme.outline.copy(alpha = 0.5f),
                )
                Screw()
            }
        }
    }
}
