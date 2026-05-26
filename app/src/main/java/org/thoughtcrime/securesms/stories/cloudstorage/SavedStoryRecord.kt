package org.thoughtcrime.securesms.stories.cloudstorage

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
  val objectName: String? = null
)
