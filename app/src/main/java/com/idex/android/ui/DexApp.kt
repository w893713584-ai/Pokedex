package com.idex.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import com.idex.android.DexViewModel
import com.idex.android.audio.DeviceSound

@Composable
fun DexApp(
    vm: DexViewModel,
    foldingFeature: FoldingFeature?,
    onPickPhoto: () -> Unit,
    onRequestCamera: () -> Unit,
) {
    // Back navigation handling
    BackHandler(enabled = vm.showingSettings || vm.showingCatalog || vm.showingCompactEntry) {
        when {
            vm.showingSettings -> vm.showingSettings = false
            vm.showingCatalog -> vm.showingCatalog = false
            vm.showingCompactEntry -> vm.showingCompactEntry = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val totalWidth = maxWidth
        val totalHeight = maxHeight
        ShellSurface(modifier = Modifier.fillMaxSize())

        val isTwoPane = totalWidth >= 600.dp && totalHeight >= 480.dp

        LaunchedEffect(isTwoPane) {
            if (isTwoPane && vm.showingCompactEntry) {
                vm.showingCompactEntry = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (isTwoPane) {
                // Two-pane inner folded / tablet layout:
                // Left = LeftDexHalf (Scanner & Lens), Middle = HingeCylinder, Right = RightDexHalf (Data & Keypad)
                val defaultLeftWidth = (totalWidth * 0.49f).coerceIn(360.dp, 540.dp)

                val leftWidth = if (foldingFeature != null &&
                    foldingFeature.orientation == FoldingFeature.Orientation.VERTICAL
                ) {
                    val bounds = foldingFeature.bounds
                    val leftPx = bounds.left
                    val featureWidthDp = with(density) { leftPx.toDp() }
                    if (featureWidthDp in 320.dp..600.dp) featureWidthDp else defaultLeftWidth
                } else {
                    defaultLeftWidth
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    key(isTwoPane) {
                        LeftDexHalf(
                            vm = vm,
                            compact = false,
                            onPickPhoto = onPickPhoto,
                            onRequestCamera = onRequestCamera,
                            modifier = Modifier
                                .width(leftWidth)
                                .fillMaxHeight(),
                        )
                    }

                    HingeCylinder(modifier = Modifier.fillMaxHeight())

                    RightDexHalf(
                        vm = vm,
                        onClose = null,
                        onPickPhoto = onPickPhoto,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                // Single-pane phone / folded outer-screen layout
                key(isTwoPane) {
                    LeftDexHalf(
                        vm = vm,
                        compact = true,
                        onPickPhoto = onPickPhoto,
                        onRequestCamera = onRequestCamera,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Slide in RightDexHalf from the right when compact entry is opened
                AnimatedVisibility(
                    visible = vm.showingCompactEntry,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                ) {
                    LaunchedEffect(Unit) {
                        vm.audio.play(DeviceSound.OPEN)
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        ShellSurface(modifier = Modifier.fillMaxSize())
                        RightDexHalf(
                            vm = vm,
                            onClose = { vm.showingCompactEntry = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Overlay dialogs
        if (vm.showingCatalog) {
            CatalogSheet(vm = vm, compact = !isTwoPane)
        }

        if (vm.showingSettings) {
            SettingsSheet(vm = vm)
        }
    }
}
