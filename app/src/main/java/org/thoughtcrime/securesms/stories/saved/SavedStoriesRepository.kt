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
    return db.getAll().sortedByDescending { it.timestamp }
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
