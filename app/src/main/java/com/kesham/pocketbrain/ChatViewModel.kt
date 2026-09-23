package com.kesham.pocketbrain

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

private const val OUT_OF_MEMORY_MESSAGE =
    "Not enough memory to load this model on this device. Try closing other apps, " +
        "restarting your phone, or using a smaller model."

/** Load state of the engine backing the chat. */
sealed interface ModelStatus {
    /** The engine for [model] is being loaded — either the first load or a route switch. */
    data class Loading(val model: Model) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

class ChatViewModel(private val appContext: Context) : ViewModel() {

    val uiState = UiState()

    private val _modelStatus = MutableStateFlow<ModelStatus>(ModelStatus.Loading(DEFAULT_MODEL))
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    private val _modelLabel = MutableStateFlow(DEFAULT_MODEL.displayName)
    val modelLabel: StateFlow<String> = _modelLabel.asStateFlow()

    private val _tokensRemaining = MutableStateFlow(-1)
    val tokensRemaining: StateFlow<Int> = _tokensRemaining.asStateFlow()

    private val _textInputEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isTextInputEnabled: StateFlow<Boolean> = _textInputEnabled.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadModel(DEFAULT_MODEL)
        }
    }

    fun sendMessage(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            uiState.addMessage(userMessage, USER_PREFIX)

            // A missing specialist model shouldn't dead-end the chat: fall back to the default.
            val routed = ModelRouter.route(userMessage) { ModelStorage.isAvailable(appContext, it) }
            var engine = loadModel(routed, reportFailure = routed == DEFAULT_MODEL)
            if (engine == null && routed != DEFAULT_MODEL) {
                uiState.addMessage(
                    "${routed.displayName} isn't available on this device — " +
                        "answering with ${DEFAULT_MODEL.displayName} instead.",
                    MODEL_PREFIX
                )
                engine = loadModel(DEFAULT_MODEL)
            }
            val model = engine ?: return@launch

            uiState.createLoadingMessage(InferenceModel.model.thinking)
            setInputEnabled(false)
            val startTimeMs = System.currentTimeMillis()
            var tokenCount = 0
            var ttftMs = -1L
            try {
                val asyncInference = model.generateResponseAsync(userMessage) { partialResult, done ->
                    tokenCount++
                    if (ttftMs < 0) ttftMs = System.currentTimeMillis() - startTimeMs
                    uiState.appendMessage(partialResult)
                    if (done) {
                        val totalMs = System.currentTimeMillis() - startTimeMs
                        val tokensPerSecond = if (totalMs > 0) tokenCount * 1000.0 / totalMs else 0.0
                        // Decode rate excludes prefill, so it is comparable across prompt lengths.
                        val decodeMs = totalMs - ttftMs
                        val decodeTps =
                            if (decodeMs > 0 && tokenCount > 1) (tokenCount - 1) * 1000.0 / decodeMs else 0.0
                        Log.i(
                            "PocketBrainPerf",
                            "PERF gen model=${InferenceModel.model.name} " +
                                "backend=${InferenceModel.activeBackend} ttftMs=$ttftMs " +
                                "totalMs=$totalMs tokens=$tokenCount " +
                                "tps=${"%.2f".format(tokensPerSecond)} " +
                                "decodeTps=${"%.2f".format(decodeTps)}"
                        )
                        uiState.setGenerationStats(tokensPerSecond)
                        setInputEnabled(true)  // Re-enable text input
                    } else {
                        // Reduce current token count (estimate only). sizeInTokens() will be used
                        // when computation is done. -1 means "not measured yet", so leave it
                        // alone rather than decrementing it into a false "context full".
                        _tokensRemaining.update { if (it < 0) it else max(0, it - 1) }
                    }
                }
                // Once the inference is done, recompute the remaining size in tokens
                asyncInference.addListener({
                    viewModelScope.launch(Dispatchers.IO) {
                        recomputeSizeInTokens(userMessage)
                    }
                }, Dispatchers.Main.asExecutor())
            } catch (e: Exception) {
                uiState.addMessage(e.localizedMessage ?: "Unknown Error", MODEL_PREFIX)
                setInputEnabled(true)
            }
        }
    }

    /**
     * Returns the engine for [target], loading it first if a different model is currently
     * resident. Returns null when loading failed; [reportFailure] controls whether that failure
     * becomes a terminal error screen or is left for the caller to recover from.
     */
    private fun loadModel(target: Model, reportFailure: Boolean = true): InferenceModel? {
        val current = InferenceModel.currentOrNull()
        if (current != null && InferenceModel.model == target) {
            return current
        }

        _modelStatus.value = ModelStatus.Loading(target)
        setInputEnabled(false)
        return try {
            InferenceModel.switchTo(appContext, target).also {
                _modelLabel.value = "${target.displayName} · ${InferenceModel.activeBackend?.name ?: "N/A"}"
                _modelStatus.value = ModelStatus.Ready
                _tokensRemaining.value = -1
                setInputEnabled(true)
            }
        } catch (e: Throwable) {
            val message = if (e is OutOfMemoryError) {
                OUT_OF_MEMORY_MESSAGE
            } else {
                e.localizedMessage ?: "Failed to load ${target.displayName}. Please try again."
            }
            if (reportFailure) {
                _modelStatus.value = ModelStatus.Failed(message)
            } else {
                _modelStatus.value = ModelStatus.Ready
                setInputEnabled(true)
            }
            null
        }
    }

    /** Re-attempts the default model load after a failure (e.g. once the file has been pushed). */
    fun retryLoad() {
        viewModelScope.launch(Dispatchers.IO) {
            loadModel(DEFAULT_MODEL)
        }
    }

    fun clearChat() {
        InferenceModel.currentOrNull()?.resetSession()
        uiState.clearMessages()
        _tokensRemaining.value = -1
    }

    fun closeEngine() {
        InferenceModel.closeInstance()
        uiState.clearMessages()
        _tokensRemaining.value = -1
    }

    private fun setInputEnabled(isEnabled: Boolean) {
        _textInputEnabled.value = isEnabled
    }

    fun recomputeSizeInTokens(message: String) {
        val model = InferenceModel.currentOrNull() ?: return
        _tokensRemaining.value = model.estimateTokensRemaining(uiState.messages, message)
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return ChatViewModel(context) as T
            }
        }
    }
}
