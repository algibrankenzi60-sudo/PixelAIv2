package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppPreferences
import com.example.engine.ImageTilingProcessor
import com.example.engine.OnnxSessionManager
import com.example.model.AIModelType
import com.example.model.ModelStatus
import com.example.util.StorageHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiEvent {
    data class ShowToast(val message: String) : UiEvent
    data class ShareImage(val bitmap: Bitmap) : UiEvent
}

data class EnhanceUiState(
    val sourceBitmap: Bitmap? = null,
    val enhancedBitmap: Bitmap? = null,
    val activeModel: AIModelType = AIModelType.REAL_ESRGAN_V4PLUS,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val progressMessage: String = "",
    val splitPosition: Float = 0.5f,
    val lastProcessingDurationMs: Long = 0L,
    val engineName: String = "",
    val sharpness: Float = 0.65f,
    val denoise: Float = 0.40f,
    val intraOpThreads: Int = 4,
    val tileSize: Int = 256,
    val isSavePng: Boolean = false,
    val modelStatuses: Map<AIModelType, ModelStatus> = emptyMap(),
    val showTutorialDialog: Boolean = false,
    val showModelManagerDialog: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showDiagnosticDialog: Boolean = false
)

class ImageEnhanceViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPreferences(application)
    val onnxSessionManager = OnnxSessionManager(application)
    private val tilingProcessor = ImageTilingProcessor(onnxSessionManager)

    private val _uiState = MutableStateFlow(EnhanceUiState())
    val uiState: StateFlow<EnhanceUiState> = _uiState.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    init {
        loadPreferencesAndStatuses()

        // If first launch, show tutorial immediately as requested by user
        if (!prefs.isTutorialShown) {
            _uiState.update { it.copy(showTutorialDialog = true) }
        }

        // Load a sample test bitmap initially so the UI is immediately alive
        loadSampleImage(isPortrait = false)
    }

    private fun loadPreferencesAndStatuses() {
        val statusMap = mutableMapOf<AIModelType, ModelStatus>()
        AIModelType.entries.forEach { type ->
            val isLoaded = onnxSessionManager.isModelLoaded(type)
            val uri = prefs.getModelUri(type)
            val fileName = prefs.getModelFileName(type) ?: type.expectedFileName
            val fileSize = prefs.getModelFileSize(type)
            statusMap[type] = ModelStatus(
                modelType = type,
                isLinked = isLoaded,
                fileUri = uri,
                fileName = fileName,
                fileSizeBytes = if (isLoaded) onnxSessionManager.getCachedModelFile(type).length() else fileSize,
                isSessionReady = isLoaded
            )
        }

        _uiState.update {
            it.copy(
                activeModel = prefs.lastSelectedModel,
                sharpness = prefs.sharpnessStrength,
                denoise = prefs.denoiseStrength,
                intraOpThreads = prefs.intraOpThreads,
                tileSize = prefs.tileSize,
                isSavePng = prefs.isSaveFormatPng,
                modelStatuses = statusMap
            )
        }
    }

    fun selectModel(modelType: AIModelType) {
        prefs.lastSelectedModel = modelType
        _uiState.update { it.copy(activeModel = modelType) }
    }

    fun setSplitPosition(position: Float) {
        _uiState.update { it.copy(splitPosition = position.coerceIn(0f, 1f)) }
    }

    fun setSharpness(value: Float) {
        prefs.sharpnessStrength = value
        _uiState.update { it.copy(sharpness = value) }
    }

    fun setDenoise(value: Float) {
        prefs.denoiseStrength = value
        _uiState.update { it.copy(denoise = value) }
    }

    fun setThreads(threads: Int) {
        prefs.intraOpThreads = threads
        _uiState.update { it.copy(intraOpThreads = threads) }
    }

    fun setTileSize(size: Int) {
        prefs.tileSize = size
        _uiState.update { it.copy(tileSize = size) }
    }

    fun setSaveFormatPng(isPng: Boolean) {
        prefs.isSaveFormatPng = isPng
        _uiState.update { it.copy(isSavePng = isPng) }
    }

    fun setSourceBitmap(bitmap: Bitmap) {
        _uiState.update {
            it.copy(
                sourceBitmap = bitmap,
                enhancedBitmap = null,
                splitPosition = 0.5f,
                progress = 0f,
                progressMessage = ""
            )
        }
    }

    fun loadFromUri(uri: Uri) {
        viewModelScope.launch {
            val bitmap = StorageHelper.loadBitmapFromUri(getApplication(), uri)
            if (bitmap != null) {
                setSourceBitmap(bitmap)
                _uiEvents.emit(UiEvent.ShowToast("Foto berhasil dimuat (${bitmap.width}x${bitmap.height} px)"))
            } else {
                _uiEvents.emit(UiEvent.ShowToast("Gagal membaca foto dari galeri"))
            }
        }
    }

    fun loadSampleImage(isPortrait: Boolean) {
        viewModelScope.launch {
            val bmp = if (isPortrait) {
                StorageHelper.createSamplePortraitBitmap()
            } else {
                StorageHelper.createSampleSceneryBitmap()
            }
            setSourceBitmap(bmp)
        }
    }

    fun importModelFromUri(modelType: AIModelType, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Mengimpor ${modelType.expectedFileName}...") }

            // Retrieve display name and size from content resolver
            var originalName = modelType.expectedFileName
            var sizeBytes = 0L
            val context = getApplication<Application>()
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1) originalName = cursor.getString(nameIndex) ?: originalName
                        if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                // Ignore cursor query failure
            }

            val result = onnxSessionManager.importModelFromUri(modelType, uri)
            if (result.isSuccess) {
                val file = result.getOrThrow()
                prefs.saveModelUri(modelType, uri, originalName, file.length())
                loadPreferencesAndStatuses()
                _uiEvents.emit(UiEvent.ShowToast("Model ${modelType.displayName} berhasil dimuat (${String.format("%.1f", file.length() / (1024.0 * 1024.0))} MB)"))
            } else {
                _uiEvents.emit(UiEvent.ShowToast("Gagal memuat model: ${result.exceptionOrNull()?.message}"))
            }

            _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
        }
    }

    fun processActiveImage() {
        val currentState = _uiState.value
        val bitmap = currentState.sourceBitmap ?: return
        if (currentState.isProcessing) return

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progress = 0.05f,
                    progressMessage = "Mempersiapkan pipeline pengolahan..."
                )
            }

            try {
                val (resultBitmap, engineLabel) = tilingProcessor.processImage(
                    bitmap = bitmap,
                    modelType = currentState.activeModel,
                    tileSize = currentState.tileSize,
                    intraOpThreads = currentState.intraOpThreads,
                    sharpness = currentState.sharpness,
                    denoise = currentState.denoise
                ) { p, msg ->
                    _uiState.update { it.copy(progress = p, progressMessage = msg) }
                }

                val duration = System.currentTimeMillis() - startTime
                _uiState.update {
                    it.copy(
                        enhancedBitmap = resultBitmap,
                        isProcessing = false,
                        progress = 1f,
                        progressMessage = "Selesai dalam ${duration}ms",
                        lastProcessingDurationMs = duration,
                        engineName = engineLabel
                    )
                }

                _uiEvents.emit(
                    UiEvent.ShowToast(
                        "Foto berhasil ditingkatkan! (${resultBitmap.width}x${resultBitmap.height} px, ${duration}ms)"
                    )
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        progressMessage = "Gagal: ${e.message}"
                    )
                }
                _uiEvents.emit(UiEvent.ShowToast("Gagal memproses gambar: ${e.message}"))
            }
        }
    }

    fun saveEnhancedImage() {
        val bmp = _uiState.value.enhancedBitmap ?: _uiState.value.sourceBitmap ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Menyimpan ke galeri...") }
            val result = StorageHelper.saveBitmapToGallery(
                context = getApplication(),
                bitmap = bmp,
                isPng = _uiState.value.isSavePng
            )
            _uiState.update { it.copy(isProcessing = false, progressMessage = "") }

            if (result.isSuccess) {
                _uiEvents.emit(UiEvent.ShowToast("Foto tersimpan di Galeri (Pictures/AI_Image_Lab)!"))
            } else {
                _uiEvents.emit(UiEvent.ShowToast("Gagal menyimpan: ${result.exceptionOrNull()?.message}"))
            }
        }
    }

    fun shareEnhancedImage() {
        val bmp = _uiState.value.enhancedBitmap ?: _uiState.value.sourceBitmap ?: return
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShareImage(bmp))
        }
    }

    fun showTutorial(show: Boolean) {
        if (!show) {
            prefs.isTutorialShown = true
        }
        _uiState.update { it.copy(showTutorialDialog = show) }
    }

    fun showModelManager(show: Boolean) {
        _uiState.update { it.copy(showModelManagerDialog = show) }
    }

    fun showSettings(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun showDiagnostic(show: Boolean) {
        _uiState.update { it.copy(showDiagnosticDialog = show) }
    }

    override fun onCleared() {
        super.onCleared()
        onnxSessionManager.releaseAll()
    }
}
