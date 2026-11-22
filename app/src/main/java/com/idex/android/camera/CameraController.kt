package com.idex.android.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

enum class CameraStatus { IDLE, STARTING, READY, DENIED, UNAVAILABLE }

/**
 * Lifecycle-scoped CameraX preview adapter.
 *
 * Uses monotonic attachment tokens so stale provider callbacks or disposing views
 * from layout switches (e.g. folding/unfolding) never unbind or overwrite a newer preview.
 */
class CameraController(private val context: Context) {
    private var provider: ProcessCameraProvider? = null
    private var previewView: PreviewView? = null
    private var boundOwner: LifecycleOwner? = null
    private var boundPreview: Preview? = null
    private var attachToken: Long = 0L
    @Volatile var attachmentVersion: Long = 0L
        private set

    fun bind(owner: LifecycleOwner, view: PreviewView, onStatus: (CameraStatus) -> Unit) {
        if (boundOwner === owner && previewView === view && boundPreview != null) {
            return
        }
        val token = ++attachToken
        attachmentVersion++
        detachCurrent()
        previewView = view
        boundOwner = owner
        onStatus(CameraStatus.STARTING)

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (token != attachToken || previewView !== view || boundOwner !== owner) {
                return@addListener
            }
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val selector = when {
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> {
                        onStatus(CameraStatus.UNAVAILABLE)
                        return@addListener
                    }
                }
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                detachCurrent()
                previewView = view
                boundOwner = owner
                cameraProvider.bindToLifecycle(owner, selector, preview)
                boundPreview = preview
                onStatus(CameraStatus.READY)
            } catch (_: Exception) {
                if (token == attachToken) {
                    onStatus(CameraStatus.UNAVAILABLE)
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun unbind(targetView: PreviewView? = null, onStatus: ((CameraStatus) -> Unit)? = null) {
        if (targetView != null && previewView !== targetView) {
            return
        }
        attachToken++
        attachmentVersion++
        detachCurrent()
        previewView = null
        boundOwner = null
        onStatus?.invoke(CameraStatus.IDLE)
    }

    private fun detachCurrent() {
        val currentPreview = boundPreview
        boundPreview = null
        if (currentPreview != null) {
            provider?.runCatching { unbind(currentPreview) }
        }
    }

    /** Current framed preview, or null if the camera isn't streaming yet. Main thread only. */
    fun grabFrame(): Bitmap? {
        val view = previewView ?: return null
        if (view.previewStreamState.value != PreviewView.StreamState.STREAMING) return null
        val bmp = view.bitmap ?: return null
        if (bmp.width <= 0 || bmp.height <= 0) {
            bmp.recycle()
            return null
        }
        return bmp
    }
}
