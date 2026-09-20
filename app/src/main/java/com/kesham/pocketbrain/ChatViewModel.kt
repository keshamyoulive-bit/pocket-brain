package com.kesham.pocketbrain

import android.content.Context
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

class ChatViewModel(
    private var inferenceModel: InferenceModel?,
    val initError: String? = null
) : ViewModel() {

    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(inferenceModel?.uiState ?: UiState())
    val uiState: StateFlow<UiState> =_uiState.asStateFlow()

    private val _tokensRemaining = MutableStateFlow(-1)
    val tokensRemaining: StateFlow<Int> = _tokensRemaining.asStateFlow()

    private val _textInputEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)
    val isTextInputEnabled: StateFlow<Boolean> = _textInputEnabled.asStateFlow()

    fun resetInferenceModel(newModel: InferenceModel) {
        inferenceModel = newModel
        _uiState.value = newModel.uiState
    }

    fun sendMessage(userMessage: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val model = inferenceModel ?: return@launch
            _uiState.value.addMessage(userMessage, USER_PREFIX)
            _uiState.value.createLoadingMessage()
            setInputEnabled(false)
            val startTimeMs = System.currentTimeMillis()
            var tokenCount = 0
            try {
                val asyncInference =  model.generateResponseAsync(userMessage, { partialResult, done ->
                    tokenCount++
                    _uiState.value.appendMessage(partialResult)
                    if (done) {
                        val elapsedSeconds = (System.currentTimeMillis() - startTimeMs) / 1000.0
                        val tokensPerSecond = if (elapsedSeconds > 0) tokenCount / elapsedSeconds else 0.0
                        _uiState.value.setGenerationStats(tokensPerSecond)
                        setInputEnabled(true)  // Re-enable text input
                    } else {
                        // Reduce current token count (estimate only). sizeInTokens() will be used
                        // when computation is done
                        _tokensRemaining.update { max(0, it - 1) }
                    }
                })
                // Once the inference is done, recompute the remaining size in tokens
                asyncInference.addListener({
                    viewModelScope.launch(Dispatchers.IO) {
                        recomputeSizeInTokens(userMessage)
                    }
                }, Dispatchers.Main.asExecutor())
            } catch (e: Exception) {
                _uiState.value.addMessage(e.localizedMessage ?: "Unknown Error", MODEL_PREFIX)
                setInputEnabled(true)
            }
        }
    }

    private fun setInputEnabled(isEnabled: Boolean) {
        _textInputEnabled.value = isEnabled
    }

    fun recomputeSizeInTokens(message: String) {
        val remainingTokens = inferenceModel?.estimateTokensRemaining(message) ?: return
        _tokensRemaining.value = remainingTokens
    }

    companion object {
        fun getFactory(context: Context) = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return try {
                    val inferenceModel = InferenceModel.getInstance(context)
                    ChatViewModel(inferenceModel) as T
                } catch (e: OutOfMemoryError) {
                    ChatViewModel(
                        inferenceModel = null,
                        initError = "Not enough memory to load this model on this device. Try closing other apps, restarting your phone, or using a smaller model."
                    ) as T
                } catch (e: Throwable) {
                    ChatViewModel(
                        inferenceModel = null,
                        initError = e.localizedMessage ?: "Failed to load the model. Please try again."
                    ) as T
                }
            }
        }
    }
}
