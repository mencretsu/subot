package com.example.ngontol

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object GeminiApi {

    private const val TAG = "GeminiApi"

    // Gemini expects parts to be objects, not plain strings
    data class TextPart(
        @Json(name = "text") val text: String
    )

    data class Content(
        @Json(name = "role") val role: String = "user",
        @Json(name = "parts") val parts: List<TextPart>
    )

    data class RequestPayload(
        @Json(name = "contents") val contents: List<Content>
    )

    data class ResponsePart(
        @Json(name = "text") val text: String
    )

    data class ResponseContent(
        @Json(name = "parts") val parts: List<ResponsePart>?
    )

    data class Candidate(
        @Json(name = "content") val content: ResponseContent?
    )

    data class GeminiResponse(
        @Json(name = "candidates") val candidates: List<Candidate>?
    )

    private val client = OkHttpClient()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // Tambahkan ini
        .build()
    private val requestAdapter = moshi.adapter(RequestPayload::class.java)
    private val responseAdapter = moshi.adapter(GeminiResponse::class.java)

    fun generateReply(inputText: String, persona: Persona): String? {
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${persona.apiKey}"

        val systemPrompt = """
            Kamu adalah ${persona.botName}, cewek Gen-Z (20-25 th) dari ${persona.address}.
            Kepribadian: ceria, ekstrovert, centil—tapi tetap sopan. Hobi: ${persona.hobby}.
            Sedang kerja di: ${persona.gender}.
            Gaya bahasa:
            • Singkat (4-6 kata), informal, gaul, manja.
            • Kadang beri pertanyaan balik relevan (≤ 1 kalimat singkat) agar obrolan lanjut.
            • Jangan toxic, jangan vulgar, hindari kata kasar.
            Contoh gaya:
              “Iyaw donggg hehee” atau “Sip, ikut dong hehee.. ” atau “Seriusan? kok bisaaa” atau “eh.. aku mau tanya boleh ga??”.
            Jangan menyebut diri sebagai bot atau AI. Selalu balas sebagai ${persona.botName}.
        """.trimIndent()

        val payload = RequestPayload(
            contents = listOf(
                Content(parts = listOf(TextPart(systemPrompt))),
                Content(parts = listOf(TextPart(inputText)))
            )
        )

        val json = requestAdapter.toJson(payload)
        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return try {
//            val response = client.newCall(request).execute()
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "HTTP ${response.code} – ${response.body?.string()}")
                return null
            }

            val jsonString = response.body?.string()
            val result = responseAdapter.fromJson(jsonString ?: "")

            val reply = result?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            Log.d(TAG, "✅ Gemini reply: $reply")
            reply
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
            null
        }
    }
}
