package org.thoughtcrime.securesms.stories.saved

import android.content.Context
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.cloudstorage.CloudStorageCredentialsProvider
import org.thoughtcrime.securesms.cloudstorage.CloudStorageServiceHelper
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryDatabase
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryRecord

class SavedStoriesRepository(private val context: Context) {

  companion object {
    private val TAG = Log.tag(SavedStoriesRepository::class.java)
  }

  fun getSavedStories(): List<SavedStoryRecord> {
    val db = SavedStoryDatabase(context)
    if (!db.exists()) {
      initializeFromCloudIfAvailable(db)
    }
    return db.getAll().sortedByDescending { it.timestamp }
  }

  private fun initializeFromCloudIfAvailable(db: SavedStoryDatabase) {
    val storage = CloudStorageCredentialsProvider.getStorageInstance(context) ?: return
    val (_, bucketName) = CloudStorageCredentialsProvider.getCredentialsAndBucket(context) ?: return
    try {
      val helper = CloudStorageServiceHelper(storage, bucketName)
      val prefix = helper.getPrefix(Recipient.self().profileName.toString())
      val cloudJson = helper.downloadJsonDb(prefix)
      if (cloudJson != null) {
        db.replaceFromJson(cloudJson)
        SignalStore.cloudStorage.bucketName = bucketName
        Log.i(TAG, "Initialized local saved-stories DB from cloud ($prefix)")
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to initialize local DB from cloud", e)
    }
  }

  fun deleteRecords(objectNames: Collection<String>) {
    if (objectNames.isEmpty()) return

    val db = SavedStoryDatabase(context)
    val storage = CloudStorageCredentialsProvider.getStorageInstance(context)
    val bucketName = SignalStore.cloudStorage.bucketName

    if (storage == null || bucketName == null) {
      Log.w(TAG, "Cannot delete remote objects: storage=$storage bucketName=$bucketName. Removing local records only.")
      objectNames.forEach { db.remove(it) }
      return
    }

    val helper = CloudStorageServiceHelper(storage, bucketName)
    objectNames.forEach { objectName ->
      try {
        helper.deleteBlob(objectName)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to delete remote object $objectName, continuing", e)
      }
      db.remove(objectName)
    }

    try {
      val prefix = helper.getPrefix(Recipient.self().profileName.toString())
      helper.uploadJsonDb(prefix, db.getJsonContent())
    } catch (e: Exception) {
      Log.w(TAG, "Failed to upload updated db json after delete", e)
    }
  }
}
