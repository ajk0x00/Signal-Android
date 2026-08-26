package org.thoughtcrime.securesms.slash;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.AskGroqJob;
import org.thoughtcrime.securesms.mms.QuoteModel;

/**
 * Slash command handler for /topic.
 */
public class TopicSlashCommandHandler implements SlashCommandHandler {

  @Override
  public @NonNull String getCommandName() {
    return "topic";
  }

  @Override
  public boolean handleCommand(
      long threadId,
      @NonNull String fullBody,
      @NonNull String commandArgs,
      @Nullable QuoteModel quote,
      long originalSentTimestamp
  ) {
    String hint = commandArgs.trim();
    Context context = AppDependencies.getApplication();

    String topicInstruction;
    try {
      topicInstruction = context.getString(R.string.topic_instruction);
    } catch (Exception e) {
      topicInstruction = "Suggest a fun, engaging, and unique conversation topic to break the silence and spark discussion.";
    }

    String question;
    if (!hint.isEmpty()) {
      question = topicInstruction + "\n\nSpecific category/focus requested by user: \"" + hint + "\"";
    } else {
      question = topicInstruction;
    }

    String participantDetails;
    try {
      participantDetails = context.getString(R.string.participant_details).trim();
    } catch (Exception e) {
      participantDetails = "";
    }

    if (!participantDetails.isEmpty()) {
      question = question + "\n\nAdditional context:\n" + participantDetails;
    }

    String quotedText = (quote != null && quote.getText() != null) ? quote.getText().trim() : null;
    if (quotedText != null && quotedText.isEmpty()) {
      quotedText = null;
    }

    AskGroqJob.enqueue(threadId, question, quotedText, originalSentTimestamp, fullBody);
    return true;
  }
}
