package org.thoughtcrime.securesms.stories.cloudstorage

import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.cloudstorage.CloudStorageCredentialsProvider
import org.thoughtcrime.securesms.cloudstorage.CloudStorageServiceHelper
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient

class CloudStorageSyncJob private constructor(parameters: Parameters) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(CloudStorageSyncJob::class.java)
    const val KEY = "CloudStorageSyncJob"
    private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L

    @JvmStatic
    fun enqueueIfNecessary() {
      if (!SignalStore.account.isRegistered) {
        Log.i(TAG, "Not registered yet, skipping cloud sync until login")
        return
      }

      val context = AppDependencies.application
      val db = SavedStoryDatabase(context)
      val lastSync = db.getLastSyncTimestamp()
      if (System.currentTimeMillis() - lastSync < SYNC_INTERVAL_MS) {
        Log.i(TAG, "Sync not needed yet")
        return
      }

      val job = CloudStorageSyncJob(
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
    if (!SignalStore.account.isRegistered) {
      Log.w(TAG, "Not registered, aborting cloud sync")
      return Result.success()
    }

    val context = AppDependencies.application
    val storage = CloudStorageCredentialsProvider.getStorageInstance(context)
      ?: return Result.failure()

    val (_, bucketName) = CloudStorageCredentialsProvider.getCredentialsAndBucket(context)
      ?: return Result.failure()

    val helper = CloudStorageServiceHelper(storage, bucketName)
    val profileName = Recipient.self().profileName.toString()
    val prefix = helper.getPrefix(profileName)
    SignalStore.cloudStorage.bucketName = bucketName

    val db = SavedStoryDatabase(context)
    val localExists = db.exists()
    val cloudJson = helper.downloadJsonDb(prefix)
    val cloudExists = cloudJson != null

    when {
      !localExists && cloudExists -> {
        db.replaceFromJson(cloudJson as String)
        Log.i(TAG, "Restored local DB from Cloud")
      }
      localExists && !cloudExists -> {
        helper.uploadJsonDb(prefix, db.getJsonContent())
        Log.i(TAG, "Uploaded local DB to Cloud")
      }
      localExists && cloudExists -> {
        val cloudDb = kotlinx.serialization.json.Json {
          ignoreUnknownKeys = true
          coerceInputValues = true
        }.decodeFromString<SavedStoryDatabaseModel>(cloudJson as String)
        val localRecords = db.getAll()

        val pendingUploads = localRecords.filter { it.objectName == null }
        val mergedRecords = cloudDb.savedStories + pendingUploads
        db.replaceAll(mergedRecords)

        helper.uploadJsonDb(prefix, db.getJsonContent())
        Log.i(TAG, "Merged DBs and uploaded (${pendingUploads.size} pending uploads)")
      }
      else -> {
        Log.i(TAG, "Both DBs empty, nothing to sync")
      }
    }

    db.setLastSyncTimestamp(System.currentTimeMillis())
    SignalStore.cloudStorage.lastSyncTimestamp = System.currentTimeMillis()
    return Result.success()
  }

  override fun onFailure() {
    Log.w(TAG, "Cloud sync failed")
  }

  class Factory : Job.Factory<CloudStorageSyncJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): CloudStorageSyncJob {
      return CloudStorageSyncJob(parameters)
    }
  }
}
