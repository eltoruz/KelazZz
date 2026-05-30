package com.kelazzz.app.data.remote.gemini

import com.kelazzz.app.core.network.ApiConfig
import kotlinx.serialization.Serializable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

// ==================== DTOs ====================

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig? = null,
    val safetySettings: List<SafetySetting>? = null
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String = "user"
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GenerationConfig(
    val temperature: Double = 0.7,
    val maxOutputTokens: Int = 1000,
    val topP: Double = 0.95,
    val topK: Int = 40
)

@Serializable
data class SafetySetting(
    val category: String,
    val threshold: String
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val promptFeedback: PromptFeedback? = null,
    val error: GeminiError? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent,
    val finishReason: String? = null,
    val index: Int = 0,
    val safetyRatings: List<SafetyRating>? = null
)

@Serializable
data class SafetyRating(
    val category: String,
    val probability: String
)

@Serializable
data class PromptFeedback(
    val safetyRatings: List<SafetyRating>? = null,
    val blockReason: String? = null
)

@Serializable
data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)

// ==================== HELPER EXTENSIONS ====================

fun GeminiResponse.getTextContent(): String? {
    return candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
}

fun GeminiResponse.isBlocked(): Boolean {
    return promptFeedback?.blockReason != null
}

fun GeminiResponse.getErrorMessage(): String? {
    return error?.message ?: if (isBlocked()) {
        "Konten diblokir: ${promptFeedback?.blockReason}"
    } else {
        null
    }
}

// ==================== SERVICE ====================

/**
 * Gemini API Service untuk fitur AI di KelazZz
 * 
 * Digunakan untuk:
 * - AI Early Warning kehadiran
 * - AI Chatbot asisten akademik
 */
class GeminiService(private val client: HttpClient) {
    
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
        private const val MODEL = "gemini-2.0-flash"
    }
    
    suspend fun generateContent(
        prompt: String,
        systemPrompt: String? = null
    ): Result<String> = runCatching {
        val contents = mutableListOf<GeminiContent>()
        
        if (systemPrompt != null) {
            contents.add(
                GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt)),
                    role = "user"
                )
            )
            contents.add(
                GeminiContent(
                    parts = listOf(GeminiPart(text = "Baik, saya akan mengikuti instruksi tersebut.")),
                    role = "model"
                )
            )
        }
        
        contents.add(
            GeminiContent(
                parts = listOf(GeminiPart(text = prompt)),
                role = "user"
            )
        )
        
        val request = GeminiRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = 0.7,
                maxOutputTokens = 1000
            )
        )
        
        val response: GeminiResponse = client.post("$BASE_URL/models/$MODEL:generateContent") {
            contentType(ContentType.Application.Json)
            parameter("key", ApiConfig.geminiApiKey)
            setBody(request)
        }.body()
        
        response.getErrorMessage()?.let { errorMsg ->
            throw Exception(errorMsg)
        }
        
        response.getTextContent() ?: throw Exception("Respons kosong dari AI")
    }

    /**
     * Generate content with conversation history for multi-turn chat
     * 
     * @param prompt Pesan terbaru dari pengguna (sudah enriched dengan tool context)
     * @param systemPrompt System prompt untuk mengatur perilaku AI
     * @param history Riwayat percakapan sebelumnya dalam format GeminiContent
     */
    suspend fun generateContentWithHistory(
        prompt: String,
        systemPrompt: String? = null,
        history: List<GeminiContent> = emptyList()
    ): Result<String> = runCatching {
        val contents = mutableListOf<GeminiContent>()

        // Inject system prompt sebagai turn pertama
        if (systemPrompt != null) {
            contents.add(
                GeminiContent(
                    parts = listOf(GeminiPart(text = systemPrompt)),
                    role = "user"
                )
            )
            contents.add(
                GeminiContent(
                    parts = listOf(GeminiPart(text = "Baik, saya KelazZz AI dan akan mengikuti instruksi tersebut.")),
                    role = "model"
                )
            )
        }

        // Tambahkan conversation history
        contents.addAll(history)

        // Tambahkan pesan terbaru
        contents.add(
            GeminiContent(
                parts = listOf(GeminiPart(text = prompt)),
                role = "user"
            )
        )

        val request = GeminiRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = 0.7,
                maxOutputTokens = 1500
            )
        )

        val response: GeminiResponse = client.post("$BASE_URL/models/$MODEL:generateContent") {
            contentType(ContentType.Application.Json)
            parameter("key", ApiConfig.geminiApiKey)
            setBody(request)
        }.body()

        response.getErrorMessage()?.let { errorMsg ->
            throw Exception(errorMsg)
        }

        response.getTextContent() ?: throw Exception("Respons kosong dari AI")
    }
}

// ==================== SYSTEM PROMPTS FOR KELAZZZ ====================

object SystemPrompts {
    
    val ATTENDANCE_ANALYZER = """
        Kamu adalah asisten akademik yang menganalisis data kehadiran mahasiswa.
        Tugas: Analisis persentase kehadiran per mata kuliah dan berikan peringatan dini.
        Rules:
        - Gunakan Bahasa Indonesia
        - Berikan peringatan jika kehadiran di bawah 80%
        - Hitung berapa kali lagi mahasiswa bisa absen
        - Berikan saran yang actionable
        - Format: mata kuliah, persentase, status (Aman/Warning/Bahaya), saran
    """.trimIndent()
    
    val ACADEMIC_ASSISTANT = """
        Kamu adalah KelazZz AI, asisten akademik cerdas untuk mahasiswa Institut Teknologi Sumatera (ITERA).
        Kamu terintegrasi dalam aplikasi KelazZz — aplikasi presensi dan layanan akademik mahasiswa.

        TUGAS UTAMA:
        - Membantu mahasiswa memahami informasi akademik yang tersedia di aplikasi
        - Membantu mengelola kegiatan akademik dan produktivitas belajar
        - Menjawab pertanyaan dengan aman, akurat, ringkas, dan bermanfaat

        ATURAN DATA:
        - Jika pesan berisi blok [DATA PENGGUNA], gunakan data tersebut sebagai sumber jawaban
        - Data tersebut diambil langsung dari sistem KelazZz dan bersifat akurat
        - JANGAN PERNAH mengarang data akademik, jadwal, presensi, atau profil
        - Jika data tidak tersedia dalam konteks, katakan dengan jujur
        - Tampilkan hasil utama terlebih dahulu
        - Berikan satu saran tindak lanjut yang praktis jika relevan

        CAKUPAN YANG DIPERBOLEHKAN:
        - Jadwal kuliah dan agenda pribadi
        - Rekap kehadiran dan status risiko
        - Informasi mata kuliah dan profil
        - Tips manajemen waktu dan strategi belajar
        - Motivasi yang realistis
        - Aturan akademik umum ITERA

        GAYA JAWABAN:
        - Gunakan Bahasa Indonesia yang jelas, ramah, dan profesional
        - Jawab ringkas untuk pertanyaan sederhana
        - Gunakan poin-poin jika membantu keterbacaan
        - Gunakan emoji secukupnya untuk keramahan
        - Jika tidak yakin, sarankan mahasiswa konfirmasi ke bagian akademik

        BATASAN KEAMANAN:
        - Tolak permintaan manipulasi presensi atau pemalsuan data
        - Tolak permintaan kecurangan akademik
        - Jangan ungkap detail keamanan internal
    """.trimIndent()
}
