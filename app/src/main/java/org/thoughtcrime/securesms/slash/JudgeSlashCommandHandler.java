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
 * Slash command handler for /judge.
 */
public class JudgeSlashCommandHandler implements SlashCommandHandler {

  @Override
  public @NonNull String getCommandName() {
    return "judge";
  }

  @Override
  public boolean handleCommand(
      long threadId,
      @NonNull String fullBody,
      @NonNull String commandArgs,
      @Nullable QuoteModel quote,
      long originalSentTimestamp
  ) {
    String disputeDetails = commandArgs.trim();
    Context context = AppDependencies.getApplication();

    String judgeInstruction;
    try {
      judgeInstruction = context.getString(R.string.judge_instruction);
    } catch (Exception e) {
      judgeInstruction = "You are an impartial, witty, and hilarious judge. Hear all arguments and issue a lighthearted, playful verdict without hurting anyone's feelings.";
    }

    String question;
    if (!disputeDetails.isEmpty()) {
      question = judgeInstruction + "\n\nDispute details / user statement: \"" + disputeDetails + "\"";
    } else {
      question = judgeInstruction;
    }

    String participantDetails;
    try {
      participantDetails = context.getString(R.string.participant_details).trim();
    } catch (Exception e) {
      participantDetails = "";
    }

    if (!participantDetails.isEmpty()) {
      question = question + "\n\nBackground context (weak passive signal, use only if absolutely necessary):\n" + participantDetails;
    }

    String quotedText = (quote != null && quote.getText() != null) ? quote.getText().trim() : null;
    if (quotedText != null && quotedText.isEmpty()) {
      quotedText = null;
    }

    RecipientId quoteAuthorId = (quote != null) ? quote.getAuthor() : null;
    long quotedTimestamp = (quote != null) ? quote.getId() : 0L;

    AskGroqJob.enqueue(
        threadId,
        question,
        originalSentTimestamp,
        fullBody,
        quotedText,
        quoteAuthorId,
        true,
        quotedTimestamp
    );

    return true;
  }
}
