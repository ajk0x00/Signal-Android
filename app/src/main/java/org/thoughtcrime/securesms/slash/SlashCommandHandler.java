package org.thoughtcrime.securesms.slash;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.mms.QuoteModel;

/**
 * Common interface for slash command handlers in chat conversations.
 */
public interface SlashCommandHandler {

  /**
   * Returns the command trigger word without the leading slash (e.g. "ask", "topic").
   */
  @NonNull
  String getCommandName();

  /**
   * Handles the command execution when matched.
   *
   * @param threadId               The target thread ID
   * @param fullBody               The full text message body
   * @param commandArgs            The arguments string following the command
   * @param quote                  Optional quote model attached to the message
   * @param originalSentTimestamp  Original message sent timestamp
   * @return true if the command was handled successfully, false otherwise
   */
  boolean handleCommand(
      long threadId,
      @NonNull String fullBody,
      @NonNull String commandArgs,
      @Nullable QuoteModel quote,
      long originalSentTimestamp
  );
}
