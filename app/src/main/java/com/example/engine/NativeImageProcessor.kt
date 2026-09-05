package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object NativeImageProcessor {

    suspend fun enhance(
        bitmap: Bitmap,
        scaleFactor: Int = 1,
        sharpnessStrength: Float = 0.65f,
        denoiseStrength: Float = 0.40f,
        isFaceMode: Boolean = false
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = bitmap.width
        val height = bitmap.height

        val scaled = if (scaleFactor > 1) {
            val targetW = width * scaleFactor
            val targetH = height * scaleFactor
            val scaledBmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(scaledBmp)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
            val matrix = Matrix()
            matrix.postScale(scaleFactor.toFloat(), scaleFactor.toFloat())
            canvas.drawBitmap(bitmap, matrix, paint)
            scaledBmp
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val outputPixels = IntArray(w * h)

        // Contrast Adaptive Sharpening (CAS) + Edge Detail Synthesis
        val sharpWeight = sharpnessStrength.coerceIn(0.1f, 1.2f)
        val denoiseWeight = denoiseStrength.coerceIn(0.0f, 0.8f)

        // Process pixels with unsharp masking & edge aware enhancement
        val stride = w
        for (y in 0 until h) {
            val yPrev = max(0, y - 1)
            val yNext = min(h - 1, y + 1)

            for (x in 0 until w) {
                val xPrev = max(0, x - 1)
                val xNext = min(w - 1, x + 1)

                val center = pixels[y * stride + x]
                val top = pixels[yPrev * stride + x]
                val bottom = pixels[yNext * stride + x]
                val left = pixels[y * stride + xPrev]
                val right = pixels[y * stride + xNext]

                val a = (center shr 24) and 0xFF

                // Channel by channel CAS sharpening
                val rC = (center shr 16) and 0xFF
                val gC = (center shr 8) and 0xFF
                val bC = center and 0xFF

                val rT = (top shr 16) and 0xFF
                val rB = (bottom shr 16) and 0xFF
                val rL = (left shr 16) and 0xFF
                val rR = (right shr 16) and 0xFF

                val gT = (top shr 8) and 0xFF
                val gB = (bottom shr 8) and 0xFF
                val gL = (left shr 8) and 0xFF
                val gR = (right shr 8) and 0xFF

                val bT = top and 0xFF
                val bB = bottom and 0xFF
                val bL = left and 0xFF
                val bR = right and 0xFF

                // Fast CAS kernel
                val rNeighborAvg = (rT + rB + rL + rR) * 0.25f
                val gNeighborAvg = (gT + gB + gL + gR) * 0.25f
                val bNeighborAvg = (bT + bB + bL + bR) * 0.25f

                val rEdge = rC - rNeighborAvg
                val gEdge = gC - gNeighborAvg
                val bEdge = bC - bNeighborAvg

                // Denoise threshold (small edges treated as noise, large edges sharpened)
                val rEdgeFiltered = if (abs(rEdge) < 8f * denoiseWeight) {
                    rEdge * (1f - denoiseWeight)
                } else {
                    rEdge * (1f + sharpWeight * 0.9f)
                }

                val gEdgeFiltered = if (abs(gEdge) < 8f * denoiseWeight) {
                    gEdge * (1f - denoiseWeight)
                } else {
                    gEdge * (1f + sharpWeight * 0.9f)
                }

                val bEdgeFiltered = if (abs(bEdge) < 8f * denoiseWeight) {
                    bEdge * (1f - denoiseWeight)
                } else {
                    bEdge * (1f + sharpWeight * 0.9f)
                }

                var rOut = (rNeighborAvg + rEdgeFiltered).roundToInt()
                var gOut = (gNeighborAvg + gEdgeFiltered).roundToInt()
                var bOut = (bNeighborAvg + bEdgeFiltered).roundToInt()

                if (isFaceMode) {
                    // Slight skin vibrancy and contrast curve
                    rOut = (rOut * 1.03f).toInt()
                    gOut = (gOut * 1.01f).toInt()
                }

                rOut = rOut.coerceIn(0, 255)
                gOut = gOut.coerceIn(0, 255)
                bOut = bOut.coerceIn(0, 255)

                outputPixels[y * stride + x] = (a shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(outputPixels, 0, w, 0, 0, w, h)
        result
    }
}
