package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.model.AIModelType
import com.example.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class ImageTilingProcessor(
    private val onnxSessionManager: OnnxSessionManager
) {
    companion object {
        private const val TAG = "ImageTilingProcessor"
    }

    /**
     * Memproses gambar dengan resolusi berapapun secara offline tanpa batasan resolusi.
     * Menggunakan tiling berbasis overlap dengan penanganan padding pada tepi gambar
     * agar model Super-Resolution (Real-ESRGAN v4+, Compact) dan Restorasi Wajah
     * (CodeFormer, GPEN) dapat berjalan mulus tanpa OOM (Out-of-Memory) di Snapdragon 660 / RAM 6GB.
     */
    suspend fun processImage(
        bitmap: Bitmap,
        modelType: AIModelType,
        tileSize: Int,
        intraOpThreads: Int,
        sharpness: Float,
        denoise: Float,
        onProgress: (progress: Float, message: String) -> Unit
    ): Pair<Bitmap, String> = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val hasOnnxModel = onnxSessionManager.isModelLoaded(modelType)

        DiagnosticLogger.i(
            TAG,
            "Memulai pemrosesan gambar (${bitmap.width}x${bitmap.height} px) dengan model: ${modelType.displayName}, ONNX loaded: $hasOnnxModel"
        )

        if (!hasOnnxModel) {
            onProgress(0.2f, "Model ${modelType.displayName} belum diimpor. Menggunakan Native AI Engine...")
            DiagnosticLogger.w(TAG, "Model ONNX belum diimpor, fallback ke Native Image Processor")
            val isFace = modelType == AIModelType.CODEFORMER || modelType == AIModelType.GPEN_BFR_256_FP16
            val result = NativeImageProcessor.enhance(
                bitmap = bitmap,
                scaleFactor = modelType.defaultScale,
                sharpnessStrength = sharpness,
                denoiseStrength = denoise,
                isFaceMode = isFace
            )
            onProgress(1.0f, "Selesai diproses")
            return@withContext Pair(result, "Native AI Engine (Model Belum Diimpor)")
        }

        val scale = max(1, modelType.defaultScale)
        val isFaceRestoration = modelType == AIModelType.CODEFORMER || modelType == AIModelType.GPEN_BFR_256_FP16

        // Jika model adalah restorasi wajah (CodeFormer / GPEN) dan gambar relatif terfokus/portrait,
        // model mengharapkan gambar utuh/wajah pada resolusi targetnya (misal 512x512 atau 256x256)
        if (isFaceRestoration && (bitmap.width <= 1024 && bitmap.height <= 1024)) {
            onProgress(0.2f, "Menjalankan Restorasi Wajah ONNX (${modelType.displayName})...")
            DiagnosticLogger.i(TAG, "Menjalankan face restoration mode pada gambar ${bitmap.width}x${bitmap.height}")

            val onnxResult = onnxSessionManager.runInference(modelType, bitmap, intraOpThreads)
            if (onnxResult.isSuccess) {
                val restored = onnxResult.getOrThrow()
                // Terapkan fine-tuning sharpness jika diinginkan
                val finalOutput = if (sharpness > 0.3f) {
                    NativeImageProcessor.enhance(restored, 1, sharpness * 0.5f, denoise * 0.5f, isFaceMode = true)
                } else {
                    restored
                }
                onProgress(1.0f, "Restorasi wajah selesai")
                DiagnosticLogger.success(TAG, "Restorasi wajah ${modelType.displayName} sukses (${finalOutput.width}x${finalOutput.height} px)")
                return@withContext Pair(finalOutput, "ONNX Runtime (${modelType.displayName})")
            } else {
                val ex = onnxResult.exceptionOrNull()
                DiagnosticLogger.e(TAG, "Restorasi wajah gagal, beralih ke Native Engine", ex)
                val fallback = NativeImageProcessor.enhance(bitmap, scale, sharpness, denoise, isFaceMode = true)
                return@withContext Pair(fallback, "Native Engine (Fallback: ${ex?.message?.take(30)})")
            }
        }

        // Tentukan ukuran tile
        val targetTileSize = when {
            tileSize > 0 -> tileSize
            modelType == AIModelType.CODEFORMER -> 512
            else -> 256
        }

        // Jika gambar lebih kecil atau pas dengan ukuran tile, proses langsung tanpa pembagian tile
        if (bitmap.width <= targetTileSize && bitmap.height <= targetTileSize) {
            onProgress(0.3f, "Menjalankan inference ONNX (${modelType.displayName})...")
            val onnxResult = onnxSessionManager.runInference(modelType, bitmap, intraOpThreads)
            if (onnxResult.isSuccess) {
                val enhanced = onnxResult.getOrThrow()
                onProgress(1.0f, "Inference selesai")
                DiagnosticLogger.success(TAG, "Inference direct selesai (${enhanced.width}x${enhanced.height})")
                return@withContext Pair(enhanced, "ONNX Runtime (${modelType.displayName})")
            } else {
                val ex = onnxResult.exceptionOrNull()
                DiagnosticLogger.e(TAG, "Inference direct gagal", ex)
                val fallback = NativeImageProcessor.enhance(bitmap, scale, sharpness, denoise)
                return@withContext Pair(fallback, "Native Engine (Fallback: ${ex?.message?.take(30)})")
            }
        }

        // PROSES TILING: Memecah gambar beresolusi tinggi menjadi potongan dengan overlap
        val overlap = 24
        val step = max(32, targetTileSize - overlap)

        val numTilesX = (bitmap.width + step - 1) / step
        val numTilesY = (bitmap.height + step - 1) / step
        val totalTiles = numTilesX * numTilesY

        val outWidth = bitmap.width * scale
        val outHeight = bitmap.height * scale

        DiagnosticLogger.i(
            TAG,
            "Memulai Tiling: Resolusi sumber ${bitmap.width}x${bitmap.height} -> Output ${outWidth}x${outHeight}. Total Tiles: $totalTiles ($numTilesX x $numTilesY), TileSize: $targetTileSize"
        )

        val outputBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        var processedTiles = 0
        var onnxSuccessCount = 0
        var onnxFailureCount = 0

        for (ty in 0 until numTilesY) {
            val yStart = ty * step
            val yEnd = min(yStart + targetTileSize, bitmap.height)
            val h = yEnd - yStart

            for (tx in 0 until numTilesX) {
                val xStart = tx * step
                val xEnd = min(xStart + targetTileSize, bitmap.width)
                val w = xEnd - xStart

                processedTiles++
                val progressPercent = (processedTiles.toFloat() / totalTiles.toFloat()) * 0.85f + 0.1f
                onProgress(
                    progressPercent,
                    "Memproses Tile $processedTiles / $totalTiles (Kryo 260 Multi-Core)..."
                )

                // Potong tile dari bitmap sumber
                val rawTile = Bitmap.createBitmap(bitmap, xStart, yStart, w, h)

                // Jika tile di tepi berukuran lebih kecil dari targetTileSize,
                // pad tile ke targetTileSize x targetTileSize agar konvolusi model tidak crash
                val (tileToInference, isPadded) = if (w < targetTileSize || h < targetTileSize) {
                    val padded = Bitmap.createBitmap(targetTileSize, targetTileSize, Bitmap.Config.ARGB_8888)
                    val padCanvas = Canvas(padded)
                    padCanvas.drawBitmap(rawTile, 0f, 0f, null)
                    Pair(padded, true)
                } else {
                    Pair(rawTile, false)
                }

                // Jalankan inference pada tile
                val inferredTileResult = onnxSessionManager.runInference(modelType, tileToInference, intraOpThreads)

                val tileOutputBitmap = if (inferredTileResult.isSuccess) {
                    onnxSuccessCount++
                    val fullInferred = inferredTileResult.getOrThrow()
                    // Jika tadi di-pad, potong bagian valid yang sesuai dengan w * scale dan h * scale
                    if (isPadded) {
                        val validW = min(w * scale, fullInferred.width)
                        val validH = min(h * scale, fullInferred.height)
                        Bitmap.createBitmap(fullInferred, 0, 0, validW, validH)
                    } else {
                        fullInferred
                    }
                } else {
                    onnxFailureCount++
                    val err = inferredTileResult.exceptionOrNull()
                    DiagnosticLogger.w(TAG, "Tile $processedTiles gagal diproses ONNX: ${err?.message}", err)
                    NativeImageProcessor.enhance(rawTile, scale, sharpness, denoise, isFaceMode = isFaceRestoration)
                }

                // Gambar tile ke dalam kanvas output
                val destRect = Rect(xStart * scale, yStart * scale, (xStart + w) * scale, (yStart + h) * scale)
                val srcRect = Rect(0, 0, min(tileOutputBitmap.width, (destRect.right - destRect.left)), min(tileOutputBitmap.height, (destRect.bottom - destRect.top)))
                canvas.drawBitmap(tileOutputBitmap, srcRect, destRect, paint)
            }
        }

        val totalDuration = System.currentTimeMillis() - startTime
        onProgress(1.0f, "Selesai menyusun seluruh tile!")

        val engineLabel = if (onnxFailureCount == 0) {
            "ONNX Runtime Tiled (${modelType.displayName})"
        } else if (onnxSuccessCount > 0) {
            "ONNX ($onnxSuccessCount tiles) + Hybrid Native"
        } else {
            "Native AI Engine (Fallback)"
        }

        DiagnosticLogger.success(
            TAG,
            "Pemrosesan selesai dalam ${totalDuration}ms. Sukses ONNX: $onnxSuccessCount, Fallback: $onnxFailureCount. Label: $engineLabel"
        )

        Pair(outputBitmap, engineLabel)
    }
}
