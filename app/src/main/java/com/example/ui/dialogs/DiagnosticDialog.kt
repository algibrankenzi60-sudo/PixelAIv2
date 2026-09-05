package com.example.ui.dialogs

import android.app.ActivityManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.DiagnosticTestResult
import com.example.engine.ModelDiagnosticInfo
import com.example.engine.OnnxSessionManager
import com.example.model.AIModelType
import com.example.util.DiagnosticLogEntry
import com.example.util.DiagnosticLogger
import com.example.util.LogLevel
import kotlinx.coroutines.launch

@Composable
fun DiagnosticDialog(
    onnxSessionManager: OnnxSessionManager,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(1) } // Default to Tab 1 (Model Inspection)
    var selectedModel by remember { mutableStateOf(AIModelType.REAL_ESRGAN_COMPACT_X4V3) }
    var modelDiagInfo by remember { mutableStateOf<ModelDiagnosticInfo?>(null) }
    var isTestingModel by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<DiagnosticTestResult?>(null) }
    var logFilter by remember { mutableStateOf<LogLevel?>(null) }
    var logEntries by remember { mutableStateOf(DiagnosticLogger.getLogs()) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    // Listen for new log updates
    LaunchedEffect(Unit) {
        DiagnosticLogger.setListener {
            logEntries = DiagnosticLogger.getLogs()
        }
    }

    // Refresh model inspection when selected model changes
    LaunchedEffect(selectedModel) {
        testResult = null
        modelDiagInfo = onnxSessionManager.inspectModel(selectedModel)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .testTag("diagnostic_dialog"),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Diagnostik & Pemeriksaan Error",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Inspeksi sesi ONNX, spesifikasi tensor & hardware",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_diagnostic")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Hardware & RAM", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Inspeksi Model ONNX", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Log Kesalahan", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                val errCount = logEntries.count { it.level == LogLevel.ERROR }
                                if (errCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.error,
                                        shape = CircleShape
                                    ) {
                                        Text(
                                            text = "$errCount",
                                            color = MaterialTheme.colorScheme.onError,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> HardwareTab(context = context)
                        1 -> ModelInspectionTab(
                            selectedModel = selectedModel,
                            onSelectModel = { selectedModel = it },
                            diagInfo = modelDiagInfo,
                            isTesting = isTestingModel,
                            testResult = testResult,
                            onRunTest = {
                                coroutineScope.launch {
                                    isTestingModel = true
                                    testResult = onnxSessionManager.runDiagnosticTest(selectedModel)
                                    isTestingModel = false
                                }
                            }
                        )
                        2 -> LogTab(
                            logs = logEntries,
                            currentFilter = logFilter,
                            onFilterChange = { logFilter = it },
                            onClearLogs = { DiagnosticLogger.clear() },
                            onCopyLogs = {
                                val text = DiagnosticLogger.getAllLogsFormatted()
                                clipboardManager.setText(AnnotatedString(text))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareTab(context: Context) {
    val actManager = remember { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
    val memInfo = remember {
        ActivityManager.MemoryInfo().also { actManager?.getMemoryInfo(it) }
    }
    val runtime = Runtime.getRuntime()
    val maxHeapMb = runtime.maxMemory() / (1024 * 1024)
    val totalHeapMb = runtime.totalMemory() / (1024 * 1024)
    val freeHeapMb = runtime.freeMemory() / (1024 * 1024)
    val usedHeapMb = totalHeapMb - freeHeapMb

    val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    val availRamGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Spesifikasi Hardware Vivo X21A", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Chipset: Qualcomm Snapdragon 660 SDM660 (14nm FinFET)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("• CPU: Octa-core (4x2.2 GHz Kryo 260 Gold + 4x1.8 GHz Kryo 260 Silver)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("• GPU: Qualcomm Adreno 512", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("• OS: Android 9 (Pie) • API 28", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("• Mode: 100% Offline (ONNX Runtime CPU OpenMP Engine)", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Status Memori RAM & Heap", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• RAM Fisik: ${String.format("%.2f", availRamGb)} GB tersedia / ${String.format("%.2f", totalRamGb)} GB total",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "• JVM Heap (largeHeap active): ${usedHeapMb} MB terpakai / ${maxHeapMb} MB batas maksimal",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "• Low Memory Warning: ${if (memInfo.lowMemory) "YA (Perangkat Menipis)" else "TIDAK (Aman)"}",
                        color = if (memInfo.lowMemory) MaterialTheme.colorScheme.error else Color(0xFF81C784),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Pencegahan Out-of-Memory (Tiling Engine)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Sistem Tiling memecah foto resolusi tinggi menjadi blok 256x256 atau 512x512 dengan overlap 24px. Hal ini menjamin foto sebesar apapun tidak akan kehabisan RAM atau force close di Android 9.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelInspectionTab(
    selectedModel: AIModelType,
    onSelectModel: (AIModelType) -> Unit,
    diagInfo: ModelDiagnosticInfo?,
    isTesting: Boolean,
    testResult: DiagnosticTestResult?,
    onRunTest: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Model Selection Chips
        item {
            Text("Pilih Model untuk Diperiksa:", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AIModelType.entries.forEach { model ->
                    FilterChip(
                        selected = selectedModel == model,
                        onClick = { onSelectModel(model) },
                        label = {
                            Text(
                                text = when (model) {
                                    AIModelType.CODEFORMER -> "CodeFormer"
                                    AIModelType.REAL_ESRGAN_V4PLUS -> "v4+"
                                    AIModelType.GPEN_BFR_256_FP16 -> "GPEN FP16"
                                    AIModelType.REAL_ESRGAN_COMPACT_X4V3 -> "Compact x4"
                                },
                                fontSize = 11.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }

        // Status Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedModel.displayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val isLoaded = diagInfo?.isFilePresent == true
                        Surface(
                            color = if (isLoaded) Color(0xFF81C784).copy(alpha = 0.15f) else Color(0xFFFFB74D).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isLoaded) Color(0xFF81C784).copy(alpha = 0.4f) else Color(0xFFFFB74D).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (isLoaded) "TERHUBUNG (${String.format("%.1f", (diagInfo?.fileSizeBytes ?: 0L) / (1024.0 * 1024.0))} MB)" else "BELUM DIIMPOR",
                                color = if (isLoaded) Color(0xFF81C784) else Color(0xFFFFB74D),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("File: ${selectedModel.expectedFileName}", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp)
                    Text("Target Skala: ${selectedModel.defaultScale}x", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)

                    if (diagInfo?.errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Catatan: ${diagInfo.errorMessage}",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Tensor Nodes Breakdown (if model is loaded)
        if (diagInfo?.isFilePresent == true && diagInfo.inputs.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Spesifikasi Node Tensor ONNX", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text("Input Nodes (${diagInfo.inputs.size}):", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        diagInfo.inputs.forEach { node ->
                            Text(
                                text = "• Nama: '${node.name}' | Tipe: ${node.type} | Shape: ${node.shape}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Output Nodes (${diagInfo.outputs.size}):", color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        diagInfo.outputs.forEach { node ->
                            Text(
                                text = "• Nama: '${node.name}' | Tipe: ${node.type} | Shape: ${node.shape}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Penjelasan spesifik perbaikan
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = when (selectedModel) {
                                    AIModelType.CODEFORMER -> "✓ Adaptasi CodeFormer: Membutuhkan 2 input (gambar 512x512 + tensor bobot 'w' 0.7f). Input sekunder otomatis disediakan tanpa error."
                                    AIModelType.GPEN_BFR_256_FP16 -> "✓ Adaptasi GPEN: Berjalan dalam mode Float16 (Half-Precision) asli, mengonversi buffer secara otomatis tanpa type mismatch."
                                    AIModelType.REAL_ESRGAN_V4PLUS -> "✓ Adaptasi Real-ESRGAN v4+: Memproses tile dengan padding batas tepi agar konvolusi RRDBNet tidak crash pada potongan kecil."
                                    AIModelType.REAL_ESRGAN_COMPACT_X4V3 -> "✓ Adaptasi Compact x4: Format CNN dinamis terbukti berjalan cepat di Snapdragon 660."
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Test Model Action
        item {
            Button(
                onClick = onRunTest,
                enabled = diagInfo?.isFilePresent == true && !isTesting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_run_model_test")
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sedang Menjalankan Uji Coba...", fontSize = 13.sp)
                } else {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uji Coba Inference Model (Live Test)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Test Result Card
        if (testResult != null) {
            item {
                val res = testResult!!
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (res.isSuccess) Color(0xFF1E3324) else Color(0xFF3B1E22)
                    ),
                    border = BorderStroke(1.dp, if (res.isSuccess) Color(0xFF81C784) else MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (res.isSuccess) Color(0xFF81C784) else MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (res.isSuccess) "UJI COBA BERHASIL (${res.executionDurationMs}ms)" else "UJI COBA GAGAL",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        if (res.outputDimensions.isNotEmpty()) {
                            Text("Dimensi Output: ${res.outputDimensions}", color = Color(0xFFD6CEEE), fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        res.logs.forEach { log ->
                            Text(
                                text = log,
                                color = Color(0xFFE2DCF0),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        if (!res.isSuccess && res.error != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Penyebab Error:", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(
                                text = res.error,
                                color = Color(0xFFFFB4AB),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 6
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogTab(
    logs: List<DiagnosticLogEntry>,
    currentFilter: LogLevel?,
    onFilterChange: (LogLevel?) -> Unit,
    onClearLogs: () -> Unit,
    onCopyLogs: () -> Unit
) {
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, currentFilter) {
        if (currentFilter == null) logs else logs.filter { it.level == currentFilter }
    }

    LaunchedEffect(logs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Controls (Filter + Copy + Clear)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = currentFilter == null,
                    onClick = { onFilterChange(null) },
                    label = { Text("Semua", fontSize = 10.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = currentFilter == LogLevel.ERROR,
                    onClick = { onFilterChange(if (currentFilter == LogLevel.ERROR) null else LogLevel.ERROR) },
                    label = { Text("Error", fontSize = 10.sp) },
                    modifier = Modifier.height(28.dp)
                )
                FilterChip(
                    selected = currentFilter == LogLevel.SUCCESS,
                    onClick = { onFilterChange(if (currentFilter == LogLevel.SUCCESS) null else LogLevel.SUCCESS) },
                    label = { Text("Sukses", fontSize = 10.sp) },
                    modifier = Modifier.height(28.dp)
                )
            }

            Row {
                IconButton(onClick = onCopyLogs, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Salin Log", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onClearLogs, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Bersihkan Log", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Log Console
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Belum ada log tercatat", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLogs) { entry ->
                        val color = when (entry.level) {
                            LogLevel.ERROR -> Color(0xFFFF8A80)
                            LogLevel.WARNING -> Color(0xFFFFD180)
                            LogLevel.SUCCESS -> Color(0xFFB9F6CA)
                            LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                            LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Column {
                            Text(
                                text = "[${entry.formattedTime}] [${entry.tag}] ${entry.message}",
                                color = color,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 14.sp
                            )
                            if (entry.details != null) {
                                Text(
                                    text = entry.details,
                                    color = Color(0xFFFF8A80).copy(alpha = 0.8f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 4
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
