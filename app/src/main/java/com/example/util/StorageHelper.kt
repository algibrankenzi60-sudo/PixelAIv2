package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StorageHelper {
    private const val TAG = "StorageHelper"

    /**
     * Menyimpan Bitmap ke galeri dengan multi-layer fallback agar tidak pernah gagal
     * dengan error "no such file directory" di Android versi apapun (Android 9 hingga Android 14+).
     */
    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        isPng: Boolean = false
    ): Result<Uri> = withContext(Dispatchers.IO) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val extension = if (isPng) "png" else "jpg"
        val mimeType = if (isPng) "image/png" else "image/jpeg"
        val filename = "AI_IMG_${timestamp}.$extension"
        val compressFormat = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

        DiagnosticLogger.i(TAG, "Mencoba menyimpan gambar: $filename (${bitmap.width}x${bitmap.height})")

        // 1. Metode MediaStore Resolver (Direkomendasikan untuk Android 10+ / Q ke atas)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AI_Image_Lab")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (imageUri != null) {
                    val stream: OutputStream? = resolver.openOutputStream(imageUri)
                    if (stream != null) {
                        stream.use { out ->
                            bitmap.compress(compressFormat, 100, out)
                        }
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(imageUri, contentValues, null, null)

                        DiagnosticLogger.success(TAG, "Berhasil disimpan via MediaStore Q+: $imageUri")
                        return@withContext Result.success(imageUri)
                    }
                }
            } catch (e: Exception) {
                DiagnosticLogger.w(TAG, "MediaStore Q+ gagal, beralih ke direct storage fallback", e)
            }
        }

        // 2. Direct Storage Fallback (Cocok untuk Android 9 Vivo X21A dan perangkat lainnya)
        val directoryCandidates = mutableListOf<File>()

        try {
            // Priority A: Public Pictures directory
            val publicPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            if (publicPictures != null) {
                directoryCandidates.add(File(publicPictures, "AI_Image_Lab"))
                directoryCandidates.add(publicPictures)
            }
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Gagal mengakses getExternalStoragePublicDirectory(PICTURES)", e)
        }

        try {
            // Priority B: Public DCIM directory
            val publicDcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            if (publicDcim != null) {
                directoryCandidates.add(File(publicDcim, "AI_Image_Lab"))
            }
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Gagal mengakses getExternalStoragePublicDirectory(DCIM)", e)
        }

        try {
            // Priority C: App-specific external pictures dir (SELALU writable tanpa runtime permission!)
            val appExternal = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            if (appExternal != null) {
                directoryCandidates.add(appExternal)
            }
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Gagal mengakses getExternalFilesDir", e)
        }

        try {
            // Priority D: Internal files dir
            directoryCandidates.add(File(context.filesDir, "Pictures"))
        } catch (e: Exception) {
            DiagnosticLogger.w(TAG, "Gagal mengakses internal filesDir", e)
        }

        var lastException: Exception? = null

        for (targetDir in directoryCandidates) {
            try {
                if (!targetDir.exists()) {
                    val created = targetDir.mkdirs()
                    DiagnosticLogger.d(TAG, "Membuat folder ${targetDir.absolutePath}: $created")
                }

                if (targetDir.exists() && targetDir.canWrite()) {
                    val file = File(targetDir, filename)
                    FileOutputStream(file).use { out ->
                        bitmap.compress(compressFormat, 100, out)
                        out.flush()
                    }

                    DiagnosticLogger.success(TAG, "File gambar tersimpan di: ${file.absolutePath}")

                    // Memicu MediaScanner agar langsung muncul di Galeri & Google Photos
                    try {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(file.absolutePath),
                            arrayOf(mimeType)
                        ) { path, scannedUri ->
                            DiagnosticLogger.i(TAG, "MediaScanner selesai untuk $path -> $scannedUri")
                        }
                    } catch (scanEx: Exception) {
                        DiagnosticLogger.w(TAG, "MediaScanner peringatan: ${scanEx.message}")
                    }

                    // Juga coba daftarkan entri di MediaStore ContentResolver jika diizinkan
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DATA, file.absolutePath)
                            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        }
                        val insertedUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        return@withContext Result.success(insertedUri ?: Uri.fromFile(file))
                    } catch (dbEx: Exception) {
                        return@withContext Result.success(Uri.fromFile(file))
                    }
                }
            } catch (ex: Exception) {
                lastException = ex
                DiagnosticLogger.w(TAG, "Gagal menyimpan di ${targetDir.absolutePath}: ${ex.message}")
            }
        }

        val err = lastException ?: Exception("Tidak dapat menemukan direktori penyimpanan yang dapat ditulisi")
        DiagnosticLogger.e(TAG, "Gagal total menyimpan gambar", err)
        Result.failure(err)
    }

    suspend fun shareBitmap(context: Context, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            val cachePath = File(context.cacheDir, "images")
            if (!cachePath.exists()) cachePath.mkdirs()
            val file = File(cachePath, "shared_enhanced_image.jpg")
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, "Ditingkatkan dengan AI Image Lab Offline (ONNX Runtime)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan Foto Hasil AI").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Gagal membagikan gambar", e)
        }
    }

    /**
     * Memuat Bitmap dari URI tanpa batasan resolusi buatan.
     * Hanya melakukan subsampling jika dimensi melampaui batas batas grafis Android (8192px)
     * agar terhindar dari batas maksimal tekstur hardware GPU perangkat.
     */
    suspend fun loadBitmapFromUri(context: Context, uri: Uri, maxSafeDimension: Int = 8192): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // First decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > maxSafeDimension || options.outWidth > maxSafeDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= maxSafeDimension && (halfWidth / inSampleSize) >= maxSafeDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }

            val loadedBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }

            if (loadedBitmap != null) {
                DiagnosticLogger.i(TAG, "Foto berhasil dimuat dari galeri: ${loadedBitmap.width}x${loadedBitmap.height} px")
            } else {
                DiagnosticLogger.e(TAG, "Gagal memecahkan (decode) stream foto dari URI: $uri")
            }
            loadedBitmap
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Gagal membaca foto dari URI: $uri", e)
            null
        }
    }

    fun createSamplePortraitBitmap(): Bitmap {
        val width = 480
        val height = 480
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint().apply { color = Color.rgb(45, 40, 58) }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val skinPaint = Paint().apply {
            color = Color.rgb(220, 180, 160)
            isAntiAlias = true
        }
        canvas.drawOval(RectF(140f, 100f, 340f, 360f), skinPaint)

        val hairPaint = Paint().apply {
            color = Color.rgb(55, 40, 35)
            isAntiAlias = true
        }
        canvas.drawArc(RectF(130f, 80f, 350f, 260f), 180f, 180f, true, hairPaint)

        val eyePaint = Paint().apply { color = Color.rgb(60, 45, 40); isAntiAlias = true }
        canvas.drawCircle(200f, 210f, 16f, eyePaint)
        canvas.drawCircle(280f, 210f, 16f, eyePaint)

        val hlPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
        canvas.drawCircle(196f, 206f, 4f, hlPaint)
        canvas.drawCircle(276f, 206f, 4f, hlPaint)

        val lipPaint = Paint().apply { color = Color.rgb(200, 110, 115); isAntiAlias = true }
        canvas.drawOval(RectF(210f, 280f, 270f, 305f), lipPaint)

        val neckPaint = Paint().apply { color = Color.rgb(195, 155, 140); isAntiAlias = true }
        canvas.drawRect(190f, 340f, 290f, 420f, neckPaint)

        val shirtPaint = Paint().apply { color = Color.rgb(80, 110, 140); isAntiAlias = true }
        canvas.drawOval(RectF(100f, 390f, 380f, 560f), shirtPaint)

        val noisePaint = Paint().apply { color = Color.argb(40, 255, 255, 255) }
        val random = java.util.Random(42)
        for (i in 0..1200) {
            val nx = random.nextFloat() * width
            val ny = random.nextFloat() * height
            canvas.drawPoint(nx, ny, noisePaint)
        }

        return bitmap
    }

    fun createSampleSceneryBitmap(): Bitmap {
        val width = 500
        val height = 375
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val skyPaint = Paint().apply { color = Color.rgb(80, 130, 190) }
        canvas.drawRect(0f, 0f, width.toFloat(), 220f, skyPaint)

        val mountainPaint = Paint().apply {
            color = Color.rgb(60, 75, 95)
            isAntiAlias = true
        }
        val path = android.graphics.Path().apply {
            moveTo(0f, 220f)
            lineTo(120f, 110f)
            lineTo(240f, 220f)
            lineTo(360f, 90f)
            lineTo(500f, 220f)
            close()
        }
        canvas.drawPath(path, mountainPaint)

        val hillPaint = Paint().apply {
            color = Color.rgb(45, 95, 55)
            isAntiAlias = true
        }
        canvas.drawOval(RectF(-50f, 190f, 350f, 380f), hillPaint)
        canvas.drawOval(RectF(200f, 210f, 550f, 390f), hillPaint)

        val lakePaint = Paint().apply { color = Color.rgb(40, 85, 125) }
        canvas.drawRect(0f, 270f, width.toFloat(), height.toFloat(), lakePaint)

        return bitmap
    }
}
