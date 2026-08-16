package org.thoughtcrime.securesms.stories.saved

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.signal.core.util.bitmaps.BitmapUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.cloudstorage.CloudStorageCredentialsProvider
import org.thoughtcrime.securesms.cloudstorage.CloudStorageServiceHelper
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryDatabase
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryMediaType
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryRecord
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.File
import java.io.FileOutputStream
import java.util.Collections

class SavedStoriesRepository(private val context: Context) {

  companion object {
    private val TAG = Log.tag(SavedStoriesRepository::class.java)

    /** Object names of videos whose thumbnail backfill is currently running, to avoid duplicate work from grid rebinds. */
    private val backfillInFlight = Collections.synchronizedSet(mutableSetOf<String>())

    /** Caps concurrent backfill download/upload work so a grid full of legacy videos doesn't flood the network. */
    private val backfillSemaphore = Semaphore(2)
  }

  fun getSavedStories(): List<SavedStoryRecord> {
    val db = SavedStoryDatabase(context)
    if (!db.exists()) {
      initializeFromCloudIfAvailable(db)
    }
    return db.getAll().sortedByDescending { it.timestamp }
  }

  /**
   * Count of saved stories, restoring the local DB from cloud first if it doesn't exist yet.
   * Does network I/O when restoring; call off the main thread.
   */
  fun getSavedStoryCount(): Int {
    val db = SavedStoryDatabase(context)
    if (!db.exists()) {
      initializeFromCloudIfAvailable(db)
    }
    return db.getCount()
  }

  private fun initializeFromCloudIfAvailable(db: SavedStoryDatabase) {
    val self = Recipient.self()
    if (self.profileName.isEmpty) {
      Log.w(TAG, "Profile name not available yet; skipping cloud init so we don't query an empty prefix")
      return
    }

    val storage = CloudStorageCredentialsProvider.getStorageInstance(context) ?: return
    val (_, bucketName) = CloudStorageCredentialsProvider.getCredentialsAndBucket(context) ?: return
    try {
      val helper = CloudStorageServiceHelper(storage, bucketName)
      val prefix = helper.getPrefix(self.profileName.toString())
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

  /**
   * For a legacy VIDEO record with no stored thumbnail, downloads the video, extracts a frame, uploads it to the
   * thumbnails folder, and persists the resulting object name. Returns true if a thumbnail was generated.
   * Safe to call repeatedly for the same record (deduped + throttled).
   */
  suspend fun ensureVideoThumbnail(record: SavedStoryRecord): Boolean {
    val objectName = record.objectName
    if (record.mediaType != SavedStoryMediaType.VIDEO || record.thumbnailObjectName != null || objectName == null) {
      return false
    }
    if (!backfillInFlight.add(objectName)) {
      return false
    }

    return try {
      backfillSemaphore.withPermit {
        val storage = CloudStorageCredentialsProvider.getStorageInstance(context) ?: return@withPermit false
        val bucketName = SignalStore.cloudStorage.bucketName ?: return@withPermit false
        val helper = CloudStorageServiceHelper(storage, bucketName)

        val dir = File(context.cacheDir, "saved_story_thumb_src").apply { mkdirs() }
        val temp = File(dir, objectName.substringAfterLast('/').ifEmpty { "video.mp4" })
        try {
          FileOutputStream(temp).use { helper.downloadFile(objectName, it) }
          val frame = MediaUtil.getVideoThumbnail(context, Uri.fromFile(temp), 1000L)
          if (frame == null) {
            Log.w(TAG, "Backfill: could not extract frame for $objectName")
            return@withPermit false
          }
          val jpeg = BitmapUtil.toCompressedJpeg(frame)
          frame.recycle()

          val profileName = Recipient.self().profileName.toString()
          val thumbnailPrefix = helper.getThumbnailPrefix(profileName)
          val baseName = record.fileName.substringBeforeLast('.')
          val thumbnailObjectName = jpeg.use { helper.uploadFile(thumbnailPrefix, "$baseName.jpg", it, MediaUtil.IMAGE_JPEG) }

          val db = SavedStoryDatabase(context)
          db.update(objectName) { it.copy(thumbnailObjectName = thumbnailObjectName) }
          helper.uploadJsonDb(helper.getPrefix(profileName), db.getJsonContent())
          Log.i(TAG, "Backfilled thumbnail for $objectName -> $thumbnailObjectName")
          true
        } finally {
          temp.delete()
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to backfill thumbnail for $objectName", e)
      false
    } finally {
      backfillInFlight.remove(objectName)
    }
  }

  fun deleteRecords(objectNames: Collection<String>) {
    if (objectNames.isEmpty()) return

    val db = SavedStoryDatabase(context)
    val targets = objectNames.toSet()
    val thumbnailsByObject = db.getAll()
      .filter { it.objectName in targets }
      .mapNotNull { record -> record.objectName?.let { it to record.thumbnailObjectName } }
      .toMap()

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
      thumbnailsByObject[objectName]?.let { thumb ->
        try {
          helper.deleteBlob(thumb)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to delete remote thumbnail $thumb, continuing", e)
        }
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
