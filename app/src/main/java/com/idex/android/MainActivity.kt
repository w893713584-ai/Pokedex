package com.idex.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.idex.android.audio.DeviceSound
import com.idex.android.camera.CameraStatus
import com.idex.android.ui.DexApp

class MainActivity : ComponentActivity() {
    private val vm: DexViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        vm.cameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(RequestPermission()) { granted ->
                vm.cameraPermission = granted
                if (!granted) {
                    vm.cameraStatus = CameraStatus.DENIED
                } else {
                    vm.setSensorEnabled(true)
                }
            }

            val photoPickerLauncher = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
                uri?.let { vm.importPhoto(it) }
            }

            val layoutInfo by remember {
                WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this)
            }.collectAsStateWithLifecycle(initialValue = null)

            val fold = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()

            // Play OPEN sound when hinge opens or closes (parity with iOS HingeObserver)
            var lastState by remember { mutableStateOf<FoldingFeature.State?>(null) }
            LaunchedEffect(fold?.state) {
                if (lastState != null && fold?.state != null && lastState != fold.state) {
                    vm.audio.play(DeviceSound.OPEN)
                }
                lastState = fold?.state
            }

            DexApp(
                vm = vm,
                foldingFeature = fold,
                onPickPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(PickVisualMedia.ImageOnly),
                    )
                },
                onRequestCamera = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
            )
        }
    }

    override fun onPause() {
        super.onPause()
        vm.suspend()
    }

    override fun onResume() {
        super.onResume()
        vm.cameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        vm.resume()
    }
}
