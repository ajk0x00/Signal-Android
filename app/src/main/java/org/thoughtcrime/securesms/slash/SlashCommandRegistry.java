package org.thoughtcrime.securesms.slash;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import org.thoughtcrime.securesms.mms.QuoteModel;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry and dispatcher for slash commands.
 */
public final class SlashCommandRegistry {

  private static final Map<String, SlashCommandHandler> HANDLERS = new HashMap<>();

  static {
    registerHandler(new AskSlashCommandHandler());
    registerHandler(new TopicSlashCommandHandler());
    registerHandler(new RapAboutSlashCommandHandler());
    registerHandler(new JudgeSlashCommandHandler());
  }

  private SlashCommandRegistry() {
  }

  public static void registerHandler(@NonNull SlashCommandHandler handler) {
    HANDLERS.put(handler.getCommandName().toLowerCase(), handler);
  }

  /**
   * Processes an outgoing message text to check if it matches a slash command.
   * If matched, dispatches to the corresponding handler.
   *
   * @return true if a command was matched and handled, false otherwise
   */
  public static boolean process(
      long threadId,
      @NonNull String body,
      @Nullable QuoteModel quote,
      long originalSentTimestamp
  ) {
    String trimmed = body.trim();
    if (!trimmed.startsWith("/")) {
      return false;
    }

    int spaceIndex = trimmed.indexOf(' ');
    String commandName;
    String commandArgs;

    if (spaceIndex != -1) {
      commandName = trimmed.substring(1, spaceIndex).toLowerCase();
      commandArgs = trimmed.substring(spaceIndex + 1);
    } else {
      commandName = trimmed.substring(1).toLowerCase();
      commandArgs = "";
    }

    SlashCommandHandler handler = HANDLERS.get(commandName);
    if (handler != null) {
      return handler.handleCommand(threadId, body, commandArgs, quote, originalSentTimestamp);
    }

    return false;
  }
}
