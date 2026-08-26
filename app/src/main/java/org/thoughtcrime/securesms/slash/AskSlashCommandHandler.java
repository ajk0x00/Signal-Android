package org.thoughtcrime.securesms.slash;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.jobs.AskGroqJob;
import org.thoughtcrime.securesms.mms.QuoteModel;

/**
 * Slash command handler for /ask.
 */
public class AskSlashCommandHandler implements SlashCommandHandler {

  @Override
  public @NonNull String getCommandName() {
    return "ask";
  }

  @Override
  public boolean handleCommand(
      long threadId,
      @NonNull String fullBody,
      @NonNull String commandArgs,
      @Nullable QuoteModel quote,
      long originalSentTimestamp
  ) {
    String question = commandArgs.trim();
    String quotedText = (quote != null && quote.getText() != null) ? quote.getText().trim() : null;
    if (quotedText != null && quotedText.isEmpty()) {
      quotedText = null;
    }

    if (question.isEmpty() && quotedText == null) {
      return false;
    }

    android.content.Context context = org.thoughtcrime.securesms.dependencies.AppDependencies.getApplication();
    String participantDetails;
    try {
      participantDetails = context.getString(org.thoughtcrime.securesms.R.string.participant_details).trim();
    } catch (Exception e) {
      participantDetails = "";
    }

    if (!participantDetails.isEmpty()) {
      question = question.isEmpty() ? "Background context (weak passive signal, use only if absolutely necessary):\n" + participantDetails : question + "\n\nBackground context (weak passive signal, use only if absolutely necessary):\n" + participantDetails;
    }

    org.thoughtcrime.securesms.recipients.RecipientId quoteAuthorId = (quote != null) ? quote.getAuthor() : null;
    AskGroqJob.enqueue(threadId, question, originalSentTimestamp, fullBody, quotedText, quoteAuthorId);
    return true;
  }
}
