package org.thoughtcrime.securesms.keyvalue

class CloudStorageValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_LAST_SYNC_TIMESTAMP = "cloudstorage.last_sync_timestamp"
    private const val KEY_BUCKET_NAME = "cloudstorage.bucket_name"
    private const val KEY_ENABLED = "cloudstorage.enabled"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = listOf(
    KEY_BUCKET_NAME,
    KEY_ENABLED
  )

  var lastSyncTimestamp by longValue(KEY_LAST_SYNC_TIMESTAMP, 0)
  var bucketName: String? by stringValue(KEY_BUCKET_NAME, null)
  var enabled by booleanValue(KEY_ENABLED, false)
}
