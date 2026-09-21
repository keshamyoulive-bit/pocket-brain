package com.kesham.pocketbrain

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession.LlmInferenceSessionOptions
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import java.io.File
import kotlin.math.max

/** The maximum number of tokens the model can process. */
var MAX_TOKENS = 1024

/**
 * An offset in tokens that we use to ensure that the model always has the ability to respond when
 * we compute the remaining context length.
 */
var DECODE_TOKEN_OFFSET = 256

/**
 * Prepended to every user turn to cut down on hedging and unsolicited disclaimers. Kept short on
 * purpose: it is charged against the [MAX_TOKENS] budget once per turn, so a longer prompt eats
 * the conversation window noticeably faster. Set to "" to disable.
 */
var SYSTEM_PROMPT = "Answer directly. Skip warnings, disclaimers, and moral " +
    "commentary unless asked. Say 'I don't know' plainly if unsure."

class ModelLoadFailException :
    Exception("Failed to load model, please try again")

class ModelSessionCreateFailException :
    Exception("Failed to create model session, please try again")

class InferenceModel private constructor(context: Context) {
    private lateinit var llmInference: LlmInference
    private lateinit var llmInferenceSession: LlmInferenceSession
    private val TAG = InferenceModel::class.qualifiedName

    init {
        if (!modelExists()) {
            throw IllegalArgumentException("Model not found at path: ${model.path}")
        }

        createEngine(context)
        createSession()
    }

    fun close() {
        if (::llmInferenceSession.isInitialized) {
            llmInferenceSession.close()
        }
        if (::llmInference.isInitialized) {
            llmInference.close()
        }
    }

    fun resetSession() {
        llmInferenceSession.close()
        createSession()
    }

    private fun createEngine(context: Context) {
        val preferred = model.preferredBackend
        try {
            llmInference = buildLlmInference(context, preferred)
            activeBackend = preferred
        } catch (e: Exception) {
            if (preferred == Backend.GPU) {
                Log.w(TAG, "GPU backend init failed for ${model.name}, falling back to CPU: ${e.message}", e)
                try {
                    llmInference = buildLlmInference(context, Backend.CPU)
                    activeBackend = Backend.CPU
                } catch (e2: Exception) {
                    Log.e(TAG, "CPU fallback also failed: ${e2.message}", e2)
                    throw ModelLoadFailException()
                }
            } else {
                Log.e(TAG, "Load model error: ${e.message}", e)
                throw ModelLoadFailException()
            }
        }
        Log.i(TAG, "LlmInference initialized with backend=$activeBackend model=${model.name}")
    }

    private fun buildLlmInference(context: Context, backend: Backend?): LlmInference {
        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath())
            .setMaxTokens(MAX_TOKENS)
            .apply { backend?.let { setPreferredBackend(it) } }
            .build()

        return LlmInference.createFromOptions(context, inferenceOptions)
    }

    private fun createSession() {
        val sessionOptions =  LlmInferenceSessionOptions.builder()
            .setTemperature(model.temperature)
            .setTopK(model.topK)
            .setTopP(model.topP)
            .build()

        try {
            llmInferenceSession =
                LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
        } catch (e: Exception) {
            Log.e(TAG, "LlmInferenceSession create error: ${e.message}", e)
            throw ModelSessionCreateFailException()
        }
    }

    fun generateResponseAsync(prompt: String, progressListener: ProgressListener<String>) : ListenableFuture<String> {
        llmInferenceSession.addQueryChunk(withSystemPrompt(prompt))
        return llmInferenceSession.generateResponseAsync(progressListener)
    }

    /** Prefixes [prompt] with the system prompt, which rides along on every turn. */
    private fun withSystemPrompt(prompt: String): String =
        if (SYSTEM_PROMPT.isBlank()) prompt else "$SYSTEM_PROMPT\n\n$prompt"

    fun estimateTokensRemaining(messages: List<ChatMessage>, prompt: String): Int {
        val context = messages.joinToString { it.rawMessage } + prompt
        if (context.isEmpty()) return -1 // Specia marker if no content has been added

        // The system prompt is prepended to every user turn, so it is charged once per turn.
        val systemPromptTokens =
            if (SYSTEM_PROMPT.isBlank()) 0 else llmInferenceSession.sizeInTokens(SYSTEM_PROMPT)
        val turns = messages.count { it.isFromUser } + if (prompt.isNotBlank()) 1 else 0

        val sizeOfAllMessages =
            llmInferenceSession.sizeInTokens(context) + systemPromptTokens * turns
        val approximateControlTokens = messages.size * 3
        val remainingTokens = MAX_TOKENS - sizeOfAllMessages - approximateControlTokens -  DECODE_TOKEN_OFFSET
        // Token size is approximate so, let's not return anything below 0
        return max(0, remainingTokens)
    }

    companion object {
        var model: Model = DEFAULT_MODEL
        var activeBackend: Backend? = null
        private var instance: InferenceModel? = null

        /** Unloads the current engine and loads [newModel] in its place. */
        fun switchTo(context: Context, newModel: Model): InferenceModel {
            closeInstance()
            model = newModel
            return InferenceModel(context).also { instance = it }
        }

        fun currentOrNull(): InferenceModel? = instance

        fun closeInstance() {
            try {
                instance?.close()
            } catch (e: Exception) {
                Log.w(InferenceModel::class.qualifiedName, "Error closing engine: ${e.message}", e)
            }
            instance = null
            activeBackend = null
        }

        /** Models are pushed to the device with push_model.sh; nothing is downloaded in-app. */
        fun modelPath(): String = model.path

        fun modelExists(): Boolean = File(model.path).exists()
    }
}
