package org.thoughtcrime.securesms.drive

data class DriveFileInfo(
  val id: String,
  val name: String,
  val mimeType: String,
  val size: Long,
  val modifiedTime: Long
)
