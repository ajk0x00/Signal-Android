/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.gemini

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

object GeminiApiClient {

  private val TAG = Log.tag(GeminiApiClient::class.java)
  private const val MODEL = "gemini-3.5-flash-lite"
  private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
  private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

  private const val SYSTEM_INSTRUCTION =
    "You are a friendly companion chatting with friends. Keep your responses short, concise, and playful—like chatting casually with friends in a messaging app. Avoid long, robotic, or overly formal answers."

  @WorkerThread
  @Throws(IOException::class)
  fun generateContent(question: String): String {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isBlank()) {
      Log.w(TAG, "Gemini API key is not configured.")
      throw IllegalStateException("Gemini API key is not configured in build.")
    }

    val url = "$BASE_URL/$MODEL:generateContent?key=$apiKey"

    val requestJson = JSONObject().apply {
      val systemInstructionObj = JSONObject().apply {
        val partsArray = JSONArray().apply {
          put(JSONObject().apply { put("text", SYSTEM_INSTRUCTION) })
        }
        put("parts", partsArray)
      }
      put("system_instruction", systemInstructionObj)

      val contentsArray = JSONArray().apply {
        val contentObj = JSONObject().apply {
          val partsArray = JSONArray().apply {
            put(JSONObject().apply { put("text", question) })
          }
          put("parts", partsArray)
        }
        put(contentObj)
      }
      put("contents", contentsArray)

      // Enable Grounding with Google Search
      val toolsArray = JSONArray().apply {
        put(JSONObject().apply {
          put("google_search", JSONObject())
        })
      }
      put("tools", toolsArray)
    }

    val requestBody = RequestBody.create(JSON_MEDIA_TYPE, requestJson.toString())
    val request = Request.Builder()
      .url(url)
      .post(requestBody)
      .build()

    AppDependencies.okHttpClient.newCall(request).execute().use { response ->
      val responseBodyString = response.body?.string() ?: ""

      if (!response.isSuccessful) {
        Log.w(TAG, "Gemini API failed with HTTP ${response.code}: $responseBodyString")
        throw IOException("Gemini API returned HTTP ${response.code}: $responseBodyString")
      }

      val json = JSONObject(responseBodyString)
      val candidates = json.optJSONArray("candidates")
      if (candidates == null || candidates.length() == 0) {
        Log.w(TAG, "Gemini API returned no candidates.")
        throw IOException("No candidates returned by Gemini API")
      }

      val firstCandidate = candidates.getJSONObject(0)
      val content = firstCandidate.optJSONObject("content")
      val parts = content?.optJSONArray("parts")

      if (parts == null || parts.length() == 0) {
        Log.w(TAG, "Gemini API returned no content parts.")
        throw IOException("No content parts in Gemini API response")
      }

      val resultBuilder = StringBuilder()
      for (i in 0 until parts.length()) {
        val part = parts.getJSONObject(i)
        val text = part.optString("text")
        if (text.isNotEmpty()) {
          resultBuilder.append(text)
        }
      }

      val answer = resultBuilder.toString().trim()
      if (answer.isEmpty()) {
        throw IOException("Empty response text from Gemini API")
      }

      return answer
    }
  }
}
