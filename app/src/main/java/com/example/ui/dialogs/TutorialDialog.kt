package com.example.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.AIModelType

@Composable
fun TutorialDialog(
    onDismiss: () -> Unit,
    onOpenModelManager: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .testTag("tutorial_dialog"),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Banner Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_ai_banner),
                        contentDescription = "AI Super Resolution Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(Color(0x88141218))
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Panduan Fitur & Model AI",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "AI Image Lab Offline • Vivo X21A Snapdragon 660",
                            color = MaterialTheme.colorScheme.tertiary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Section 1: Explanation of user's 4 files
                    Text(
                        text = "1. FUNGSI 4 FILE ONNX YANG ANDA MILIKI",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    ModelExplanationCard(
                        fileName = "codeformer.onnx",
                        title = "CodeFormer",
                        subtitle = "Restorasi Wajah Ultra-Detail (S-Lab NTU)",
                        explanation = "Model spesialis yang merekonstruksi detail mata, alis, dan tekstur kulit pada foto wajah yang buram, blur, atau terkompresi menggunakan Codebook Lookup Priors.",
                        badgeColor = MaterialTheme.colorScheme.primary
                    )

                    ModelExplanationCard(
                        fileName = "real-ESRGAN-v4plus.onnx",
                        title = "Real-ESRGAN v4+",
                        subtitle = "Super-Resolution 4x untuk Foto Umum",
                        explanation = "Meningkatkan resolusi foto hingga 4x lipat (misal 500px jadi 2000px). Menajamkan pemandangan alam, arsitektur gedung, pakaian, dan tulisan secara alami.",
                        badgeColor = MaterialTheme.colorScheme.tertiary
                    )

                    ModelExplanationCard(
                        fileName = "GPEN-bfr-256.fp16.onnx",
                        title = "GPEN BFR-256 (FP16)",
                        subtitle = "Restorasi Wajah Cepat & Ringan",
                        explanation = "Menggunakan Generative Facial Prior dalam format FP16 (Half Precision). Sangat hemat RAM 6GB dan ringan bagi Snapdragon 660, cocok untuk perbaikan cepat.",
                        badgeColor = Color(0xFF81C784)
                    )

                    ModelExplanationCard(
                        fileName = "releasr-general-x4v3.onnx",
                        title = "RealESRGAN Compact x4v3",
                        subtitle = "Upscaling 4x Ringkas & Gesit",
                        explanation = "Model compact berarsitektur ringkas dengan parameter minimal. Mempercepat proses pembesaran 4x hingga 3 kali lebih kencang tanpa membuat HP panas.",
                        badgeColor = Color(0xFFFFB74D)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Section 2: How to load files
                    Text(
                        text = "2. CARA MENGHUBUNGKAN FILE .ONNX ANDA",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    GuideStepRow(
                        number = "1",
                        icon = Icons.Default.FolderOpen,
                        title = "Buka Menu 'Model AI (.onnx)'",
                        description = "Ketuk tombol 'Model AI' di bagian atas layar atau tombol di bawah."
                    )

                    GuideStepRow(
                        number = "2",
                        icon = Icons.Default.AutoAwesome,
                        title = "Pilih File dari Penyimpanan",
                        description = "Tekan 'Pilih File .onnx' pada model yang sesuai dan cari file yang sudah Anda download di folder Downloads."
                    )

                    GuideStepRow(
                        number = "3",
                        icon = Icons.Default.CheckCircle,
                        title = "Langsung Siap Digunakan",
                        description = "ONNX Runtime akan memuat model secara lokal. Jika belum dimuat, aplikasi tetap menyediakan Native AI Engine berkualitas tinggi!"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Section 3: Optimization for SD660 / Vivo X21A
                    Text(
                        text = "3. OPTIMALISASI SNAPDRAGON 660 & RAM 6GB",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Vivo X21A (Kryo 260 4-Core + Tiling Mode)",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "• 4 Core Performa: Thread ONNX disetel otomatis ke 4 core Kryo Gold agar tidak panas.\n• Mode Tiling (Potongan 256px): Menjaga memori RAM 6GB tetap aman dan anti crash saat memproses foto beresolusi tinggi.\n• 100% Offline: Tidak memerlukan koneksi internet, privasi data foto Anda sepenuhnya aman di perangkat.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onDismiss()
                            onOpenModelManager()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tutorial_btn_open_models")
                    ) {
                        Text(text = "Kelola Model", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tutorial_btn_dismiss")
                    ) {
                        Text(text = "Mulai Pakai", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelExplanationCard(
    fileName: String,
    title: String,
    subtitle: String,
    explanation: String,
    badgeColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = fileName,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun GuideStepRow(
    number: String,
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
