package org.thoughtcrime.securesms.cloudstorage

data class CloudStorageObjectInfo(
  val objectName: String,
  val name: String,
  val contentType: String,
  val size: Long,
  val updatedTime: Long
)
