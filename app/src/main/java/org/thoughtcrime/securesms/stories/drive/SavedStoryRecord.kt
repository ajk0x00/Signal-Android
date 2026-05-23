package org.thoughtcrime.securesms.stories.drive

import kotlinx.serialization.Serializable

@Serializable
enum class SavedStoryMediaType {
  IMAGE,
  VIDEO,
  TEXT
}

@Serializable
data class SavedStoryRecord(
  val fileName: String,
  val mediaType: SavedStoryMediaType,
  val timestamp: Long,
  val fileSize: Long,
  val senderName: String,
  val driveFileId: String? = null
)
