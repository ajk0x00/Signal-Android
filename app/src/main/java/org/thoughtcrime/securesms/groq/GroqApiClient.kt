/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groq

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.io.IOException

object GroqApiClient {

  private val TAG = Log.tag(GroqApiClient::class.java)
  const val DEFAULT_MODEL = "openai/gpt-oss-120b"
  private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
  private const val MODELS_ENDPOINT = "https://api.groq.com/openai/v1/models"
  private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

  val DEFAULT_MODELS = listOf(
    "openai/gpt-oss-120b",
    "groq/compound-mini",
    "llama-3.3-70b-versatile",
    "llama-3.1-8b-instant",
    "mixtral-8x7b-32768",
    "deepseek-r1-distill-llama-70b",
    "gemma2-9b-it"
  )

  fun getSystemInstruction(): String {
    return try {
      AppDependencies.application.getString(org.thoughtcrime.securesms.R.string.groq_ai_system_instruction)
    } catch (e: Exception) {
      "You are a friendly companion chatting with friends. Keep your responses short, concise, and playful—like chatting casually with friends in a messaging app. Avoid long, robotic, or overly formal answers."
    }
  }

  fun getSelectedModel(): String {
    return SignalStore.settings.aiModel.ifBlank { DEFAULT_MODEL }
  }

  @VisibleForTesting
  internal fun buildRequestBodyJson(question: String): JSONObject {
    return JSONObject().apply {
      put("model", getSelectedModel())

      val messagesArray = JSONArray().apply {
        put(
          JSONObject().apply {
            put("role", "system")
            put("content", getSystemInstruction())
          }
        )
        put(
          JSONObject().apply {
            put("role", "user")
            put("content", question)
          }
        )
      }
      put("messages", messagesArray)
    }
  }

  @VisibleForTesting
  internal fun parseResponseBodyJson(responseBodyString: String): String {
    val json = JSONObject(responseBodyString)
    val choices = json.optJSONArray("choices")
    if (choices == null || choices.length() == 0) {
      Log.w(TAG, "Groq API returned no choices.")
      throw IOException("No choices returned by Groq API")
    }

    val firstChoice = choices.getJSONObject(0)
    val message = firstChoice.optJSONObject("message")
    val content = message?.optString("content")?.trim()

    if (content.isNullOrEmpty()) {
      Log.w(TAG, "Groq API returned empty message content.")
      throw IOException("Empty response text from Groq API")
    }

    return content
  }

  @WorkerThread
  @Throws(IOException::class)
  fun generateContent(question: String): String {
    val apiKey = BuildConfig.GROQ_API_KEY
    if (apiKey.isBlank()) {
      Log.w(TAG, "Groq API key is not configured.")
      throw IOException("Groq API key is not configured in build.")
    }

    val requestJson = buildRequestBodyJson(question)
    val requestBody = RequestBody.create(JSON_MEDIA_TYPE, requestJson.toString())
    val request = Request.Builder()
      .url(ENDPOINT)
      .header("Authorization", "Bearer $apiKey")
      .post(requestBody)
      .build()

    AppDependencies.okHttpClient.newCall(request).execute().use { response ->
      val responseBodyString = response.body?.string() ?: ""

      if (!response.isSuccessful) {
        Log.w(TAG, "Groq API failed with HTTP ${response.code}: $responseBodyString")
        throw IOException("Groq API returned HTTP ${response.code}: $responseBodyString")
      }

      return parseResponseBodyJson(responseBodyString)
    }
  }

  @WorkerThread
  fun fetchAvailableModels(): List<String> {
    val apiKey = BuildConfig.GROQ_API_KEY
    if (apiKey.isBlank()) {
      return DEFAULT_MODELS
    }

    val request = Request.Builder()
      .url(MODELS_ENDPOINT)
      .header("Authorization", "Bearer $apiKey")
      .get()
      .build()

    return try {
      AppDependencies.okHttpClient.newCall(request).execute().use { response ->
        val bodyString = response.body?.string() ?: ""
        if (!response.isSuccessful || bodyString.isBlank()) {
          Log.w(TAG, "Failed to fetch models from Groq API (HTTP ${response.code}), using defaults")
          return DEFAULT_MODELS
        }
        val json = JSONObject(bodyString)
        val dataArray = json.optJSONArray("data") ?: return DEFAULT_MODELS
        val modelList = mutableListOf<String>()
        for (i in 0 until dataArray.length()) {
          val modelObj = dataArray.getJSONObject(i)
          val id = modelObj.optString("id")
          if (id.isNotBlank()) {
            modelList.add(id)
          }
        }
        if (modelList.isNotEmpty()) {
          if (!modelList.contains(DEFAULT_MODEL)) {
            modelList.add(0, DEFAULT_MODEL)
          }
          modelList.distinct()
        } else {
          DEFAULT_MODELS
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Error fetching models from Groq API", e)
      DEFAULT_MODELS
    }
  }
}
