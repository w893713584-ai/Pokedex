package com.idex.android.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.idex.android.DexViewModel
import com.idex.android.audio.DeviceSound
import com.idex.android.camera.CameraStatus

@Composable
fun LeftDexHalf(
    vm: DexViewModel,
    compact: Boolean,
    onPickPhoto: () -> Unit,
    onRequestCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isSpeaking by vm.audio.isSpeaking.collectAsState()

    BoxWithConstraints(modifier = modifier) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        val side: Dp = minOf(availableWidth - 84.dp, availableHeight - 380.dp, 380.dp).coerceAtLeast(160.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Signature Top Lights
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ScannerLights(
                    isScanning = vm.isScanning,
                    isSpeaking = isSpeaking,
                    lensSize = 58.dp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            vm.audio.play(DeviceSound.BUTTON)
                            vm.showingCatalog = true
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = vm.text("Pokédex List", "图鉴列表", "図鑑一覧"),
                            tint = DexTheme.outline,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            vm.audio.play(DeviceSound.BUTTON)
                            vm.showingSettings = true
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = vm.text("Settings", "设置", "設定"),
                            tint = DexTheme.outline,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Signature Stepped Contour
            SteppedTopBar(Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))

            // 2. White Bezel Housing the Viewfinder Screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 32.dp, bottomEnd = 16.dp))
                    .background(DexTheme.bezelWhite)
                    .border(3.dp, DexTheme.outline, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 32.dp, bottomEnd = 16.dp))
                    .padding(14.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Two Red Dots at top of bezel
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(DexTheme.ledRed).border(1.dp, DexTheme.outline, CircleShape))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(DexTheme.ledRed).border(1.dp, DexTheme.outline, CircleShape))
                    }

                    // Main Viewfinder Screen
                    Box(
                        modifier = Modifier
                            .size(side)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (vm.sensorOn || vm.isScanning || vm.capturedImage != null || vm.scanResult != null) DexTheme.lcd
                                else DexTheme.screenOff
                            )
                            .border(2.5.dp, DexTheme.outline, RoundedCornerShape(8.dp)),
                    ) {
                        when {
                            // 1. Analysing a frame / imported photo: sweep animation
                            vm.isScanning || vm.capturedImage != null -> {
                                vm.capturedImage?.let { captured ->
                                    Image(
                                        bitmap = captured.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                val transition = rememberInfiniteTransition(label = "scanSweep")
                                val progress by transition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = 1100, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart,
                                    ),
                                    label = "progress",
                                )
                                Box(Modifier.fillMaxSize().background(DexTheme.ink.copy(alpha = 0.16f))) {
                                    val beamY = side * progress
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .offset(y = beamY - 50.dp)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(Color.Transparent, DexTheme.lcd.copy(alpha = 0.65f))
                                                )
                                            ),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .offset(y = beamY)
                                            .background(DexTheme.cream),
                                    )
                                }
                            }

                            // 2. Recognition result (camera or photo). Shows the currently selected
                            //    candidate so choosing another candidate updates the viewfinder too.
                            vm.scanResult != null && vm.selected != null -> {
                                val pokemon = vm.selected!!
                                Canvas(Modifier.fillMaxSize()) {
                                    drawLine(
                                        color = DexTheme.ink.copy(alpha = 0.15f),
                                        start = Offset(size.width / 2, 0f),
                                        end = Offset(size.width / 2, size.height),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                    drawLine(
                                        color = DexTheme.ink.copy(alpha = 0.15f),
                                        start = Offset(0f, size.height / 2),
                                        end = Offset(size.width, size.height / 2),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                }
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
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
                                                .size(side * 0.58f)
                                                .clickable { vm.playCry() },
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    PixelText(
                                        text = pokemon.name(vm.language),
                                        size = 9f,
                                        language = vm.language,
                                        color = DexTheme.ink,
                                    )
                                    if (vm.scanResult?.accepted == null) {
                                        Spacer(Modifier.height(3.dp))
                                        PixelText(
                                            text = vm.text("LOW CONFIDENCE", "置信度较低", "低確信度"),
                                            size = 5f,
                                            language = vm.language,
                                            color = DexTheme.ink.copy(alpha = 0.85f),
                                        )
                                    }
                                }
                            }

                            // 3. Sensor OFF = archive mode: mirror the Pokémon on the data screen.
                            !vm.sensorOn -> {
                                val currentPokemon = vm.selected
                                val sprite = remember(currentPokemon?.id) {
                                    currentPokemon?.let { loadAssetBitmap(context, it.spriteAsset) }
                                }
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    if (sprite != null && currentPokemon != null) {
                                        Image(
                                            bitmap = sprite,
                                            contentDescription = currentPokemon.name(vm.language),
                                            filterQuality = FilterQuality.None,
                                            modifier = Modifier
                                                .size(side * 0.58f)
                                                .clickable { vm.playCry() },
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        PixelText(
                                            text = "#${currentPokemon.number} ${currentPokemon.name(vm.language)}",
                                            size = 8f,
                                            language = vm.language,
                                            color = Color.White.copy(alpha = 0.9f),
                                            maxLines = 1,
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    PixelText(
                                        text = vm.text("PRESS RED KEY TO SCAN", "按红键开启识别", "赤ボタンでスキャン"),
                                        size = 5f,
                                        language = vm.language,
                                        color = DexTheme.lcd.copy(alpha = 0.7f),
                                    )
                                }
                            }

                            // 4. Live camera
                            vm.wantsLiveCamera -> {
                                val previewHolder = remember { arrayOfNulls<PreviewView>(1) }
                                AndroidView(
                                    factory = { ctx ->
                                        PreviewView(ctx).apply {
                                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                            scaleType = PreviewView.ScaleType.FILL_CENTER
                                        }.also { previewHolder[0] = it }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                ) { view ->
                                    previewHolder[0] = view
                                    vm.camera.bind(lifecycleOwner, view) { vm.cameraStatus = it }
                                }
                                DisposableEffect(lifecycleOwner) {
                                    onDispose {
                                        previewHolder[0]?.let { view ->
                                            vm.camera.unbind(view) { vm.cameraStatus = it }
                                        }
                                    }
                                }
                                Reticle(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(18.dp),
                                    color = DexTheme.cream,
                                )
                            }

                            // 5. Camera permission not granted
                            !vm.cameraPermission -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = vm.text("Camera permission needed", "需要相机权限", "カメラ権限が必要です"),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = DexTheme.ink,
                                        textAlign = TextAlign.Center,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    HardwareButton(
                                        onClick = {
                                            vm.audio.play(DeviceSound.BUTTON)
                                            onRequestCamera()
                                        },
                                    ) {
                                        PixelText(
                                            text = vm.text("GRANT ACCESS", "授权相机", "許可する"),
                                            size = 6f,
                                            language = vm.language,
                                        )
                                    }
                                }
                            }

                            else -> {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    PixelText(
                                        text = vm.message ?: vm.text("READY", "就绪", "準備完了"),
                                        size = 8f,
                                        language = vm.language,
                                        color = DexTheme.ink,
                                    )
                                }
                            }
                        }

                        // Top mini status strip
                        val bannerTitle = when {
                            vm.isScanning -> vm.text("SCANNING…", "扫描中…", "スキャン中…")
                            vm.scannedPokemon != null -> vm.scanStatusTitle
                            vm.message != null -> vm.message!!
                            !vm.sensorOn -> vm.text("ARCHIVE · SENSOR OFF", "档案 · 传感器关闭", "図鑑 · センサーオフ")
                            vm.capturedImage == null -> vm.text("OPTICAL SENSOR", "光学传感器", "光学センサー")
                            else -> vm.text("PHOTO REVIEW", "照片回看", "写真確認")
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DexTheme.outline.copy(alpha = 0.85f))
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixelText(bannerTitle, 5.5f, vm.language, color = DexTheme.cream)
                        }

                        ScanLines(Modifier.matchParentSize())
                    }

                    Spacer(Modifier.height(10.dp))

                    // Bottom of white bezel: Red Power Key (A3) + Speaker Grille
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AnimeRoundButton(
                                onClick = {
                                    if (!vm.cameraPermission && !vm.sensorOn) {
                                        onRequestCamera()
                                    } else {
                                        vm.toggleSensor()
                                    }
                                },
                                size = 28.dp,
                                color = if (vm.sensorOn) DexTheme.ledGreen else DexTheme.ledRed,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PowerSettingsNew,
                                    contentDescription = "Sensor Power",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Indicator(
                                color = if (vm.sensorOn) DexTheme.ledGreen else DexTheme.ledRed,
                                size = 8.dp,
                                lit = vm.sensorOn,
                            )
                        }

                        SpeakerGrille(count = 3, scale = 0.9f)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 3. Middle Hardware Controls: Black Round Button + Capsules
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Main Big Black Scan Button
                val scanDisabled = vm.isScanning || (!vm.canRescan && vm.cameraStatus != CameraStatus.READY && vm.sensorOn)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimeRoundButton(
                        onClick = {
                            if (vm.canRescan) vm.rescan() else vm.scan()
                        },
                        size = 50.dp,
                        color = DexTheme.keyDark,
                        enabled = !scanDisabled,
                    ) {
                        Icon(
                            imageVector = if (vm.canRescan && !vm.isScanning) Icons.Filled.Refresh else Icons.Filled.CenterFocusStrong,
                            contentDescription = "Scan",
                            tint = if (vm.sensorOn) DexTheme.lcd else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        PixelText(
                            text = when {
                                vm.isScanning -> vm.text("SCANNING", "扫描中", "スキャン中")
                                !vm.sensorOn -> vm.text("POWER ON", "开启识别", "起動")
                                vm.canRescan -> vm.text("RESCAN", "重试", "再試行")
                                else -> vm.text("IDENTIFY", "识别", "識別")
                            },
                            size = 7f,
                            language = vm.language,
                            color = DexTheme.outline,
                        )
                    }
                }

                // Two Capsules: Red (Camera) and Blue (Photo)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimePillButton(
                            onClick = {
                                if (!vm.cameraPermission) onRequestCamera() else vm.chooseCamera()
                            },
                            color = DexTheme.ledRed,
                            width = 44.dp,
                            height = 14.dp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = "Camera",
                            tint = DexTheme.outline,
                            modifier = Modifier.size(14.dp),
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimePillButton(
                            onClick = onPickPhoto,
                            color = DexTheme.keyBlue,
                            width = 44.dp,
                            height = 14.dp,
                        )
                        Spacer(Modifier.height(2.dp))
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = "Import Photo",
                            tint = DexTheme.outline,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 4. Bottom Row: Small Green LCD + D-Pad
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Mini Green Screen showing #025 and confidence or OPEN button
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DexTheme.outline)
                            .padding(2.5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(DexTheme.lcd)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Column {
                                val current = vm.selected
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    PixelText(
                                        text = "#${current?.number ?: "025"}",
                                        size = 8f,
                                        language = vm.language,
                                        color = DexTheme.ink,
                                        latinOnly = true,
                                    )
                                    PixelText(
                                        text = vm.confidence?.let { "${(it * 100).toInt()}%" }
                                            ?: (current?.region(vm.language) ?: "KANTO"),
                                        size = 6f,
                                        language = vm.language,
                                        color = DexTheme.ink.copy(alpha = 0.75f),
                                        latinOnly = vm.confidence != null,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                PixelText(
                                    text = current?.name(vm.language) ?: "Pikachu",
                                    size = 7f,
                                    language = vm.language,
                                    color = DexTheme.ink,
                                    maxLines = 1,
                                )
                            }
                            ScanLines(Modifier.matchParentSize())
                        }
                    }

                    // In single pane (compact), show buttons to open Catalog & Data screen
                    if (compact) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            HardwareButton(
                                onClick = {
                                    vm.audio.play(DeviceSound.BUTTON)
                                    vm.showingCatalog = true
                                },
                                color = DexTheme.keyDark,
                                modifier = Modifier.weight(1f),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = null,
                                        tint = DexTheme.cream,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    PixelText(
                                        text = vm.text("CATALOG", "图鉴列表", "図鑑一覧"),
                                        size = 5.5f,
                                        language = vm.language,
                                    )
                                }
                            }

                            HardwareButton(
                                onClick = {
                                    vm.audio.play(DeviceSound.BUTTON)
                                    vm.showingCompactEntry = true
                                },
                                color = DexTheme.keyBlue,
                                modifier = Modifier.weight(1f),
                            ) {
                                PixelText(
                                    text = vm.text("DATA ▶", "数据面板 ▶", "データ ▶"),
                                    size = 5.5f,
                                    language = vm.language,
                                )
                            }
                        }
                    }
                }

                // D-Pad (Directional Pad ✚)
                DPad(
                    onLeft = { vm.step(-1) },
                    onRight = { vm.step(1) },
                    onUp = { vm.showStats = false },
                    onDown = { vm.showStats = true },
                    size = 96.dp,
                )
            }
        }
    }
}
