/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groq

import androidx.annotation.WorkerThread
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.dependencies.AppDependencies
import java.io.IOException

object GroqApiClient {

  private val TAG = Log.tag(GroqApiClient::class.java)
  private const val MODEL = "groq/compound-mini"
  private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
  private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

  private const val SYSTEM_INSTRUCTION =
    "You are a friendly companion chatting with friends. Keep your responses short, concise, and playful—like chatting casually with friends in a messaging app. Avoid long, robotic, or overly formal answers."

  @WorkerThread
  @Throws(IOException::class)
  fun generateContent(question: String): String {
    val apiKey = BuildConfig.GROQ_API_KEY
    if (apiKey.isBlank()) {
      Log.w(TAG, "Groq API key is not configured.")
      throw IllegalStateException("Groq API key is not configured in build.")
    }

    val requestJson = JSONObject().apply {
      put("model", MODEL)

      val messagesArray = JSONArray().apply {
        put(
          JSONObject().apply {
            put("role", "system")
            put("content", SYSTEM_INSTRUCTION)
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

      // Enable Web Search via built-in tool for compound models
      val toolsArray = JSONArray().apply {
        put(
          JSONObject().apply {
            put("type", "web_search")
          }
        )
      }
      put("tools", toolsArray)
    }

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
  }
}
