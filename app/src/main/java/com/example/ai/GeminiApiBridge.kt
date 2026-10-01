package com.example.ai

import com.example.BuildConfig
import com.example.data.entity.UserSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class GeminiResult {
    data class Success(
        val responseText: String,
        val promptTokens: Int,
        val candidateTokens: Int,
        val modelUsed: String
    ) : GeminiResult()

    data class Error(val message: String) : GeminiResult()
}

object GeminiApiBridge {

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Resolves the effective API key (User custom key in settings takes precedence, then BuildConfig).
     */
    fun resolveApiKey(settings: UserSettingsEntity?): String {
        val customKey = settings?.customApiKey?.trim().orEmpty()
        if (customKey.isNotEmpty()) {
            return customKey
        }
        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Executes the Gemini REST API call with dynamically structured prompt and system instructions.
     */
    suspend fun generateContent(
        promptInjection: PromptInjectionResult,
        settings: UserSettingsEntity
    ): GeminiResult = withContext(Dispatchers.IO) {
        val apiKey = resolveApiKey(settings)

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiResult.Error(
                "Gemini API key is not configured. Please enter your API key in Settings or configure the AI Studio Secrets panel."
            )
        }

        val rawModelName = settings.modelName.trim().ifEmpty { "gemini-2.5-flash" }
        // Clean model name if user provided "models/..." prefix
        val modelName = rawModelName.removePrefix("models/")

        val endpoint = "$BASE_URL$modelName:generateContent?key=$apiKey"

        try {
            val rootJson = JSONObject()

            // 1. Contents array
            val contentsArray = JSONArray()
            val userContent = JSONObject()
            val partsArray = JSONArray()
            val partText = JSONObject().put("text", promptInjection.effectiveUserPrompt)
            partsArray.put(partText)
            userContent.put("role", "user")
            userContent.put("parts", partsArray)
            contentsArray.put(userContent)
            rootJson.put("contents", contentsArray)

            // 2. System Instruction (if using system_instruction strategy and not blank)
            if (promptInjection.effectiveSystemInstruction.isNotBlank()) {
                val sysInstObj = JSONObject()
                val sysPartsArray = JSONArray()
                sysPartsArray.put(JSONObject().put("text", promptInjection.effectiveSystemInstruction))
                sysInstObj.put("parts", sysPartsArray)
                rootJson.put("systemInstruction", sysInstObj)
            }

            // 3. Generation Config (low temperature for adherence, fast answers)
            val genConfig = JSONObject()
            genConfig.put("temperature", 0.4)
            genConfig.put("maxOutputTokens", 1024)
            rootJson.put("generationConfig", genConfig)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = rootJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val errorMsg = try {
                    val errJson = JSONObject(responseBodyString)
                    errJson.optJSONObject("error")?.optString("message")
                        ?: "HTTP ${response.code}: ${response.message}"
                } catch (e: Exception) {
                    "HTTP ${response.code}: $responseBodyString"
                }
                return@withContext GeminiResult.Error(errorMsg)
            }

            // Parse response
            val respJson = JSONObject(responseBodyString)
            val candidates = respJson.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext GeminiResult.Error("No response candidates returned by Gemini.")
            }

            val firstCandidate = candidates.getJSONObject(0)
            val contentObj = firstCandidate.optJSONObject("content")
            val parts = contentObj?.optJSONArray("parts")
            val generatedText = if (parts != null && parts.length() > 0) {
                parts.getJSONObject(0).optString("text", "")
            } else {
                "No text generated."
            }

            // Usage metadata
            val usageMetadata = respJson.optJSONObject("usageMetadata")
            val promptTokens = usageMetadata?.optInt("promptTokenCount")
                ?: (promptInjection.effectiveUserPrompt.length / 4)
            val candTokens = usageMetadata?.optInt("candidatesTokenCount")
                ?: (generatedText.length / 4)

            GeminiResult.Success(
                responseText = generatedText,
                promptTokens = promptTokens,
                candidateTokens = candTokens,
                modelUsed = modelName
            )
        } catch (e: Exception) {
            GeminiResult.Error(e.localizedMessage ?: "Unknown network error during Gemini API call")
        }
    }

    /**
     * Extracts potential memory nuggets locally from user text.
     */
    fun extractCandidateFacts(text: String): List<Pair<String, String>> {
        val facts = mutableListOf<Pair<String, String>>()
        val lower = text.lowercase()

        // Simple high-precision regex heuristic for local fact detection
        val patterns = listOf(
            Regex("(?:i prefer|i like|i always use|my favorite)\\s+([^.,;!?]+)", RegexOption.IGNORE_CASE) to "Preference",
            Regex("(?:my name is|i am an?|i work as|i live in)\\s+([^.,;!?]+)", RegexOption.IGNORE_CASE) to "Fact",
            Regex("(?:do not|never|always remember to|don't)\\s+([^.,;!?]+)", RegexOption.IGNORE_CASE) to "Constraint",
            Regex("(?:my project is|i'm building|we are developing)\\s+([^.,;!?]+)", RegexOption.IGNORE_CASE) to "Project"
        )

        for ((regex, tag) in patterns) {
            val match = regex.find(text)
            if (match != null && match.groupValues.size > 1) {
                val extracted = match.value.trim()
                if (extracted.length in 8..80) {
                    facts.add(Pair(extracted, tag))
                }
            }
        }
        return facts
    }
}
