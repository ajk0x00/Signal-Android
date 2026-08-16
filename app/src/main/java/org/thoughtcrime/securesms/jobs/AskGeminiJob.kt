/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.gemini.GeminiApiClient
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import java.io.IOException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Background job to query Gemini 3.5 Flash-Lite with Google Search Grounding
 * and send the response back into the conversation thread as a quote-reply.
 */
class AskGeminiJob private constructor(
  private val threadId: Long,
  private val question: String,
  private val originalSentTimestamp: Long,
  private val originalBody: String,
  parameters: Parameters
) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(AskGeminiJob::class.java)

    const val KEY = "AskGeminiJob"

    private const val KEY_THREAD_ID = "thread_id"
    private const val KEY_QUESTION = "question"
    private const val KEY_ORIGINAL_SENT_TIMESTAMP = "original_sent_timestamp"
    private const val KEY_ORIGINAL_BODY = "original_body"

    @JvmStatic
    fun enqueue(
      threadId: Long,
      question: String,
      originalSentTimestamp: Long,
      originalBody: String
    ) {
      AppDependencies.jobManager.add(
        AskGeminiJob(
          threadId = threadId,
          question = question,
          originalSentTimestamp = originalSentTimestamp,
          originalBody = originalBody
        )
      )
    }
  }

  private constructor(
    threadId: Long,
    question: String,
    originalSentTimestamp: Long,
    originalBody: String
  ) : this(
    threadId = threadId,
    question = question,
    originalSentTimestamp = originalSentTimestamp,
    originalBody = originalBody,
    parameters = Parameters.Builder()
      .setQueue("AskGeminiJob")
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(3)
      .build()
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder()
      .putLong(KEY_THREAD_ID, threadId)
      .putString(KEY_QUESTION, question)
      .putLong(KEY_ORIGINAL_SENT_TIMESTAMP, originalSentTimestamp)
      .putString(KEY_ORIGINAL_BODY, originalBody)
      .serialize()
  }

  override fun getFactoryKey(): String = KEY

  override fun onRun() {
    Log.i(TAG, "Executing AskGeminiJob for thread $threadId, question length: ${question.length}")

    val recipient = SignalDatabase.threads.getRecipientForThreadId(threadId)
    if (recipient == null) {
      Log.w(TAG, "Recipient not found for thread $threadId, skipping.")
      return
    }

    val answer = GeminiApiClient.generateContent(question)
    Log.i(TAG, "Received answer from Gemini, sending quote reply to thread $threadId")

    val quote = QuoteModel(
      id = originalSentTimestamp,
      author = Recipient.self().id,
      text = originalBody,
      isOriginalMissing = false,
      attachment = null,
      mentions = emptyList(),
      type = QuoteModel.Type.NORMAL,
      bodyRanges = null
    )

    val outgoingMessage = OutgoingMessage(
      threadRecipient = recipient,
      sentTimeMillis = System.currentTimeMillis(),
      body = "🤖 $answer",
      expiresIn = recipient.expiresInSeconds.seconds.inWholeMilliseconds,
      isUrgent = true,
      isSecure = true,
      outgoingQuote = quote
    )

    MessageSender.send(
      context,
      outgoingMessage,
      threadId,
      MessageSender.SendType.SIGNAL,
      null,
      null
    )
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return when (e) {
      is IllegalStateException -> false // Missing API key or configuration error
      is IOException -> true            // Network failure / rate limit / 5xx
      else -> false
    }
  }

  override fun onFailure() {
    Log.w(TAG, "AskGeminiJob failed permanently for thread $threadId")
  }

  class Factory : Job.Factory<AskGeminiJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AskGeminiJob {
      val data = JsonJobData.deserialize(serializedData)
      return AskGeminiJob(
        threadId = data.getLong(KEY_THREAD_ID),
        question = data.getString(KEY_QUESTION),
        originalSentTimestamp = data.getLong(KEY_ORIGINAL_SENT_TIMESTAMP),
        originalBody = data.getString(KEY_ORIGINAL_BODY),
        parameters = parameters
      )
    }
  }
}
