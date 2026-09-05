package com.example.model

enum class ModelCategory {
    FACE_RESTORATION,
    SUPER_RESOLUTION,
    COMPACT_FAST
}

enum class AIModelType(
    val id: String,
    val displayName: String,
    val expectedFileName: String,
    val category: ModelCategory,
    val defaultScale: Int,
    val targetInputSize: Int, // e.g. 512 for codeformer, 256 for GPEN
    val tagline: String,
    val description: String,
    val technicalDetails: String
) {
    CODEFORMER(
        id = "codeformer",
        displayName = "CodeFormer",
        expectedFileName = "codeformer.onnx",
        category = ModelCategory.FACE_RESTORATION,
        defaultScale = 1,
        targetInputSize = 512,
        tagline = "Restorasi Wajah Ultra-Detail (S-Lab NTU)",
        description = "Algoritma restorasi wajah berbasis Codebook Lookup Priors. Sangat ampuh memulihkan detail mata, iris, bibir, dan tekstur kulit dari foto buram, rusak, atau terkompresi berat.",
        technicalDetails = "Input: 512x512 RGB Float32 [-1, 1]. Mengubah representasi wajah diskrit menjadi resolusi tinggi."
    ),

    REAL_ESRGAN_V4PLUS(
        id = "realesrgan_v4plus",
        displayName = "Real-ESRGAN v4+",
        expectedFileName = "real-ESRGAN-v4plus.onnx",
        category = ModelCategory.SUPER_RESOLUTION,
        defaultScale = 4,
        targetInputSize = 256,
        tagline = "Super-Resolution 4x Generasi ke-4",
        description = "Model upscaler 4x terbaik untuk fotografi umum, pemandangan, tanaman, bangunan, dan teks. Mengembalikan ketajaman mikro tanpa noise artefak.",
        technicalDetails = "Input: NCHW RGB Float32 [0, 1] -> Output 4x resolusi asli. Menggunakan synthetic degradation training."
    ),

    GPEN_BFR_256_FP16(
        id = "gpen_bfr_256",
        displayName = "GPEN BFR-256 (FP16)",
        expectedFileName = "GPEN-bfr-256.fp16.onnx",
        category = ModelCategory.FACE_RESTORATION,
        defaultScale = 1,
        targetInputSize = 256,
        tagline = "Restorasi Wajah Cepat FP16 (Hemat RAM)",
        description = "Generative Prior for Face Restoration dalam bobot FP16 (Half Precision). Sangat ringan dan dirancang khusus untuk chip Snapdragon 660 & Adreno 512 agar berjalan cepat tanpa panas.",
        technicalDetails = "Input: 256x256 RGB FP16/Float32 [-1, 1]. Ringan, cepat, hemat memori RAM Vivo X21A."
    ),

    REAL_ESRGAN_COMPACT_X4V3(
        id = "realesrgan_compact_x4v3",
        displayName = "RealESRGAN Compact x4v3",
        expectedFileName = "releasr-general-x4v3.onnx",
        category = ModelCategory.COMPACT_FAST,
        defaultScale = 4,
        targetInputSize = 256,
        tagline = "Upscaling 4x Ringkas & Cepat",
        description = "Varian SRVGG compact dari Real-ESRGAN. Memiliki parameter jauh lebih sedikit dibanding v4+, menghasilkan upscaling 4x berkecepatan 3-4 kali lebih kencang di smartphone.",
        technicalDetails = "Input: NCHW RGB Float32 [0, 1]. Model ringan khusus komputasi hemat daya pada perangkat mobile."
    );

    companion object {
        fun fromId(id: String): AIModelType {
            return entries.firstOrNull { it.id == id } ?: REAL_ESRGAN_V4PLUS
        }

        fun findByFileName(fileName: String): AIModelType? {
            val clean = fileName.lowercase().replace(";", "").trim()
            return entries.firstOrNull { model ->
                val expected = model.expectedFileName.lowercase()
                clean.contains(expected) ||
                (model == REAL_ESRGAN_COMPACT_X4V3 && (clean.contains("releasr") || clean.contains("compact") || clean.contains("x4v3"))) ||
                (model == GPEN_BFR_256_FP16 && (clean.contains("gpen") || clean.contains("bfr"))) ||
                (model == CODEFORMER && clean.contains("codeformer")) ||
                (model == REAL_ESRGAN_V4PLUS && (clean.contains("esrgan") && clean.contains("v4plus")))
            }
        }
    }
}
