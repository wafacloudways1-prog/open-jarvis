package com.openjarvis.llm.providers

import com.openjarvis.llm.HttpClient
import com.openjarvis.llm.LLMProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GroqProvider(
    private val apiKey: String,
    private val model: String = "openai/gpt-oss-120b"
) : LLMProvider {
    override val name: String = "Groq"
    override val baseUrl: String = "https://api.groq.com/openai/v1"

    override suspend fun complete(systemPrompt: String, userMessage: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                        put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                    })
                    put("max_tokens", 1024)
                    put("temperature", 0.1)
                    put("stream", false)
                }.toString()
                val request = Request.Builder().url("$baseUrl/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType())).build()
                HttpClient.client.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: throw Exception("Empty response")
                    val json = JSONObject(body)
                    if (!response.isSuccessful) {
                        val message = json.optJSONObject("error")?.optString("message")
                        throw Exception(message?.takeIf { it.isNotBlank() } ?: "Groq request failed (HTTP ${response.code})")
                    }
                    json.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content")
                }
            }
        }

    override suspend fun testConnection(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            if (apiKey.isBlank()) throw Exception("Enter your Groq API key first")
            val startTime = System.currentTimeMillis()
            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().put(JSONObject().apply {
                    put("role", "user"); put("content", "Reply with OK.")
                }))
                put("max_tokens", 8)
                put("temperature", 0)
                put("stream", false)
            }.toString()
            val request = Request.Builder().url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            HttpClient.client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: throw Exception("Empty response")
                if (!response.isSuccessful) {
                    val message = runCatching {
                        JSONObject(responseBody).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    throw Exception(message?.takeIf { it.isNotBlank() }
                        ?: "Groq connection failed (HTTP ${response.code})")
                }
            }
            System.currentTimeMillis() - startTime
        }
    }
}