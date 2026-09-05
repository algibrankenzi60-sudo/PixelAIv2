package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.example.model.AIModelType

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_image_lab_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TUTORIAL_SHOWN = "key_tutorial_shown"
        private const val KEY_INTRA_OP_THREADS = "key_intra_op_threads"
        private const val KEY_TILE_SIZE = "key_tile_size"
        private const val KEY_SAVE_FORMAT_PNG = "key_save_format_png"
        private const val KEY_LAST_SELECTED_MODEL = "key_last_selected_model"
        private const val KEY_SHARPNESS_STRENGTH = "key_sharpness_strength"
        private const val KEY_DENOISE_STRENGTH = "key_denoise_strength"
        private const val PREFIX_MODEL_URI = "model_uri_"
        private const val PREFIX_MODEL_NAME = "model_name_"
        private const val PREFIX_MODEL_SIZE = "model_size_"
    }

    var isTutorialShown: Boolean
        get() = prefs.getBoolean(KEY_TUTORIAL_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_TUTORIAL_SHOWN, value).apply()

    // Snapdragon 660 has 4 Performance Kryo 260 Gold cores
    var intraOpThreads: Int
        get() = prefs.getInt(KEY_INTRA_OP_THREADS, 4)
        set(value) = prefs.edit().putInt(KEY_INTRA_OP_THREADS, value.coerceIn(1, 8)).apply()

    // 0 = Off (Full Image), 256 = 256x256 Tiles (Safest for SD660), 512 = 512x512 Tiles
    var tileSize: Int
        get() = prefs.getInt(KEY_TILE_SIZE, 256)
        set(value) = prefs.edit().putInt(KEY_TILE_SIZE, value).apply()

    var isSaveFormatPng: Boolean
        get() = prefs.getBoolean(KEY_SAVE_FORMAT_PNG, false)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_FORMAT_PNG, value).apply()

    var lastSelectedModel: AIModelType
        get() = AIModelType.fromId(prefs.getString(KEY_LAST_SELECTED_MODEL, AIModelType.REAL_ESRGAN_V4PLUS.id) ?: AIModelType.REAL_ESRGAN_V4PLUS.id)
        set(value) = prefs.edit().putString(KEY_LAST_SELECTED_MODEL, value.id).apply()

    var sharpnessStrength: Float
        get() = prefs.getFloat(KEY_SHARPNESS_STRENGTH, 0.65f)
        set(value) = prefs.edit().putFloat(KEY_SHARPNESS_STRENGTH, value).apply()

    var denoiseStrength: Float
        get() = prefs.getFloat(KEY_DENOISE_STRENGTH, 0.40f)
        set(value) = prefs.edit().putFloat(KEY_DENOISE_STRENGTH, value).apply()

    fun saveModelUri(modelType: AIModelType, uri: Uri?, fileName: String?, fileSizeBytes: Long) {
        prefs.edit().apply {
            if (uri != null) {
                putString(PREFIX_MODEL_URI + modelType.id, uri.toString())
                putString(PREFIX_MODEL_NAME + modelType.id, fileName)
                putLong(PREFIX_MODEL_SIZE + modelType.id, fileSizeBytes)
            } else {
                remove(PREFIX_MODEL_URI + modelType.id)
                remove(PREFIX_MODEL_NAME + modelType.id)
                remove(PREFIX_MODEL_SIZE + modelType.id)
            }
        }.apply()
    }

    fun getModelUri(modelType: AIModelType): Uri? {
        val str = prefs.getString(PREFIX_MODEL_URI + modelType.id, null) ?: return null
        return try {
            Uri.parse(str)
        } catch (e: Exception) {
            null
        }
    }

    fun getModelFileName(modelType: AIModelType): String? {
        return prefs.getString(PREFIX_MODEL_NAME + modelType.id, null)
    }

    fun getModelFileSize(modelType: AIModelType): Long {
        return prefs.getLong(PREFIX_MODEL_SIZE + modelType.id, 0L)
    }
}
