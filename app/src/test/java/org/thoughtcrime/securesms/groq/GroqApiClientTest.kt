/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groq

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class GroqApiClientTest {

  @Test
  fun `buildRequestBodyJson creates valid payload without tools field`() {
    val requestJson = GroqApiClient.buildRequestBodyJson("What is the capital of France?")

    assertThat(requestJson.getString("model")).isEqualTo("groq/compound-mini")
    assertThat(requestJson.has("tools")).isFalse()

    val messages = requestJson.getJSONArray("messages")
    assertThat(messages.length()).isEqualTo(2)

    val systemMessage = messages.getJSONObject(0)
    assertThat(systemMessage.getString("role")).isEqualTo("system")
    assertThat(systemMessage.getString("content")).isNotNull()

    val userMessage = messages.getJSONObject(1)
    assertThat(userMessage.getString("role")).isEqualTo("user")
    assertThat(userMessage.getString("content")).isEqualTo("What is the capital of France?")
  }

  @Test
  fun `parseResponseBodyJson successfully extracts content from valid Groq compound response`() {
    val jsonResponse = """
      {
        "id": "chatcmpl-12345",
        "object": "chat.completion",
        "created": 1723810000,
        "model": "groq/compound-mini",
        "choices": [
          {
            "index": 0,
            "message": {
              "role": "assistant",
              "content": "Paris is the capital of France.",
              "reasoning": "Looking up capital of France...",
              "executed_tools": []
            },
            "finish_reason": "stop"
          }
        ],
        "usage": {
          "prompt_tokens": 20,
          "completion_tokens": 10,
          "total_tokens": 30
        }
      }
    """.trimIndent()

    val content = GroqApiClient.parseResponseBodyJson(jsonResponse)
    assertThat(content).isEqualTo("Paris is the capital of France.")
  }

  @Test
  fun `parseResponseBodyJson throws on empty choices`() {
    val jsonResponse = """
      {
        "id": "chatcmpl-12345",
        "choices": []
      }
    """.trimIndent()

    assertThrows(IOException::class.java) {
      GroqApiClient.parseResponseBodyJson(jsonResponse)
    }
  }

  @Test
  fun `parseResponseBodyJson throws on missing or empty message content`() {
    val jsonResponse = """
      {
        "id": "chatcmpl-12345",
        "choices": [
          {
            "index": 0,
            "message": {
              "role": "assistant",
              "content": "   "
            }
          }
        ]
      }
    """.trimIndent()

    assertThrows(IOException::class.java) {
      GroqApiClient.parseResponseBodyJson(jsonResponse)
    }
  }
}
