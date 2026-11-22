package com.idex.android.recognition

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import com.idex.android.data.PokemonCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.nio.FloatBuffer
import kotlin.math.exp

/** A model score, not a calibrated probability that a Pokémon is present. */
data class RecognitionCandidate(val dexId: Int, val name: String, val confidence: Double, val logit: Double)

data class RecognitionResult(
    val candidates: List<RecognitionCandidate>,
    val accepted: RecognitionCandidate?,
    val logitMargin: Double,
)

class RecognitionException(message: String) : Exception(message)

/** Pure JVM-testable scoring, ranking, and acceptance policy for classifier logits. */
object PokemonScoring {
    private const val TOP_K = 3
    private const val MIN_CONFIDENCE = 0.25
    private const val MIN_MARGIN = 0.5

    fun score(logits: DoubleArray, labels: List<Pair<Int, String>>): RecognitionResult {
        if (labels.isEmpty() || logits.size != labels.size || logits.any { !it.isFinite() }) {
            throw RecognitionException("The recognition model returned invalid scores.")
        }
        val max = logits.max()
        val exps = DoubleArray(logits.size) { exp(logits[it] - max) }
        val denominator = exps.sum()
        if (!denominator.isFinite() || denominator <= 0.0) {
            throw RecognitionException("The recognition model returned invalid scores.")
        }
        val order = logits.indices.sortedByDescending { logits[it] }
        val candidates = order.take(TOP_K).map { i ->
            RecognitionCandidate(labels[i].first, labels[i].second, exps[i] / denominator, logits[i])
        }
        val margin = if (order.size >= 2) logits[order[0]] - logits[order[1]] else Double.POSITIVE_INFINITY
        val first = candidates[0]
        val accepted = if (first.confidence >= MIN_CONFIDENCE && margin >= MIN_MARGIN) first else null
        return RecognitionResult(candidates, accepted, margin)
    }
}

/**
 * Offline Vision Transformer classifier for all 1025 Pokémon species across Gen I–IX (ONNX Runtime).
 * Top candidates and acceptance evaluated locally; no image ever leaves the device.
 */
class PokemonClassifier(private val context: Context) {
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var closed = false
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var sessionOptions: OrtSession.SessionOptions? = null
    private var labels: List<Pair<Int, String>> = emptyList()

    suspend fun warmUp() = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (!closed) loadIfNecessary()
        }
    }

    suspend fun classify(image: Bitmap): RecognitionResult = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (closed) throw RecognitionException("Classifier is closed.")
            loadIfNecessary()
            val environment = env ?: throw RecognitionException("Classifier is unavailable.")
            val activeSession = session ?: throw RecognitionException("Classifier is unavailable.")
            val input = toTensorData(image)
            val logits = OnnxTensor.createTensor(
                environment,
                input,
                longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong()),
            ).use { tensor ->
                activeSession.run(mapOf(INPUT to tensor)).use { output ->
                    @Suppress("UNCHECKED_CAST")
                    val batch = output[0].value as? Array<FloatArray>
                        ?: throw RecognitionException("Unexpected recognition output shape.")
                    val values = batch.firstOrNull()
                        ?: throw RecognitionException("Empty recognition output.")
                    DoubleArray(values.size) { values[it].toDouble() }
                }
            }
            PokemonScoring.score(logits, labels)
        }
    }

    private fun loadIfNecessary() {
        if (closed || session != null) return
        val labelText = context.assets.open("pokemon_labels.json").bufferedReader().use { it.readText() }
        val array = JSONArray(labelText)
        val seenLabelIds = HashSet<Int>(array.length())
        val loaded = (0 until array.length()).map {
            val o = array.getJSONObject(it)
            val id = o.getInt("id")
            val name = o.getString("name").trim()
            if (name.isEmpty() || !seenLabelIds.add(id)) {
                throw RecognitionException("The recognition label data is invalid.")
            }
            id to name
        }
        val catalog = PokemonCatalog.get(context)
        if (loaded.isEmpty() || loaded.any { catalog.pokemon(it.first) == null }) {
            throw RecognitionException("The recognition label data is invalid.")
        }
        val bytes = context.assets.open(MODEL).use { it.readBytes() }
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        val newSession = try {
            environment.createSession(bytes, options)
        } catch (t: Throwable) {
            options.runCatching { close() }
            throw t
        }
        if (closed) {
            newSession.runCatching { close() }
            options.runCatching { close() }
            return
        }
        session = newSession
        sessionOptions = options
        env = environment
        labels = loaded
    }

    fun close() {
        closed = true
        cleanupScope.launch {
            mutex.withLock {
                session?.runCatching { close() }
                session = null
                sessionOptions?.runCatching { close() }
                sessionOptions = null
                env = null
                labels = emptyList()
            }
        }
    }

    companion object {
        const val SIZE = 224
        private const val MODEL = "national_classifier.onnx"
        private const val INPUT = "image"

        /** Stretch to 224×224 (scale-fill, matching Core ML) → NCHW float in 0..1. */
        fun toTensorData(image: Bitmap): FloatBuffer {
            val scaled = Bitmap.createScaledBitmap(image, SIZE, SIZE, true)
            val pixels = IntArray(SIZE * SIZE)
            scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            if (scaled !== image) scaled.recycle()
            val plane = SIZE * SIZE
            val buffer = FloatBuffer.allocate(3 * plane)
            for (i in 0 until plane) {
                val p = pixels[i]
                buffer.put(i, ((p shr 16) and 0xFF) / 255f)
                buffer.put(plane + i, ((p shr 8) and 0xFF) / 255f)
                buffer.put(2 * plane + i, (p and 0xFF) / 255f)
            }
            buffer.rewind()
            return buffer
        }
    }
}
