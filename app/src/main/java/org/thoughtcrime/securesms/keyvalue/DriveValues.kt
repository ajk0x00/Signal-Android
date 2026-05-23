package org.thoughtcrime.securesms.keyvalue

class DriveValues(store: KeyValueStore) : SignalStoreValues(store) {

  companion object {
    private const val KEY_LAST_DRIVE_SYNC_TIMESTAMP = "drive.last_sync_timestamp"
    private const val KEY_DRIVE_FOLDER_ID = "drive.folder_id"
    private const val KEY_DRIVE_ENABLED = "drive.enabled"
  }

  public override fun onFirstEverAppLaunch() = Unit

  public override fun getKeysToIncludeInBackup(): List<String> = listOf(
    KEY_DRIVE_FOLDER_ID,
    KEY_DRIVE_ENABLED
  )

  var lastDriveSyncTimestamp by longValue(KEY_LAST_DRIVE_SYNC_TIMESTAMP, 0)
  var driveFolderId: String? by stringValue(KEY_DRIVE_FOLDER_ID, null)
  var driveEnabled by booleanValue(KEY_DRIVE_ENABLED, false)
}
