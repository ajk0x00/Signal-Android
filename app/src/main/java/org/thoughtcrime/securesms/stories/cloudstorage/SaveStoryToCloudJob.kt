package org.thoughtcrime.securesms.stories.cloudstorage

import android.annotation.SuppressLint
import android.net.Uri
import com.bumptech.glide.load.Options
import org.signal.core.util.bitmaps.BitmapUtil
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.cloudstorage.CloudStorageCredentialsProvider
import org.thoughtcrime.securesms.cloudstorage.CloudStorageServiceHelper
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.database.withAttachments
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.mms.PartAuthority
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.StoryTextPostModel
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaveStoryToCloudJob private constructor(
  private val messageId: Long,
  private val attachmentId: Long,
  parameters: Parameters
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(SaveStoryToCloudJob::class.java)
    const val KEY = "SaveStoryToCloudJob"

    /**
     * All saves share one queue so they run sequentially. Each job does a read-modify-write of the saved-stories JSON
     * DB (local + cloud); running them in parallel (e.g. "Save all stories") would clobber each other's writes and only
     * the last record would survive, even though every media file uploaded fine.
     */
    private const val QUEUE = "SaveStoryToCloudJob"

    /** Sentinel meaning "no specific attachment; use the first slide of the story". */
    private const val NO_ATTACHMENT = -1L

    @JvmStatic
    fun enqueue(messageId: Long) {
      enqueue(messageId, NO_ATTACHMENT)
    }

    @JvmStatic
    fun enqueue(messageId: Long, attachmentId: Long) {
      val job = SaveStoryToCloudJob(
        messageId = messageId,
        attachmentId = attachmentId,
        parameters = Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setQueue(QUEUE)
          .setMaxAttempts(3)
          .build()
      )
      AppDependencies.jobManager.add(job)
    }
  }

  override fun serialize(): ByteArray = "$messageId:$attachmentId".toByteArray()

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    return try {
      runUpload()
    } catch (e: Exception) {
      Log.w(TAG, "Upload failed with exception", e)
      Result.failure()
    }
  }

  @SuppressLint("ThreadConstraint")
  private fun runUpload(): Result {
    val application = AppDependencies.application
    val messageRecord = SignalDatabase.messages.getMessageRecordOrNull(messageId)?.withAttachments()
    if (messageRecord == null) {
      Log.w(TAG, "Aborting: message record not found for id=$messageId")
      return Result.failure()
    }

    val storage = CloudStorageCredentialsProvider.getStorageInstance(application)
    if (storage == null) {
      Log.w(TAG, "Aborting: failed to build Storage client (check cloud_storage_config.json)")
      return Result.failure()
    }

    val credAndBucket = CloudStorageCredentialsProvider.getCredentialsAndBucket(application)
    if (credAndBucket == null) {
      Log.w(TAG, "Aborting: credentials/bucket unavailable")
      return Result.failure()
    }
    val bucketName = credAndBucket.second

    val helper = CloudStorageServiceHelper(storage, bucketName)
    val profileName = Recipient.self().profileName.toString()
    val prefix = helper.getPrefix(profileName)
    SignalStore.cloudStorage.bucketName = bucketName

    val initDb = SavedStoryDatabase(application)
    if (!initDb.exists()) {
      try {
        helper.downloadJsonDb(prefix)?.let {
          initDb.replaceFromJson(it)
          Log.i(TAG, "Initialized local DB from existing cloud DB at $prefix")
        }
      } catch (e: Exception) {
        Log.w(TAG, "Could not pre-load cloud DB; proceeding with empty local DB", e)
      }
    }

    val mmsRecord = messageRecord as? org.thoughtcrime.securesms.database.model.MmsMessageRecord
    if (mmsRecord == null) {
      Log.w(TAG, "Aborting: message $messageId is not an MmsMessageRecord (class=${messageRecord.javaClass.simpleName})")
      return Result.failure()
    }

    // For text stories there is no slide. Otherwise select the requested attachment (album-aware) or the first slide.
    var videoThumbnailSourceUri: Uri? = null
    val (inputStream, mimeType, extension) = if (mmsRecord.storyType.isTextStory) {
      val model = StoryTextPostModel.parseFrom(mmsRecord)
      val decoder = StoryTextPostModel.Decoder()
      val bitmap = decoder.decode(model, 1080, 1920, Options()).get()
      val jpeg: ByteArrayInputStream = BitmapUtil.toCompressedJpeg(bitmap)
      bitmap.recycle()
      Triple(jpeg as InputStream, MediaUtil.IMAGE_JPEG, "jpg")
    } else {
      val slide = selectSlide(mmsRecord)
      if (slide == null) {
        Log.w(TAG, "Aborting: no slide on mms message $messageId (attachmentId=$attachmentId)")
        return Result.failure()
      }
      val uri = slide.uri
      if (uri == null) {
        Log.w(TAG, "Aborting: slide has no uri (contentType=${slide.contentType})")
        return Result.failure()
      }
      val stream = PartAuthority.getAttachmentStream(application, uri)
      val ext = when {
        MediaUtil.isVideoType(slide.contentType) -> "mp4"
        MediaUtil.isImageType(slide.contentType) -> "jpg"
        else -> "bin"
      }
      if (MediaUtil.isVideoType(slide.contentType)) {
        videoThumbnailSourceUri = uri
      }
      Triple(stream, slide.contentType ?: "application/octet-stream", ext)
    }

    // Include the message (and attachment) id so distinct stories posted within the same second don't collide on the
    // same object name. Re-saving the exact same item still yields the same name, so the duplicate guard below works.
    val dateFormatter = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.US)
    val idSuffix = if (attachmentId == NO_ATTACHMENT) "_$messageId" else "_${messageId}_$attachmentId"
    val baseName = dateFormatter.format(Date(messageRecord.dateSent)) + idSuffix
    val fileName = "$baseName.$extension"

    inputStream.use { stream ->
      Log.i(TAG, "Uploading $fileName to bucket=$bucketName prefix=$prefix")
      val objectName = helper.uploadFile(prefix, fileName, stream, mimeType)

      val mediaType = when {
        mmsRecord.storyType.isTextStory -> SavedStoryMediaType.TEXT
        MediaUtil.isVideoType(mimeType) -> SavedStoryMediaType.VIDEO
        else -> SavedStoryMediaType.IMAGE
      }

      val db = SavedStoryDatabase(application)
      if (db.getAll().any { it.objectName == objectName }) {
        Log.i(TAG, "Skipping db add: object already saved ($objectName)")
        SignalStore.cloudStorage.enabled = true
        return Result.success()
      }

      val thumbnailObjectName = if (mediaType == SavedStoryMediaType.VIDEO && videoThumbnailSourceUri != null) {
        uploadVideoThumbnail(application, helper, profileName, baseName, videoThumbnailSourceUri!!)
      } else {
        null
      }

      val record = SavedStoryRecord(
        fileName = fileName,
        mediaType = mediaType,
        timestamp = messageRecord.dateSent,
        fileSize = 0,
        senderName = profileName,
        objectName = objectName,
        thumbnailObjectName = thumbnailObjectName
      )

      db.add(record)
      helper.uploadJsonDb(prefix, db.getJsonContent())

      SignalStore.cloudStorage.enabled = true
      Log.i(TAG, "Story saved to Cloud: $fileName")
    }

    return Result.success()
  }

  /** Returns the slide matching [attachmentId], or the first slide when no specific attachment was requested. */
  private fun selectSlide(mmsRecord: org.thoughtcrime.securesms.database.model.MmsMessageRecord): org.thoughtcrime.securesms.mms.Slide? {
    if (attachmentId == NO_ATTACHMENT) {
      return mmsRecord.slideDeck.firstSlide
    }
    return mmsRecord.slideDeck.slides.firstOrNull {
      (it.asAttachment() as? org.thoughtcrime.securesms.attachments.DatabaseAttachment)?.attachmentId?.id == attachmentId
    } ?: mmsRecord.slideDeck.firstSlide
  }

  /** Extracts a frame from the video at [sourceUri], uploads it under the thumbnails folder, and returns its object name (or null on failure). */
  private fun uploadVideoThumbnail(
    application: android.content.Context,
    helper: CloudStorageServiceHelper,
    profileName: String,
    baseName: String,
    sourceUri: Uri
  ): String? {
    return try {
      val frame = MediaUtil.getVideoThumbnail(application, sourceUri, 1000L)
      if (frame == null) {
        Log.w(TAG, "Could not extract video frame for thumbnail")
        return null
      }
      val jpeg = BitmapUtil.toCompressedJpeg(frame)
      frame.recycle()
      val thumbnailPrefix = helper.getThumbnailPrefix(profileName)
      jpeg.use { helper.uploadFile(thumbnailPrefix, "$baseName.jpg", it, MediaUtil.IMAGE_JPEG) }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to upload video thumbnail; continuing without it", e)
      null
    }
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to save story to Cloud")
  }

  class Factory : Job.Factory<SaveStoryToCloudJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SaveStoryToCloudJob {
      val raw = serializedData?.let { String(it) } ?: "0"
      val parts = raw.split(":")
      val messageId = parts.getOrNull(0)?.toLongOrNull() ?: 0L
      val attachmentId = parts.getOrNull(1)?.toLongOrNull() ?: NO_ATTACHMENT
      return SaveStoryToCloudJob(messageId, attachmentId, parameters)
    }
  }
}
