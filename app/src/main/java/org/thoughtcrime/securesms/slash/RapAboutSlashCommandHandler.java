package org.thoughtcrime.securesms.slash;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.AskGroqJob;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.recipients.RecipientId;

/**
 * Slash command handler for /rapabout.
 */
public class RapAboutSlashCommandHandler implements SlashCommandHandler {

  @Override
  public @NonNull String getCommandName() {
    return "rapabout";
  }

  @Override
  public boolean handleCommand(
      long threadId,
      @NonNull String fullBody,
      @NonNull String commandArgs,
      @Nullable QuoteModel quote,
      long originalSentTimestamp
  ) {
    String topicHint = commandArgs.trim();
    Context context = AppDependencies.getApplication();

    String rapInstruction;
    try {
      rapInstruction = context.getString(R.string.rapabout_instruction);
    } catch (Exception e) {
      rapInstruction = "Write a short, creative, funny, and rhyming rap song based on the context.";
    }

    String question;
    if (!topicHint.isEmpty()) {
      question = rapInstruction + "\n\nRap topic / focus requested by user: \"" + topicHint + "\"";
    } else {
      question = rapInstruction;
    }


    String quotedText = (quote != null && quote.getText() != null) ? quote.getText().trim() : null;
    if (quotedText != null && quotedText.isEmpty()) {
      quotedText = null;
    }

    RecipientId quoteAuthorId = (quote != null) ? quote.getAuthor() : null;

    AskGroqJob.enqueue(threadId, question, originalSentTimestamp, fullBody, quotedText, quoteAuthorId);
    return true;
  }
}
