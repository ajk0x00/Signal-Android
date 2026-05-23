package org.thoughtcrime.securesms.stories.drive

import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.drive.DriveCredentialsProvider
import org.thoughtcrime.securesms.drive.DriveServiceHelper
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient

class DriveSyncJob private constructor(parameters: Parameters) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(DriveSyncJob::class.java)
    const val KEY = "DriveSyncJob"
    private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L

    @JvmStatic
    fun enqueueIfNecessary() {
      val context = AppDependencies.application
      val db = SavedStoryDatabase(context)
      val lastSync = db.getLastSyncTimestamp()
      if (System.currentTimeMillis() - lastSync < SYNC_INTERVAL_MS) {
        Log.i(TAG, "Sync not needed yet")
        return
      }

      val job = DriveSyncJob(
        Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setMaxAttempts(3)
          .build()
      )
      AppDependencies.jobManager.add(job)
    }
  }

  constructor() : this(
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(3)
      .build()
  )

  override fun serialize(): ByteArray? = null

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    val context = AppDependencies.application
    val credential = DriveCredentialsProvider.createCredential(context)
      ?: return Result.failure()

    val drive = Drive.Builder(
      NetHttpTransport(),
      JacksonFactory.getDefaultInstance(),
      credential
    ).setApplicationName("Signal").build()

    val helper = DriveServiceHelper(drive)
    val profileName = Recipient.self().profileName.toString()
    val folderId = helper.ensureFolderExists(profileName)
    SignalStore.drive.driveFolderId = folderId

    val db = SavedStoryDatabase(context)
    val localExists = db.exists()
    val driveJson = helper.downloadJsonDb(folderId)
    val driveExists = driveJson != null

    when {
      !localExists && driveExists -> {
        db.replaceFromJson(driveJson as String)
        Log.i(TAG, "Restored local DB from Drive")
      }
      localExists && !driveExists -> {
        helper.uploadJsonDb(folderId, db.getJsonContent())
        Log.i(TAG, "Uploaded local DB to Drive")
      }
      localExists && driveExists -> {
        val driveDb = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
          .decodeFromString<SavedStoryDatabaseModel>(driveJson as String)
        val localRecords = db.getAll()

        val pendingUploads = localRecords.filter { it.driveFileId == null }
        val mergedRecords = driveDb.savedStories + pendingUploads
        db.replaceAll(mergedRecords)

        helper.uploadJsonDb(folderId, db.getJsonContent())
        Log.i(TAG, "Merged DBs and uploaded (${pendingUploads.size} pending uploads)")
      }
      else -> {
        Log.i(TAG, "Both DBs empty, nothing to sync")
      }
    }

    db.setLastSyncTimestamp(System.currentTimeMillis())
    SignalStore.drive.lastDriveSyncTimestamp = System.currentTimeMillis()
    return Result.success()
  }

  override fun onFailure() {
    Log.w(TAG, "Drive sync failed")
  }

  class Factory : Job.Factory<DriveSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): DriveSyncJob {
      return DriveSyncJob(parameters)
    }
  }
}
