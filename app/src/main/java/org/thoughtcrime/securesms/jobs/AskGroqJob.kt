/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobs

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.groq.GroqApiClient
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.JsonJobData
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.recipients.RecipientId
import java.io.IOException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * Background job to query Groq compound-mini with web search
 * and send the response back into the conversation thread as a quote-reply.
 */
class AskGroqJob private constructor(
  private val threadId: Long,
  private val question: String,
  private val quotedText: String?,
  private val quoteAuthorId: RecipientId?,
  private val includeThreadContext: Boolean,
  private val quotedTimestamp: Long,
  private val originalSentTimestamp: Long,
  private val originalBody: String,
  parameters: Parameters
) : BaseJob(parameters) {

  companion object {
    private val TAG = Log.tag(AskGroqJob::class.java)

    const val KEY = "AskGroqJob"
    private const val MAX_ATTEMPTS = 3

    private const val KEY_THREAD_ID = "thread_id"
    private const val KEY_QUESTION = "question"
    private const val KEY_QUOTED_TEXT = "quoted_text"
    private const val KEY_QUOTE_AUTHOR_ID = "quote_author_id"
    private const val KEY_INCLUDE_THREAD_CONTEXT = "include_thread_context"
    private const val KEY_QUOTED_TIMESTAMP = "quoted_timestamp"
    private const val KEY_ORIGINAL_SENT_TIMESTAMP = "original_sent_timestamp"
    private const val KEY_ORIGINAL_BODY = "original_body"

    @JvmStatic
    @JvmOverloads
    fun enqueue(
      threadId: Long,
      question: String,
      originalSentTimestamp: Long,
      originalBody: String,
      quotedText: String? = null,
      quoteAuthorId: RecipientId? = null,
      includeThreadContext: Boolean = false,
      quotedTimestamp: Long = 0L
    ) {
      AppDependencies.jobManager.add(
        AskGroqJob(
          threadId = threadId,
          question = question,
          quotedText = quotedText,
          quoteAuthorId = quoteAuthorId,
          includeThreadContext = includeThreadContext,
          quotedTimestamp = quotedTimestamp,
          originalSentTimestamp = originalSentTimestamp,
          originalBody = originalBody
        )
      )
    }
  }

  private constructor(
    threadId: Long,
    question: String,
    quotedText: String?,
    quoteAuthorId: RecipientId?,
    includeThreadContext: Boolean,
    quotedTimestamp: Long,
    originalSentTimestamp: Long,
    originalBody: String
  ) : this(
    threadId = threadId,
    question = question,
    quotedText = quotedText,
    quoteAuthorId = quoteAuthorId,
    includeThreadContext = includeThreadContext,
    quotedTimestamp = quotedTimestamp,
    originalSentTimestamp = originalSentTimestamp,
    originalBody = originalBody,
    parameters = Parameters.Builder()
      .setQueue("AskGroqJob")
      .setLifespan(1.days.inWholeMilliseconds)
      .setMaxAttempts(MAX_ATTEMPTS)
      .build()
  )

  override fun serialize(): ByteArray? {
    val builder = JsonJobData.Builder()
      .putLong(KEY_THREAD_ID, threadId)
      .putString(KEY_QUESTION, question)
      .putString(KEY_QUOTED_TEXT, quotedText)
      .putBoolean(KEY_INCLUDE_THREAD_CONTEXT, includeThreadContext)
      .putLong(KEY_QUOTED_TIMESTAMP, quotedTimestamp)
      .putLong(KEY_ORIGINAL_SENT_TIMESTAMP, originalSentTimestamp)
      .putString(KEY_ORIGINAL_BODY, originalBody)

    if (quoteAuthorId != null) {
      builder.putLong(KEY_QUOTE_AUTHOR_ID, quoteAuthorId.toLong())
    }

    return builder.serialize()
  }

  override fun getFactoryKey(): String = KEY

  override fun onRun() {
    Log.i(TAG, "Executing AskGroqJob for thread $threadId, question length: ${question.length}, hasQuotedText: ${!quotedText.isNullOrBlank()}")

    val recipient = SignalDatabase.threads.getRecipientForThreadId(threadId)
    if (recipient == null) {
      Log.w(TAG, "Recipient not found for thread $threadId, skipping.")
      return
    }

    val prompt = if (!quotedText.isNullOrBlank()) {
      val senderName = if (quotedText.trim().startsWith("🤖")) {
        "AI Assistant"
      } else if (quoteAuthorId != null) {
        val authorRecipient = Recipient.resolved(quoteAuthorId)
        authorRecipient.getDisplayName(context)
      } else {
        null
      }

      val header = if (senderName != null) {
        "Quoted message (from $senderName):"
      } else {
        "Quoted message:"
      }

      if (question.isNotBlank()) {
        "$header\n\"\"\"\n$quotedText\n\"\"\"\n\nQuestion: $question"
      } else {
        "$header\n\"\"\"\n$quotedText\n\"\"\"\n\nPlease explain or answer regarding the quoted message above."
      }
    } else {
      question
    }

    val threadMessagesContext = if (includeThreadContext) {
      val records = if (quotedTimestamp > 0L) {
        SignalDatabase.messages.getMessagesInThreadAfter(threadId, quotedTimestamp, 30)
      } else {
        SignalDatabase.messages.getRecentMessagesInThread(threadId, 25)
      }

      val formattedMessages = records.mapNotNull { msg ->
        val text = msg.body?.trim()
        if (text.isNullOrBlank() || text.startsWith("/")) {
          null
        } else {
          val sender = if (text.startsWith("🤖")) {
            "AI Assistant"
          } else {
            msg.fromRecipient.getDisplayName(context)
          }
          "- [$sender]: $text"
        }
      }

      if (formattedMessages.isNotEmpty()) {
        val historyLabel = if (quotedTimestamp > 0L) "Subsequent conversation history" else "Recent conversation history"
        "\n\n$historyLabel:\n" + formattedMessages.joinToString("\n")
      } else {
        ""
      }
    } else {
      ""
    }

    val fullPrompt = prompt + threadMessagesContext
    val answer = GroqApiClient.generateContent(fullPrompt)
    Log.i(TAG, "Received answer from Groq, sending quote reply to thread $threadId")
    sendReply(recipient, "🤖 $answer")
  }

  override fun onShouldRetry(e: Exception): Boolean {
    return when (e) {
      is IOException -> !org.thoughtcrime.securesms.BuildConfig.GROQ_API_KEY.isBlank()
      else -> false
    }
  }

  override fun onFailure() {
    Log.w(TAG, "AskGroqJob failed permanently for thread $threadId")
    val recipient = SignalDatabase.threads.getRecipientForThreadId(threadId)
    if (recipient != null) {
      val errorMessage = if (org.thoughtcrime.securesms.BuildConfig.GROQ_API_KEY.isBlank()) {
        "🤖 Groq API key is not configured. Please set the GROQ_API_KEY build configuration to use /ask."
      } else {
        "🤖 Sorry, I couldn't get a response from Groq. Please check your network or try again later."
      }
      sendReply(recipient, errorMessage)
    }
  }

  private fun sendReply(recipient: Recipient, replyBody: String) {
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
      body = replyBody,
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

  class Factory : Job.Factory<AskGroqJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): AskGroqJob {
      val data = JsonJobData.deserialize(serializedData)
      val quoteAuthorId = if (data.hasLong(KEY_QUOTE_AUTHOR_ID)) RecipientId.from(data.getLong(KEY_QUOTE_AUTHOR_ID)) else null

      return AskGroqJob(
        threadId = data.getLong(KEY_THREAD_ID),
        question = data.getString(KEY_QUESTION),
        quotedText = data.getStringOrDefault(KEY_QUOTED_TEXT, null),
        quoteAuthorId = quoteAuthorId,
        includeThreadContext = data.getBooleanOrDefault(KEY_INCLUDE_THREAD_CONTEXT, false),
        quotedTimestamp = data.getLongOrDefault(KEY_QUOTED_TIMESTAMP, 0L),
        originalSentTimestamp = data.getLong(KEY_ORIGINAL_SENT_TIMESTAMP),
        originalBody = data.getString(KEY_ORIGINAL_BODY),
        parameters = parameters
      )
    }
  }
}
