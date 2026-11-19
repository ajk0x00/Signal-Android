package org.thoughtcrime.securesms.keyvalue

import org.signal.storage.keyvalue.KeyValueStore
import org.signal.storage.keyvalue.SignalStoreValues
import org.signal.storage.keyvalue.booleanValue
import org.signal.storage.keyvalue.longValue
import org.signal.storage.keyvalue.stringValue

class CloudStorageValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_LAST_SYNC_TIMESTAMP = "cloudstorage.last_sync_timestamp"
    private const val KEY_BUCKET_NAME = "cloudstorage.bucket_name"
    private const val KEY_ENABLED = "cloudstorage.enabled"
  }

  override fun onFirstEverAppLaunch() = Unit

  override val keysToIncludeInBackup: List<String> = listOf(
    KEY_BUCKET_NAME,
    KEY_ENABLED
  )

  var lastSyncTimestamp by longValue(KEY_LAST_SYNC_TIMESTAMP, 0)
  var bucketName: String? by stringValue(KEY_BUCKET_NAME, null)
  var enabled by booleanValue(KEY_ENABLED, false)
}
