package com.example.ai

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository {
    private val apiKey: String = BuildConfig.GEMINI_API_KEY

    suspend fun getColorGradingAdvice(userQuery: String, chatHistory: List<Content> = emptyList()): String = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Gemini API key is missing. Add GEMINI_API_KEY to your local .env file and rebuild."
        }

        val historyWithNewQuery = chatHistory.toMutableList()
        historyWithNewQuery.add(Content(parts = listOf(Part(text = userQuery)), role = "user"))

        val request = GenerateContentRequest(
             contents = historyWithNewQuery,
             generationConfig = GenerationConfig(
                 thinkingConfig = ThinkingConfig(thinkingLevel = "HIGH")
             ),
             systemInstruction = Content(
                 parts = listOf(
                     Part(text = "You are ChromaBot, an elite, professional colorist AI assistant built into ChromaPro. You provide expert color grading advice for professional filmmakers akin to DaVinci Resolve workflows. Explain Lift, Gamma, Gain, Offset, LUTs, color spaces (like Rec.709, DCI-P3), log curves, and advanced real-time AI color adjustments. Think highly before answering.")
                 )
             )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response from AI."
        } catch (e: Exception) {
            "Error analyzing color request: ${e.message}"
        }
    }
}
