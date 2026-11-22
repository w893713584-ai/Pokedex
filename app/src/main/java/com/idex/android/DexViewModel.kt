package com.idex.android

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.idex.android.audio.DeviceSound
import com.idex.android.audio.DexAudio
import com.idex.android.audio.VoiceStyle
import com.idex.android.camera.CameraController
import com.idex.android.camera.CameraStatus
import com.idex.android.data.AppLanguage
import com.idex.android.data.Pokemon
import com.idex.android.data.PokemonCatalog
import com.idex.android.recognition.PokemonClassifier
import com.idex.android.recognition.RecognitionCandidate
import com.idex.android.recognition.RecognitionException
import com.idex.android.recognition.RecognitionResult
import com.idex.android.recognition.ScanImagePreparer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Android counterpart of the iOS DexStore. Survives folding/unfolding and rotation. */
class DexViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("idex", 0)
    val catalog = PokemonCatalog.get(app)
    val audio = DexAudio(app)
    val camera = CameraController(app)
    private val classifier = PokemonClassifier(app)
    private var scanJob: Job? = null
    private var isActive = true
    private var operationGeneration = 0L
    private var activeOperationId = 0L

    var language by mutableStateOf(AppLanguage.fromCode(prefs.getString("language", null)) ?: AppLanguage.systemDefault())
        private set
    var selected by mutableStateOf(catalog.pokemon(prefs.getInt("lastViewed", 25)) ?: catalog.pokemon(25))
        private set
    var isScanning by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set
    var candidates by mutableStateOf<List<RecognitionCandidate>>(emptyList()); private set
    var capturedImage by mutableStateOf<Bitmap?>(null); private set
    var scanResult by mutableStateOf<RecognitionResult?>(null); private set
    // Sample/test target is a developer-only switch: never persisted, never exposed in the UI.
    var useSample by mutableStateOf(false); private set
    var confidence by mutableStateOf<Double?>(null); private set
    var identified by mutableStateOf(false); private set
    var seenIds by mutableStateOf(
        prefs.getStringSet("seen", emptySet())
            .orEmpty()
            .mapNotNull { it.toIntOrNull() }
            .filter { catalog.pokemon(it) != null }
            .toSet()
    ); private set
    var cameraStatus by mutableStateOf(CameraStatus.IDLE)
    var cameraPermission by mutableStateOf(false)
    var sensorOn by mutableStateOf(false)
    var voiceStyleId by mutableStateOf(prefs.getString("voiceStyle", "anime") ?: "anime")
    var showStats by mutableStateOf(false)
    var showingCatalog by mutableStateOf(false)
    var showingSettings by mutableStateOf(false)
    var showingCompactEntry by mutableStateOf(false)

    val sampleId = 25

    init {
        audio.voiceStyle = VoiceStyle.byId(voiceStyleId)
        viewModelScope.launch { runCatching { classifier.warmUp() } }
        startAutoScanLoop()
    }

    fun text(en: String, zh: String, ja: String) = language.text(en, zh, ja)

    val scannedPokemon: Pokemon? get() = scanResult?.candidates?.firstOrNull()?.let { catalog.pokemon(it.dexId) }
    val scanStatusTitle: String
        get() = when {
            isScanning -> text("SCANNING", "扫描中", "スキャン中")
            scanResult != null -> resultStatusTitle(scanResult?.accepted != null)
            else -> entryStatusTitle
        }
    val entryStatusTitle: String
        get() = when {
            identified -> resultStatusTitle(true)
            confidence != null -> resultStatusTitle(false)
            else -> text("ARCHIVE", "档案", "図鑑")
        }

    private fun resultStatusTitle(isIdentified: Boolean): String =
        if (isIdentified) text("IDENTIFIED", "已识别", "識別完了")
        else text("BEST MATCH", "最可能匹配", "最有力候補")

    /** The live preview should be running. */
    val wantsLiveCamera: Boolean
        get() = isActive && sensorOn && !useSample && cameraPermission && capturedImage == null && scanResult == null && !isScanning

    val canRescan: Boolean get() = capturedImage != null || scannedPokemon != null || message != null

    fun updateLanguage(value: AppLanguage) {
        language = value
        prefs.edit { putString("language", value.code) }
        audio.stopSpeaking()
        message = null
    }

    fun cycleLanguage() {
        val next = when (language) {
            AppLanguage.ZH -> AppLanguage.JA
            AppLanguage.JA -> AppLanguage.EN
            AppLanguage.EN -> AppLanguage.ZH
        }
        audio.play(DeviceSound.BUTTON)
        updateLanguage(next)
    }

    fun updateVoiceStyle(id: String) {
        voiceStyleId = id
        prefs.edit { putString("voiceStyle", id) }
        audio.voiceStyle = VoiceStyle.byId(id)
        previewVoice()
    }

    fun cycleVoiceStyle() {
        val styles = VoiceStyle.ALL.map { it.id }
        val currentIdx = styles.indexOf(voiceStyleId).coerceAtLeast(0)
        val next = styles[(currentIdx + 1) % styles.size]
        audio.play(DeviceSound.BUTTON)
        updateVoiceStyle(next)
    }

    fun previewVoice() {
        audio.speak(text("Pokédex online.", "宝可梦图鉴，启动。", "ポケモン図鑑、起動。"), language)
    }

    fun playCry() {
        selected?.let { audio.playCryOnly(it) }
    }

    fun setSensorEnabled(enabled: Boolean) {
        resetScanner()
        sensorOn = enabled
    }

    fun toggleSensor() {
        audio.play(DeviceSound.OPEN)
        resetScanner()
        sensorOn = !sensorOn
    }

    fun updateUseSample(value: Boolean) {
        resetScanner()
        useSample = value
    }

    fun chooseCamera() {
        resetScanner()
        useSample = false
        sensorOn = true
        audio.play(DeviceSound.BUTTON)
    }

    fun rescan() {
        resetScanner()
        sensorOn = true
        audio.play(DeviceSound.BUTTON)
    }

    private fun resetScanner() {
        cancelScan()
        audio.stopSpeaking()
        showingCompactEntry = false
        scanResult = null
        confidence = null
        candidates = emptyList()
        message = null
        identified = false
    }

    private fun beginOperation(): Long {
        cancelScan()
        val opId = operationGeneration
        activeOperationId = opId
        isScanning = true
        confidence = null
        message = null
        candidates = emptyList()
        identified = false
        scanResult = null
        audio.stopSpeaking()
        audio.play(DeviceSound.SCAN)
        return opId
    }

    fun scan() {
        if (isScanning || !isActive) return
        if (!useSample && !sensorOn) {
            toggleSensor()
            return
        }
        // Grab the frame before beginOperation() removes the live preview.
        val frame = if (useSample) null else camera.grabFrame()
        if (!useSample && frame == null) return

        launchScanOperation(
            prepareImage = {
                withContext(Dispatchers.Default) {
                    if (useSample) {
                        val sprite = getApplication<Application>().assets.open(catalog.pokemon(sampleId)!!.spriteAsset)
                            .use { BitmapFactory.decodeStream(it) }
                            ?: throw IllegalStateException("Sample sprite missing")
                        try {
                            ScanImagePreparer.opaqueSprite(sprite)
                        } finally {
                            sprite.recycle()
                        }
                    } else {
                        prepareSquareFrame(frame ?: throw IllegalStateException("No camera frame"))
                    }
                }
            },
            fallbackErrorMessage = {
                text(
                    "Couldn't scan. Try a photo or enable the camera.",
                    "未能扫描，请导入照片或启用相机。",
                    "スキャンできませんでした。写真を読み込むか、カメラを有効にしてください。",
                )
            },
        )
    }

    fun importPhoto(uri: Uri) {
        // NOTE: do not gate on isActive here. The photo picker result is delivered in onStart,
        // before onResume() flips isActive back to true. Calling beginOperation() also safely
        // supersedes any in-flight scan rather than discarding the picked image.
        isActive = true
        useSample = false
        launchScanOperation(
            prepareImage = {
                withContext(Dispatchers.IO) {
                    ScanImagePreparer.photo(getApplication<Application>().contentResolver, uri)
                }
            },
            fallbackErrorMessage = {
                text(
                    "This photo couldn't be read. Choose another image.",
                    "无法读取这张照片，请选择其他图片。",
                    "この写真を読み込めませんでした。別の画像を選んでください。",
                )
            },
        )
    }

    private fun launchScanOperation(
        prepareImage: suspend () -> Bitmap,
        fallbackErrorMessage: () -> String,
    ) {
        val opId = beginOperation()
        scanJob = viewModelScope.launch {
            var unpublishedImage: Bitmap? = null
            try {
                val image = prepareImage()
                if (opId != operationGeneration) {
                    image.runCatching { recycle() }
                    return@launch
                }
                unpublishedImage = image
                capturedImage = image
                unpublishedImage = null
                val result = classifier.classify(image)
                if (opId != operationGeneration) return@launch
                applyRecognition(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (opId == operationGeneration) {
                    capturedImage = null
                    message = if (e is RecognitionException) {
                        text(
                            "Recognition is unavailable. Restart Pokedex and try again.",
                            "识别暂不可用，请重启 Pokedex 后重试。",
                            "識別を利用できません。Pokedex を再起動して、もう一度お試しください。",
                        )
                    } else {
                        fallbackErrorMessage()
                    }
                    audio.play(DeviceSound.ERROR)
                }
            } finally {
                unpublishedImage?.runCatching { if (!isRecycled) recycle() }
                if (activeOperationId == opId) {
                    isScanning = false
                    activeOperationId = 0L
                }
            }
        }
    }

    private fun prepareSquareFrame(frame: Bitmap): Bitmap = try {
        ScanImagePreparer.centerSquare(frame)
    } finally {
        frame.recycle()
    }

    private fun applyRecognition(result: RecognitionResult) {
        val match = result.candidates.firstOrNull() ?: throw RecognitionException("invalid output")
        val pokemon = catalog.pokemon(match.dexId) ?: throw RecognitionException("invalid output")
        candidates = result.candidates
        select(pokemon)
        scanResult = result
        capturedImage = null
        confidence = match.confidence
        identified = result.accepted != null
        if (identified) {
            message = null
            seenIds = seenIds + pokemon.id
            prefs.edit { putStringSet("seen", seenIds.map { it.toString() }.toSet()) }
        } else {
            message = text("Best match; low confidence.", "最可能的匹配，置信度较低。", "最有力候補ですが、確信度が低い結果です。")
        }
        audio.announce(pokemon, language)
    }

    private fun startAutoScanLoop() {
        viewModelScope.launch {
            while (true) {
                delay(1200)
                val overlaysOpen = showingCompactEntry || showingCatalog || showingSettings
                if (wantsLiveCamera && !overlaysOpen && cameraStatus == CameraStatus.READY) {
                    val gen = operationGeneration
                    val camVersion = camera.attachmentVersion
                    val frame = camera.grabFrame() ?: continue
                    var cropped: Bitmap? = null
                    try {
                        cropped = withContext(Dispatchers.Default) { prepareSquareFrame(frame) }
                        val result = classifier.classify(cropped)
                        val stillValid = gen == operationGeneration &&
                            camVersion == camera.attachmentVersion &&
                            wantsLiveCamera &&
                            !(showingCompactEntry || showingCatalog || showingSettings)
                        if (result.accepted != null && stillValid) {
                            applyRecognition(result)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        frame.runCatching { if (!isRecycled) recycle() }
                    } finally {
                        cropped?.runCatching { if (!isRecycled) recycle() }
                    }
                }
            }
        }
    }

    fun browse(pokemon: Pokemon) {
        audio.stopSpeaking()
        enterArchiveMode()
        select(pokemon)
        showingCatalog = false
        audio.play(DeviceSound.BUTTON)
    }

    fun step(delta: Int) {
        audio.stopSpeaking()
        val current = selected ?: return
        enterArchiveMode()
        val list = catalog.entries
        if (list.isEmpty()) return
        val currentIndex = list.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        val nextIndex = Math.floorMod(currentIndex + delta, list.size)
        select(list[nextIndex])
        audio.play(DeviceSound.BUTTON)
    }

    /**
     * Browsing the archive: drop any previous scan result and power the sensor down,
     * so the left viewfinder mirrors the Pokémon currently shown on the data screen.
     */
    private fun enterArchiveMode() {
        cancelScan()
        sensorOn = false
        scanResult = null
        candidates = emptyList()
        confidence = null
        message = null
        identified = false
    }

    private fun select(pokemon: Pokemon?) {
        selected = pokemon
        pokemon?.let { prefs.edit { putInt("lastViewed", it.id) } }
    }

    fun cancelScan() {
        operationGeneration++
        scanJob?.cancel()
        scanJob = null
        activeOperationId = 0L
        isScanning = false
        capturedImage = null
    }

    fun speak() {
        val p = selected ?: return
        if (isActive) audio.speak(p.spokenEntry(language), language)
    }

    fun suspend() {
        isActive = false
        sensorOn = false
        cancelScan()
        audio.suspend()
    }

    fun resume() {
        isActive = true
        audio.resume()
    }

    override fun onCleared() {
        cancelScan()
        audio.shutdown()
        classifier.close()
        super.onCleared()
    }
}
