/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class AskGroqJobTest {

  @Test
  fun `serialize and deserialize with quotedText`() {
    val job = AskGroqJob.Factory().create(
      Job.Parameters.Builder().build(),
      JsonJobData.Builder()
        .putLong("thread_id", 123L)
        .putString("question", "what is this?")
        .putString("quoted_text", "hello world")
        .putLong("quote_author_id", 789L)
        .putLong("original_sent_timestamp", 456L)
        .putString("original_body", "/ask what is this?")
        .serialize()
    )

    val serialized = job.serialize()
    assertThat(serialized).isNotNull()

    val deserialized = JsonJobData.deserialize(serialized)
    assertThat(deserialized.getLong("thread_id")).isEqualTo(123L)
    assertThat(deserialized.getString("question")).isEqualTo("what is this?")
    assertThat(deserialized.getStringOrDefault("quoted_text", null)).isEqualTo("hello world")
    assertThat(deserialized.getLong("quote_author_id")).isEqualTo(789L)
    assertThat(deserialized.getLong("original_sent_timestamp")).isEqualTo(456L)
    assertThat(deserialized.getString("original_body")).isEqualTo("/ask what is this?")
  }

  @Test
  fun `serialize and deserialize without quotedText`() {
    val job = AskGroqJob.Factory().create(
      Job.Parameters.Builder().build(),
      JsonJobData.Builder()
        .putLong("thread_id", 123L)
        .putString("question", "tell me a joke")
        .putLong("original_sent_timestamp", 456L)
        .putString("original_body", "/ask tell me a joke")
        .serialize()
    )

    val serialized = job.serialize()
    assertThat(serialized).isNotNull()

    val deserialized = JsonJobData.deserialize(serialized)
    assertThat(deserialized.getLong("thread_id")).isEqualTo(123L)
    assertThat(deserialized.getString("question")).isEqualTo("tell me a joke")
    assertThat(deserialized.getStringOrDefault("quoted_text", null)).isNull()
    assertThat(deserialized.getLong("original_sent_timestamp")).isEqualTo(456L)
    assertThat(deserialized.getString("original_body")).isEqualTo("/ask tell me a joke")
  }
}
