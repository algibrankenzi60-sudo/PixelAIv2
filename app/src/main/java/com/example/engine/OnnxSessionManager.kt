package com.example.engine

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import com.example.model.AIModelType
import com.example.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

data class ModelNodeDetail(
    val name: String,
    val type: String,
    val shape: String,
    val isFloat16: Boolean
)

data class ModelDiagnosticInfo(
    val modelType: AIModelType,
    val isFilePresent: Boolean,
    val filePath: String,
    val fileSizeBytes: Long,
    val inputs: List<ModelNodeDetail> = emptyList(),
    val outputs: List<ModelNodeDetail> = emptyList(),
    val errorMessage: String? = null
)

data class DiagnosticTestResult(
    val modelType: AIModelType,
    val isSuccess: Boolean,
    val logs: List<String>,
    val error: String? = null,
    val executionDurationMs: Long = 0L,
    val outputDimensions: String = ""
)

class OnnxSessionManager(private val context: Context) {
    companion object {
        private const val TAG = "OnnxSessionManager"

        /**
         * Mengonversi Float32 standar (IEEE 754) menjadi Float16 (Half-Precision) Short.
         * Penting untuk model seperti GPEN-bfr-256.fp16.onnx
         */
        fun floatToHalf(fval: Float): Short {
            val fbits = java.lang.Float.floatToIntBits(fval)
            val sign = (fbits ushr 16) and 0x8000
            var valInt = (fbits and 0x7fffffff) + 0x1000
            if (valInt >= 0x47800000) {
                if ((fbits and 0x7fffffff) >= 0x47800000) {
                    if (valInt >= 0x7f800000) {
                        return (sign or 0x7c00 or ((fbits and 0x007fffff) ushr 13)).toShort()
                    }
                    return (sign or 0x7c00).toShort()
                }
                return (sign or 0x7bff).toShort()
            }
            if (valInt >= 0x38800000) {
                return (sign or ((valInt - 0x38000000) ushr 13)).toShort()
            }
            if (valInt < 0x33000000) {
                return sign.toShort()
            }
            valInt = (fbits and 0x7fffffff) ushr 23
            return (sign or ((((fbits and 0x7fffff) or 0x800000) + (0x800000 ushr (valInt - 102))) ushr (126 - valInt))).toShort()
        }

        /**
         * Mengonversi Float16 (Half-Precision) Short menjadi Float32 standar.
         */
        fun halfToFloat(hval: Short): Float {
            val bits = hval.toInt() and 0xffff
            val sign = (bits and 0x8000) shl 16
            val exp = (bits and 0x7c00) ushr 10
            val mant = bits and 0x03ff
            if (exp == 0) {
                if (mant == 0) return java.lang.Float.intBitsToFloat(sign)
                var m = mant
                var e = 0
                while ((m and 0x0400) == 0) {
                    m = m shl 1
                    e++
                }
                val exp32 = (127 - 15 - e + 1) shl 23
                val mant32 = (m and 0x03ff) shl 13
                return java.lang.Float.intBitsToFloat(sign or exp32 or mant32)
            } else if (exp == 31) {
                val exp32 = 0xff shl 23
                val mant32 = mant shl 13
                return java.lang.Float.intBitsToFloat(sign or exp32 or mant32)
            }
            val exp32 = (exp + (127 - 15)) shl 23
            val mant32 = mant shl 13
            return java.lang.Float.intBitsToFloat(sign or exp32 or mant32)
        }
    }

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val activeSessions = ConcurrentHashMap<AIModelType, OrtSession>()

    fun getCachedModelFile(modelType: AIModelType): File {
        val dir = File(context.filesDir, "onnx_models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${modelType.id}.onnx")
    }

    fun isModelLoaded(modelType: AIModelType): Boolean {
        val file = getCachedModelFile(modelType)
        return file.exists() && file.length() > 1024
    }

    suspend fun importModelFromUri(modelType: AIModelType, uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetFile = getCachedModelFile(modelType)
            DiagnosticLogger.i(TAG, "Mengimpor model ${modelType.displayName} dari URI: $uri -> ${targetFile.absolutePath}")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            } ?: return@withContext Result.failure(Exception("Gagal membuka stream input dari URI"))

            if (targetFile.length() < 1024) {
                targetFile.delete()
                val err = Exception("File model kosong atau terlalu kecil (${targetFile.length()} bytes)")
                DiagnosticLogger.e(TAG, "Gagal impor model", err)
                return@withContext Result.failure(err)
            }

            // Invalidate existing session
            closeSession(modelType)

            // Validate model can be loaded by ONNX Runtime
            val session = createSessionForFile(targetFile, 4)
            activeSessions[modelType] = session

            val diag = inspectSession(modelType, session, targetFile)
            DiagnosticLogger.success(
                TAG,
                "Model ${modelType.displayName} berhasil dimuat! Input: ${diag.inputs.map { "${it.name}:${it.type}${it.shape}" }}, Output: ${diag.outputs.map { "${it.name}:${it.type}${it.shape}" }}"
            )

            Result.success(targetFile)
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error mengimpor model ${modelType.displayName}", e)
            Result.failure(e)
        }
    }

    private fun createSessionForFile(file: File, intraOpThreads: Int): OrtSession {
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(intraOpThreads.coerceIn(1, 6))
            setInterOpNumThreads(1)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setMemoryPatternOptimization(true)
        }
        return env.createSession(file.absolutePath, options)
    }

    suspend fun getOrCreateSession(modelType: AIModelType, intraOpThreads: Int): OrtSession? = withContext(Dispatchers.IO) {
        val existing = activeSessions[modelType]
        if (existing != null) return@withContext existing

        val file = getCachedModelFile(modelType)
        if (!file.exists() || file.length() <= 1024) {
            DiagnosticLogger.w(TAG, "File model ${modelType.displayName} belum ada di disk.")
            return@withContext null
        }

        try {
            val session = createSessionForFile(file, intraOpThreads)
            activeSessions[modelType] = session
            session
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Gagal membuat sesi ONNX untuk ${modelType.displayName}", e)
            null
        }
    }

    /**
     * Menginspeksi model ONNX secara mendalam untuk keperluan Diagnostik
     */
    suspend fun inspectModel(modelType: AIModelType): ModelDiagnosticInfo = withContext(Dispatchers.IO) {
        val file = getCachedModelFile(modelType)
        if (!file.exists() || file.length() <= 1024) {
            return@withContext ModelDiagnosticInfo(
                modelType = modelType,
                isFilePresent = false,
                filePath = file.absolutePath,
                fileSizeBytes = 0L,
                errorMessage = "File model belum diimpor ke aplikasi."
            )
        }

        try {
            val session = getOrCreateSession(modelType, 4)
                ?: return@withContext ModelDiagnosticInfo(
                    modelType = modelType,
                    isFilePresent = true,
                    filePath = file.absolutePath,
                    fileSizeBytes = file.length(),
                    errorMessage = "Gagal memuat sesi ONNX dari file."
                )

            inspectSession(modelType, session, file)
        } catch (e: Exception) {
            ModelDiagnosticInfo(
                modelType = modelType,
                isFilePresent = true,
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                errorMessage = "Error inspeksi: ${e.message}"
            )
        }
    }

    private fun inspectSession(modelType: AIModelType, session: OrtSession, file: File): ModelDiagnosticInfo {
        val inputs = mutableListOf<ModelNodeDetail>()
        for ((name, nodeInfo) in session.inputInfo) {
            val tInfo = nodeInfo.info as? TensorInfo
            val typeStr = tInfo?.type?.toString() ?: "UNKNOWN"
            val shapeStr = tInfo?.shape?.contentToString() ?: "[]"
            val isFp16 = tInfo?.type == OnnxJavaType.FLOAT16
            inputs.add(ModelNodeDetail(name, typeStr, shapeStr, isFp16))
        }

        val outputs = mutableListOf<ModelNodeDetail>()
        for ((name, nodeInfo) in session.outputInfo) {
            val tInfo = nodeInfo.info as? TensorInfo
            val typeStr = tInfo?.type?.toString() ?: "UNKNOWN"
            val shapeStr = tInfo?.shape?.contentToString() ?: "[]"
            val isFp16 = tInfo?.type == OnnxJavaType.FLOAT16
            outputs.add(ModelNodeDetail(name, typeStr, shapeStr, isFp16))
        }

        return ModelDiagnosticInfo(
            modelType = modelType,
            isFilePresent = true,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            inputs = inputs,
            outputs = outputs
        )
    }

    /**
     * Menjalankan uji coba inference (diagnostik) pada model dengan log lengkap
     */
    suspend fun runDiagnosticTest(modelType: AIModelType): DiagnosticTestResult = withContext(Dispatchers.Default) {
        val logs = mutableListOf<String>()
        val startTotal = System.currentTimeMillis()

        logs.add("Memulai uji coba diagnostik model: ${modelType.displayName}...")

        val session = try {
            getOrCreateSession(modelType, 4)
        } catch (e: Exception) {
            logs.add("GAGAL: Error inisialisasi sesi ONNX: ${e.message}")
            return@withContext DiagnosticTestResult(
                modelType = modelType,
                isSuccess = false,
                logs = logs,
                error = e.stackTraceToString()
            )
        }

        if (session == null) {
            logs.add("GAGAL: Sesi ONNX bernilai null (apakah file .onnx sudah diimpor?)")
            return@withContext DiagnosticTestResult(
                modelType = modelType,
                isSuccess = false,
                logs = logs,
                error = "File model belum diimpor atau format .onnx rusak"
            )
        }

        logs.add("Sesi ONNX aktif. Memeriksa spesifikasi tensor...")

        try {
            // Analisis input
            val imageInputNode = findImageInputNode(session)
            if (imageInputNode == null) {
                logs.add("GAGAL: Tidak dapat menemukan node input gambar pada model.")
                return@withContext DiagnosticTestResult(
                    modelType = modelType,
                    isSuccess = false,
                    logs = logs,
                    error = "Node input gambar tidak ditemukan"
                )
            }

            val (inputName, nodeInfo) = imageInputNode
            val tensorInfo = nodeInfo.info as TensorInfo
            val isFp16 = tensorInfo.type == OnnxJavaType.FLOAT16
            val expectedShape = tensorInfo.shape

            logs.add("Node gambar: '$inputName', Tipe: ${tensorInfo.type}, Shape: ${expectedShape.contentToString()}")

            // Tentukan ukuran uji coba
            val testW = if (expectedShape.size >= 4 && expectedShape[3] > 0) expectedShape[3].toInt() else 128
            val testH = if (expectedShape.size >= 4 && expectedShape[2] > 0) expectedShape[2].toInt() else 128
            logs.add("Menyiapkan test patch gambar: ${testW}x${testH} px...")

            val testBitmap = Bitmap.createBitmap(testW, testH, Bitmap.Config.ARGB_8888).apply {
                val canvas = Canvas(this)
                val paint = Paint().apply { color = android.graphics.Color.BLUE }
                canvas.drawRect(0f, 0f, testW.toFloat(), testH.toFloat(), paint)
            }

            // Jalankan inference
            val inferenceStart = System.currentTimeMillis()
            val result = runInference(modelType, testBitmap, 4)
            val duration = System.currentTimeMillis() - inferenceStart

            if (result.isSuccess) {
                val outputBmp = result.getOrThrow()
                logs.add("BERHASIL: Inference sukses dalam ${duration}ms! Ukuran output: ${outputBmp.width}x${outputBmp.height} px")
                DiagnosticLogger.success(TAG, "Diagnostik ${modelType.displayName} BERHASIL (${duration}ms)")

                DiagnosticTestResult(
                    modelType = modelType,
                    isSuccess = true,
                    logs = logs,
                    executionDurationMs = duration,
                    outputDimensions = "${outputBmp.width}x${outputBmp.height} px"
                )
            } else {
                val ex = result.exceptionOrNull()
                logs.add("GAGAL saat eksekusi model: ${ex?.message}")
                DiagnosticLogger.e(TAG, "Diagnostik ${modelType.displayName} GAGAL", ex)

                DiagnosticTestResult(
                    modelType = modelType,
                    isSuccess = false,
                    logs = logs,
                    error = ex?.stackTraceToString() ?: "Unknown error"
                )
            }
        } catch (e: Exception) {
            logs.add("EXCEPTION: ${e.message}")
            DiagnosticLogger.e(TAG, "Diagnostik exception", e)
            DiagnosticTestResult(
                modelType = modelType,
                isSuccess = false,
                logs = logs,
                error = e.stackTraceToString()
            )
        }
    }

    private fun findImageInputNode(session: OrtSession): Map.Entry<String, NodeInfo>? {
        // Cari node dengan 4 dimensi (NCHW atau NHWC)
        for (entry in session.inputInfo) {
            val tInfo = entry.value.info as? TensorInfo ?: continue
            val shape = tInfo.shape
            if (shape.size == 4) {
                return entry
            }
        }
        // Jika tidak ada yang pasti 4D, kembalikan input pertama
        return session.inputInfo.entries.firstOrNull()
    }

    /**
     * Menjalankan AI Inference dengan auto-adapting untuk:
     * - Multi-inputs (seperti CodeFormer yang membutuhkan input gambar + bobot 'w'/'weight')
     * - Float16 / FP16 (seperti GPEN FP16)
     * - Fixed input dimensions (512x512 untuk CodeFormer, 256x256 untuk GPEN)
     * - Dynamic NCHW / NHWC tensor formats
     * - Auto output dynamic range detection ([-1, 1], [0, 1], [0, 255])
     */
    suspend fun runInference(
        modelType: AIModelType,
        inputBitmap: Bitmap,
        intraOpThreads: Int
    ): Result<Bitmap> = withContext(Dispatchers.Default) {
        val session = getOrCreateSession(modelType, intraOpThreads)
            ?: return@withContext Result.failure(Exception("Model ONNX ${modelType.displayName} belum dimuat."))

        val createdTensors = mutableListOf<OnnxTensor>()

        try {
            val imageInputEntry = findImageInputNode(session)
                ?: return@withContext Result.failure(Exception("Node input gambar tidak ditemukan pada model"))

            val imageInputName = imageInputEntry.key
            val imageTensorInfo = imageInputEntry.value.info as TensorInfo
            val isFloat16 = imageTensorInfo.type == OnnxJavaType.FLOAT16
            val expectedShape = imageTensorInfo.shape

            // Tentukan target dimensi yang diharapkan model
            val requiredH = if (expectedShape.size >= 4 && expectedShape[2] > 0) expectedShape[2].toInt() else inputBitmap.height
            val requiredW = if (expectedShape.size >= 4 && expectedShape[3] > 0) expectedShape[3].toInt() else inputBitmap.width

            // Sesuaikan bitmap jika model membutuhkan fixed size (misal CodeFormer 512x512 atau GPEN 256x256)
            val preparedBitmap = if (inputBitmap.width != requiredW || inputBitmap.height != requiredH) {
                Bitmap.createScaledBitmap(inputBitmap, requiredW, requiredH, true)
            } else {
                inputBitmap
            }

            val width = preparedBitmap.width
            val height = preparedBitmap.height

            // Normalisasi nilai pixel:
            // CodeFormer dan GPEN menggunakan [-1.0, 1.0]
            // Real-ESRGAN menggunakan [0.0, 1.0]
            val isNegativeOneToOne = modelType == AIModelType.CODEFORMER || modelType == AIModelType.GPEN_BFR_256_FP16

            val pixels = IntArray(width * height)
            preparedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val channelStride = width * height
            val shape = longArrayOf(1, 3, height.toLong(), width.toLong())

            val inputMap = mutableMapOf<String, OnnxTensor>()

            // Siapkan Tensor Gambar (FP16 atau FP32)
            if (isFloat16) {
                val shortBuffer = ShortBuffer.allocate(3 * width * height)
                var idx = 0
                val rShorts = ShortArray(channelStride)
                val gShorts = ShortArray(channelStride)
                val bShorts = ShortArray(channelStride)

                for (i in 0 until channelStride) {
                    val pixel = pixels[i]
                    var r = (pixel shr 16 and 0xFF) / 255.0f
                    var g = (pixel shr 8 and 0xFF) / 255.0f
                    var b = (pixel and 0xFF) / 255.0f

                    if (isNegativeOneToOne) {
                        r = (r - 0.5f) / 0.5f
                        g = (g - 0.5f) / 0.5f
                        b = (b - 0.5f) / 0.5f
                    }

                    rShorts[i] = floatToHalf(r)
                    gShorts[i] = floatToHalf(g)
                    bShorts[i] = floatToHalf(b)
                }

                shortBuffer.put(rShorts)
                shortBuffer.put(gShorts)
                shortBuffer.put(bShorts)
                shortBuffer.flip()

                val imageTensor = OnnxTensor.createTensor(env, shortBuffer, shape, OnnxJavaType.FLOAT16)
                createdTensors.add(imageTensor)
                inputMap[imageInputName] = imageTensor
            } else {
                val floatBuffer = FloatBuffer.allocate(3 * width * height)
                val rFloats = FloatArray(channelStride)
                val gFloats = FloatArray(channelStride)
                val bFloats = FloatArray(channelStride)

                for (i in 0 until channelStride) {
                    val pixel = pixels[i]
                    var r = (pixel shr 16 and 0xFF) / 255.0f
                    var g = (pixel shr 8 and 0xFF) / 255.0f
                    var b = (pixel and 0xFF) / 255.0f

                    if (isNegativeOneToOne) {
                        r = (r - 0.5f) / 0.5f
                        g = (g - 0.5f) / 0.5f
                        b = (b - 0.5f) / 0.5f
                    }

                    rFloats[i] = r
                    gFloats[i] = g
                    bFloats[i] = b
                }

                floatBuffer.put(rFloats)
                floatBuffer.put(gFloats)
                floatBuffer.put(bFloats)
                floatBuffer.flip()

                val imageTensor = OnnxTensor.createTensor(env, floatBuffer, shape)
                createdTensors.add(imageTensor)
                inputMap[imageInputName] = imageTensor
            }

            // Tangani input sekunder (seperti 'weight' atau 'w' pada CodeFormer)
            for ((name, nodeInfo) in session.inputInfo) {
                if (name == imageInputName) continue
                val tInfo = nodeInfo.info as? TensorInfo ?: continue
                val inputType = tInfo.type

                val lowerName = name.lowercase()
                DiagnosticLogger.d(TAG, "Menyiapkan input sekunder '$name' (${tInfo.type}, shape: ${tInfo.shape.contentToString()})")

                // Bobot fidelity CodeFormer (biasanya 0.5f atau 0.7f)
                val scalarVal = if (lowerName.contains("weight") || lowerName == "w" || lowerName.contains("fidelity")) 0.7f else 1.0f

                val secondaryTensor = if (inputType == OnnxJavaType.FLOAT16) {
                    val sBuf = ShortBuffer.allocate(1).put(floatToHalf(scalarVal)).apply { flip() }
                    val sShape = if (tInfo.shape.isEmpty()) longArrayOf(1) else tInfo.shape
                    OnnxTensor.createTensor(env, sBuf, sShape, OnnxJavaType.FLOAT16)
                } else if (inputType == OnnxJavaType.FLOAT) {
                    val fBuf = FloatBuffer.allocate(1).put(scalarVal).apply { flip() }
                    val sShape = if (tInfo.shape.isEmpty()) longArrayOf(1) else tInfo.shape
                    OnnxTensor.createTensor(env, fBuf, sShape)
                } else if (inputType == OnnxJavaType.INT64) {
                    OnnxTensor.createTensor(env, longArrayOf(1))
                } else {
                    val fBuf = FloatBuffer.allocate(1).put(scalarVal).apply { flip() }
                    OnnxTensor.createTensor(env, fBuf, longArrayOf(1))
                }

                createdTensors.add(secondaryTensor)
                inputMap[name] = secondaryTensor
            }

            // Eksekusi ONNX Runtime
            val results = session.run(inputMap)

            // Cari tensor output gambar (biasanya yang memiliki 3 channel di dim 1 atau dim 3)
            var chosenOutput: OnnxTensor? = null
            for (i in 0 until results.size()) {
                val candidate = results.get(i) as? OnnxTensor ?: continue
                val cShape = candidate.info.shape
                if (cShape.size == 4 && (cShape[1] == 3L || cShape[3] == 3L)) {
                    chosenOutput = candidate
                    break
                }
            }

            if (chosenOutput == null && results.size() > 0) {
                chosenOutput = results.get(0) as? OnnxTensor
            }

            val outputTensor = chosenOutput
                ?: return@withContext Result.failure(Exception("Output tensor gambar tidak ditemukan dari model"))

            val outputShape = outputTensor.info.shape
            val isOutputFp16 = outputTensor.info.type == OnnxJavaType.FLOAT16

            // Deteksi format NCHW vs NHWC
            val isNchw = outputShape.size >= 4 && outputShape[1] == 3L
            val outH = if (isNchw) outputShape[2].toInt() else outputShape[1].toInt()
            val outW = if (isNchw) outputShape[3].toInt() else outputShape[2].toInt()
            val outChannels = 3
            val outStride = outW * outH

            val outFloats = FloatArray(outChannels * outStride)

            if (isOutputFp16) {
                val sBuf = outputTensor.shortBuffer
                val count = min(outFloats.size, sBuf.remaining())
                for (i in 0 until count) {
                    outFloats[i] = halfToFloat(sBuf.get())
                }
            } else {
                val fBuf = outputTensor.floatBuffer
                val count = min(outFloats.size, fBuf.remaining())
                fBuf.get(outFloats, 0, count)
            }

            // Deteksi rentang output secara dinamis
            var minVal = Float.MAX_VALUE
            var maxVal = Float.MIN_VALUE
            val sampleStep = max(1, outStride / 500)
            for (i in 0 until outStride step sampleStep) {
                val v = outFloats[i]
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
            }

            val isOutputNegativeOneToOne = minVal < -0.05f

            val outPixels = IntArray(outW * outH)
            var outIdx = 0

            if (isNchw) {
                for (y in 0 until outH) {
                    for (x in 0 until outW) {
                        var rF = outFloats[outIdx]
                        var gF = outFloats[outStride + outIdx]
                        var bF = outFloats[2 * outStride + outIdx]

                        if (isOutputNegativeOneToOne) {
                            rF = (rF * 0.5f + 0.5f)
                            gF = (gF * 0.5f + 0.5f)
                            bF = (bF * 0.5f + 0.5f)
                        } else if (maxVal <= 1.5f) {
                            // Already in [0, 1]
                        } else {
                            // Already in [0, 255]
                            rF /= 255f
                            gF /= 255f
                            bF /= 255f
                        }

                        val rInt = (rF.coerceIn(0f, 1f) * 255.0f).toInt()
                        val gInt = (gF.coerceIn(0f, 1f) * 255.0f).toInt()
                        val bInt = (bF.coerceIn(0f, 1f) * 255.0f).toInt()

                        outPixels[outIdx] = (0xFF shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                        outIdx++
                    }
                }
            } else {
                // NHWC format
                var pixelIdx = 0
                for (i in 0 until outStride) {
                    var rF = outFloats[pixelIdx]
                    var gF = outFloats[pixelIdx + 1]
                    var bF = outFloats[pixelIdx + 2]

                    if (isOutputNegativeOneToOne) {
                        rF = (rF * 0.5f + 0.5f)
                        gF = (gF * 0.5f + 0.5f)
                        bF = (bF * 0.5f + 0.5f)
                    } else if (maxVal <= 1.5f) {
                        // In [0, 1]
                    } else {
                        rF /= 255f
                        gF /= 255f
                        bF /= 255f
                    }

                    val rInt = (rF.coerceIn(0f, 1f) * 255.0f).toInt()
                    val gInt = (gF.coerceIn(0f, 1f) * 255.0f).toInt()
                    val bInt = (bF.coerceIn(0f, 1f) * 255.0f).toInt()

                    outPixels[i] = (0xFF shl 24) or (rInt shl 16) or (gInt shl 8) or bInt
                    pixelIdx += 3
                }
            }

            val resultBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
            resultBitmap.setPixels(outPixels, 0, outW, 0, 0, outW, outH)

            results.close()

            // Jika input tadi di-scale untuk menyesuaikan fixed size (misal CodeFormer),
            // kembalikan ukuran sesuai skala model jika perlu
            val finalBitmap = if (preparedBitmap != inputBitmap && modelType.defaultScale == 1) {
                Bitmap.createScaledBitmap(resultBitmap, inputBitmap.width, inputBitmap.height, true)
            } else {
                resultBitmap
            }

            Result.success(finalBitmap)
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Inference error pada ${modelType.displayName}: ${e.message}", e)
            Result.failure(e)
        } finally {
            createdTensors.forEach {
                try { it.close() } catch (_: Exception) {}
            }
        }
    }

    fun closeSession(modelType: AIModelType) {
        try {
            activeSessions.remove(modelType)?.close()
            DiagnosticLogger.d(TAG, "Sesi model ${modelType.displayName} ditutup.")
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Error saat menutup sesi: ${e.message}")
        }
    }

    fun releaseAll() {
        activeSessions.values.forEach {
            try { it.close() } catch (_: Exception) {}
        }
        activeSessions.clear()
        DiagnosticLogger.d(TAG, "Seluruh sesi ONNX dibersihkan.")
    }
}
