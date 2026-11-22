package com.idex.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.idex.android.data.AppLanguage
import com.idex.android.data.Pokemon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.sin

enum class DeviceSound { BUTTON, SCAN, SUCCESS, ERROR, OPEN }

/**
 * Hardware-style chiptune cues, Pokémon cries, and the Pokédex narrator.
 *
 * All mutable audio ownership, TTS configuration/submission, and AudioTrack/MediaPlayer
 * lifecycle run serialized on a single dedicated audio dispatcher thread; CPU-heavy
 * [RoboVoice] DSP runs on [Dispatchers.Default] and delivers finished PCM back to the
 * audio dispatcher before playback.
 */
class DexAudio(context: Context) {
    private data class SpeechRequest(
        val id: String,
        val text: String,
        val language: AppLanguage,
        val style: VoiceStyle,
        val file: File?,
    )

    private val appContext = context.applicationContext
    private val sampleRate = 22_050
    private val cues = DeviceSound.entries.associateWith { render(notes(it)) }

    private val audioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "idex-audio").apply { isDaemon = true }
    }
    private val audioDispatcher = audioExecutor.asCoroutineDispatcher()
    private val audioScope = CoroutineScope(SupervisorJob() + audioDispatcher)
    private val dspScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var cueTrack: AudioTrack? = null
    private var cryPlayer: MediaPlayer? = null
    private var crySessionId = 0L
    private var voiceTrack: AudioTrack? = null
    private var processJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private var pendingSpeech: Pair<String, AppLanguage>? = null
    private var active = true
    private var shutDown = false
    private var utteranceCounter = 0L
    private var currentRequest: SpeechRequest? = null
    private val activeRequests = HashMap<String, SpeechRequest>()

    /** Narrator voice preset; persisted by the ViewModel. */
    @Volatile var voiceStyle: VoiceStyle = VoiceStyle.ANIME_DEX

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _isPlayingCry = MutableStateFlow(false)
    val isPlayingCry: StateFlow<Boolean> = _isPlayingCry

    init {
        val engine = TextToSpeech(appContext) { status ->
            postAudio {
                ttsReady = status == TextToSpeech.SUCCESS
                ttsFailed = !ttsReady
                val next = if (ttsReady && active && !shutDown) pendingSpeech else null
                pendingSpeech = null
                if (next != null) {
                    speakInternal(next.first, next.second)
                }
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                if (utteranceId == null) return
                postAudio {
                    val req = activeRequests[utteranceId]
                    if (req != null && req.file == null && currentRequest?.id == req.id) {
                        _isSpeaking.value = true
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == null) return
                postAudio {
                    val req = activeRequests[utteranceId] ?: return@postAudio
                    if (req.file == null) {
                        clearRequestIfCurrent(utteranceId, removeFromActive = true)
                    } else {
                        onSynthesizedInternal(utteranceId)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = postFailed(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = postFailed(utteranceId)

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == null) return
                postAudio {
                    val req = clearRequestIfCurrent(utteranceId, removeFromActive = true)
                    req?.file?.delete()
                }
            }
        })
        tts = engine
    }

    private fun postAudio(block: () -> Unit) {
        if (!audioExecutor.isShutdown) {
            runCatching { audioScope.launch { if (!shutDown) block() } }
        }
    }

    private fun postFailed(id: String?) {
        postAudio { onFailedInternal(id) }
    }

    // ---------------------------------------------------------------- cues

    fun play(cue: DeviceSound) = postAudio {
        if (!active) return@postAudio
        val data = cues[cue] ?: return@postAudio
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(data.size * 2)
                .build()
            val written = track.write(data, 0, data.size)
            if (written <= 0 || !active) {
                releaseTrackSafely(track)
                return@postAudio
            }
            val old = cueTrack
            cueTrack = track
            old?.let { releaseTrackSafely(it) }
            track.play()
        } catch (_: Exception) {
            track?.let { releaseTrackSafely(it) }
        }
    }

    // ---------------------------------------------------------------- cries

    /** Success chime → cry → spoken entry. */
    fun announce(pokemon: Pokemon, language: AppLanguage) {
        play(DeviceSound.SUCCESS)
        postAudio {
            if (!active) return@postAudio
            stopVoiceOnlyInternal()
            playCryInternal(pokemon) { speakInternal(pokemon.spokenEntry(language), language) }
        }
    }

    /** Play only the cry (dedicated CRY button). Interrupts narration. */
    fun playCryOnly(pokemon: Pokemon) = postAudio {
        if (!active) return@postAudio
        stopVoiceOnlyInternal()
        playCryInternal(pokemon, null)
    }

    fun playCry(pokemon: Pokemon, onDone: (() -> Unit)? = null) = postAudio {
        playCryInternal(pokemon, onDone)
    }

    private fun playCryInternal(pokemon: Pokemon, onDone: (() -> Unit)?) {
        releaseCryInternal()
        if (!active) {
            onDone?.invoke()
            return
        }
        val sessionId = ++crySessionId
        var created: MediaPlayer? = null
        try {
            appContext.assets.openFd(pokemon.cryAsset).use { afd ->
                val mp = MediaPlayer()
                created = mp
                mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                mp.setOnCompletionListener {
                    postAudio { if (releaseCryInternal(sessionId)) onDone?.invoke() }
                }
                mp.setOnErrorListener { _, _, _ ->
                    postAudio { if (releaseCryInternal(sessionId)) onDone?.invoke() }
                    true
                }
                mp.setOnPreparedListener { readyMp ->
                    postAudio {
                        if (crySessionId == sessionId && cryPlayer === readyMp && active) {
                            readyMp.runCatching { start() }.onFailure {
                                if (releaseCryInternal(sessionId)) onDone?.invoke()
                            }
                        } else {
                            releaseCryInternal(sessionId)
                        }
                    }
                }
                cryPlayer = mp
                _isPlayingCry.value = true
                mp.prepareAsync()
            }
        } catch (_: Exception) {
            releasePlayerSafely(created)
            if (cryPlayer === created) cryPlayer = null
            _isPlayingCry.value = false
            if (crySessionId == sessionId) {
                crySessionId++
                onDone?.invoke()
            }
        }
    }

    // ---------------------------------------------------------------- narration

    fun speak(text: String, language: AppLanguage) = postAudio {
        speakInternal(text, language)
    }

    private fun speakInternal(text: String, language: AppLanguage) {
        if (!active) return
        val engine = tts ?: return
        if (!ttsReady) {
            if (!ttsFailed) pendingSpeech = text to language
            return
        }

        stopVoiceOnlyInternal()
        val style = voiceStyle
        configureEngine(engine, language, style)

        if (style.isClean) {
            speakDirectInternal(engine, text, language, style)
            return
        }

        val num = ++utteranceCounter
        val id = "dex-file-$num"
        val file = File(appContext.cacheDir, "dex_tts_${num}_${System.nanoTime()}.wav")
        val req = SpeechRequest(id, text, language, style, file).also {
            currentRequest = it
            activeRequests[id] = it
            _isSpeaking.value = true
        }

        val result = engine.synthesizeToFile(text, Bundle(), file, id)
        if (result != TextToSpeech.SUCCESS) {
            file.delete()
            fallbackToDirectInternal(req)
        }
    }

    private fun speakDirectInternal(
        engine: TextToSpeech,
        text: String,
        language: AppLanguage,
        style: VoiceStyle,
    ) {
        if (!active) return
        val id = "dex-direct-${++utteranceCounter}"
        val req = SpeechRequest(id, text, language, style, null).also {
            currentRequest = it
            activeRequests[id] = it
            _isSpeaking.value = true
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
        if (result != TextToSpeech.SUCCESS) {
            clearRequestIfCurrent(id, removeFromActive = true)
        }
    }

    private fun fallbackToDirectInternal(failedReq: SpeechRequest) {
        activeRequests.remove(failedReq.id)
        val engine = tts
        if (!active || currentRequest?.id != failedReq.id || engine == null) {
            clearRequestIfCurrent(failedReq.id)
            return
        }
        configureEngine(engine, failedReq.language, failedReq.style)
        speakDirectInternal(engine, failedReq.text, failedReq.language, failedReq.style)
    }

    private fun configureEngine(engine: TextToSpeech, language: AppLanguage, style: VoiceStyle) {
        val locale = language.speechLocale
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.getDefault()
        }
        pickNarratorVoice(engine, locale)?.let { engine.voice = it }
        engine.setPitch(style.ttsPitch)
        engine.setSpeechRate(style.ttsRate)
    }

    /**
     * Prefer an offline male voice for the locale. Engines expose gender only via
     * names/features, so this is heuristic; falls back to the engine default.
     */
    private fun pickNarratorVoice(engine: TextToSpeech, locale: Locale): Voice? = runCatching {
        val candidates = engine.voices.orEmpty().filter {
            it.locale.language == locale.language && !it.isNetworkConnectionRequired &&
                !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }
        fun maleScore(v: Voice): Int {
            val tag = (v.name + " " + v.features.joinToString(" ")).lowercase(Locale.ROOT)
            return when {
                "female" in tag -> -1
                "male" in tag -> 2
                else -> 0
            }
        }
        candidates.maxByOrNull { maleScore(it) * 10 + (if (it.locale.country == locale.country) 1 else 0) }
            ?.takeIf { maleScore(it) > 0 }
    }.getOrNull()

    private fun onSynthesizedInternal(id: String) {
        val req = activeRequests.remove(id) ?: return
        val file = req.file ?: return
        if (!active || currentRequest?.id != id) {
            file.delete()
            return
        }
        processJob?.cancel()
        processJob = dspScope.launch {
            val processed = try {
                RoboVoice.process(RoboVoice.readWav(file), req.style)
            } catch (_: Exception) {
                null
            } finally {
                file.delete()
            }
            postAudio {
                if (!active || currentRequest?.id != req.id) return@postAudio
                if (processed != null) {
                    playPcmInternal(processed, req.id)
                } else {
                    fallbackToDirectInternal(req)
                }
            }
        }
    }

    private fun onFailedInternal(id: String?) {
        if (id == null) {
            currentRequest = null
            _isSpeaking.value = false
            return
        }
        val req = activeRequests.remove(id)
        req?.file?.delete()
        if (req != null && req.file != null) {
            fallbackToDirectInternal(req)
        } else {
            clearRequestIfCurrent(id, removeFromActive = true)
        }
    }

    private fun playPcmInternal(pcm: Pcm, reqId: String) {
        if (pcm.samples.isEmpty() || !active || currentRequest?.id != reqId) {
            clearRequestIfCurrent(reqId)
            return
        }
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(pcm.sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.samples.size * 2)
                .build()
            val written = track.write(pcm.samples, 0, pcm.samples.size)
            if (written <= 0) {
                releaseTrackSafely(track)
                clearRequestIfCurrent(reqId)
                return
            }
            track.notificationMarkerPosition = pcm.samples.size.coerceAtLeast(1)
            val createdTrack = track
            createdTrack.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    postAudio {
                        if (currentRequest?.id == reqId && voiceTrack === createdTrack) {
                            currentRequest = null
                            _isSpeaking.value = false
                        }
                    }
                }

                override fun onPeriodicNotification(t: AudioTrack?) {}
            }, mainHandler)

            val prevTrack = voiceTrack
            voiceTrack = createdTrack
            prevTrack?.let { releaseTrackSafely(it) }
            createdTrack.play()
        } catch (_: Exception) {
            track?.let { releaseTrackSafely(it) }
            clearRequestIfCurrent(reqId)
        }
    }

    private fun clearRequestIfCurrent(id: String, removeFromActive: Boolean = false): SpeechRequest? {
        val removed = if (removeFromActive) activeRequests.remove(id) else null
        if (currentRequest?.id == id) {
            currentRequest = null
            _isSpeaking.value = false
        }
        return removed
    }

    fun stopSpeaking() = postAudio {
        stopVoiceOnlyInternal()
        releaseCryInternal()
    }

    private fun stopVoiceOnlyInternal() {
        pendingSpeech = null
        currentRequest = null
        val files = activeRequests.values.mapNotNull { it.file }
        activeRequests.clear()
        processJob?.cancel()
        processJob = null
        val prevTrack = voiceTrack
        voiceTrack = null
        _isSpeaking.value = false

        files.forEach { it.delete() }
        tts?.runCatching { stop() }
        prevTrack?.let { releaseTrackSafely(it) }
    }

    fun suspend() = postAudio { suspendInternal() }

    private fun suspendInternal() {
        active = false
        val oldCue = cueTrack
        cueTrack = null
        oldCue?.let { releaseTrackSafely(it) }
        stopVoiceOnlyInternal()
        releaseCryInternal()
    }

    fun resume() = postAudio {
        active = true
    }

    fun shutdown() {
        dspScope.cancel()
        if (audioExecutor.isShutdown) return
        runCatching {
            audioScope.launch {
                shutDown = true
                suspendInternal()
                tts?.runCatching { shutdown() }
                tts = null
                audioScope.cancel()
                audioExecutor.shutdown()
            }
        }
    }

    private fun releaseCryInternal(expectedSessionId: Long? = null): Boolean {
        if (expectedSessionId != null && expectedSessionId != crySessionId) return false
        crySessionId++
        val player = cryPlayer
        cryPlayer = null
        _isPlayingCry.value = false
        releasePlayerSafely(player)
        return true
    }

    private fun releasePlayerSafely(player: MediaPlayer?) {
        if (player == null) return
        player.runCatching { if (isPlaying) stop() }
        player.runCatching { release() }
    }

    private fun releaseTrackSafely(track: AudioTrack) {
        track.runCatching { setPlaybackPositionUpdateListener(null) }
        track.runCatching { pause() }
        track.runCatching { flush() }
        track.runCatching { stop() }
        track.runCatching { release() }
    }

    private fun notes(cue: DeviceSound): List<Pair<Double, Double>> = when (cue) {
        DeviceSound.BUTTON -> listOf(1046.5 to 0.045, 1568.0 to 0.045)
        DeviceSound.SCAN -> listOf(523.25 to 0.065, 783.99 to 0.065, 1046.5 to 0.11)
        DeviceSound.SUCCESS -> listOf(659.25 to 0.075, 783.99 to 0.075, 1046.5 to 0.075, 1318.5 to 0.18)
        DeviceSound.ERROR -> listOf(329.63 to 0.10, 246.94 to 0.15)
        DeviceSound.OPEN -> listOf(392.0 to 0.05, 523.25 to 0.05, 659.25 to 0.10)
    }

    /** Soft square wave with short attack/release envelopes. */
    private fun render(notes: List<Pair<Double, Double>>): ShortArray {
        val out = ArrayList<Short>()
        for ((frequency, duration) in notes) {
            val count = (duration * sampleRate).toInt()
            val fade = (0.006 * sampleRate).toInt()
            for (i in 0 until count) {
                val t = i.toDouble() / sampleRate
                val square = if (sin(2 * PI * frequency * t) >= 0) 1.0 else -1.0
                val env = minOf(1.0, i.toDouble() / fade, (count - i).toDouble() / fade)
                out.add((square * env * 0.12 * Short.MAX_VALUE).toInt().toShort())
            }
        }
        return out.toShortArray()
    }
}
