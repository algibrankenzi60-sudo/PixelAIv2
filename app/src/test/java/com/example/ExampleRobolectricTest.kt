package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AI Image Lab", appName)
  }

  @Test
  fun `verify four onnx models defined`() {
    val models = com.example.model.AIModelType.entries
    assertEquals(4, models.size)
    assertEquals("codeformer.onnx", com.example.model.AIModelType.CODEFORMER.expectedFileName)
    assertEquals("real-ESRGAN-v4plus.onnx", com.example.model.AIModelType.REAL_ESRGAN_V4PLUS.expectedFileName)
    assertEquals("GPEN-bfr-256.fp16.onnx", com.example.model.AIModelType.GPEN_BFR_256_FP16.expectedFileName)
    assertEquals("releasr-general-x4v3.onnx", com.example.model.AIModelType.REAL_ESRGAN_COMPACT_X4V3.expectedFileName)
  }

  @Test
  fun `verify floatToHalf and halfToFloat roundtrip`() {
    val testValues = floatArrayOf(0.0f, 1.0f, 0.5f, -0.5f, 0.7f)
    for (v in testValues) {
      val h = com.example.engine.OnnxSessionManager.floatToHalf(v)
      val back = com.example.engine.OnnxSessionManager.halfToFloat(h)
      org.junit.Assert.assertTrue("Difference for $v was too large: $back", kotlin.math.abs(v - back) < 0.01f)
    }
  }

  @Test
  fun `verify diagnostic logger logging`() {
    com.example.util.DiagnosticLogger.clear()
    com.example.util.DiagnosticLogger.i("TestTag", "Testing message")
    val logs = com.example.util.DiagnosticLogger.getLogs()
    assertEquals(1, logs.size)
    assertEquals("TestTag", logs[0].tag)
    assertEquals("Testing message", logs[0].message)
  }
}
