package org.thoughtcrime.securesms.stories.cloudstorage

import com.bumptech.glide.load.Options
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
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaveStoryToCloudJob private constructor(
  private val messageId: Long,
  parameters: Parameters
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(SaveStoryToCloudJob::class.java)
    const val KEY = "SaveStoryToCloudJob"

    @JvmStatic
    fun enqueue(messageId: Long) {
      val job = SaveStoryToCloudJob(
        messageId = messageId,
        parameters = Parameters.Builder()
          .addConstraint(NetworkConstraint.KEY)
          .setMaxAttempts(3)
          .build()
      )
      AppDependencies.jobManager.add(job)
    }
  }

  constructor(messageId: Long) : this(
    messageId = messageId,
    Parameters.Builder()
      .addConstraint(NetworkConstraint.KEY)
      .setMaxAttempts(3)
      .build()
  )

  override fun serialize(): ByteArray = messageId.toString().toByteArray()

  override fun getFactoryKey(): String = KEY

  override suspend fun doRun(): Result {
    return try {
      runUpload()
    } catch (e: Exception) {
      Log.w(TAG, "Upload failed with exception", e)
      Result.failure()
    }
  }

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

    val (inputStream, mimeType, extension) = if (mmsRecord.storyType.isTextStory) {
      val model = StoryTextPostModel.parseFrom(mmsRecord)
      val decoder = StoryTextPostModel.Decoder()
      val bitmap = decoder.decode(model, 1080, 1920, Options()).get()
      val jpeg: ByteArrayInputStream = BitmapUtil.toCompressedJpeg(bitmap)
      bitmap.recycle()
      Triple(jpeg as InputStream, MediaUtil.IMAGE_JPEG, "jpg")
    } else {
      val slide = mmsRecord.slideDeck.firstSlide
      if (slide == null) {
        Log.w(TAG, "Aborting: no slide on mms message $messageId")
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
      Triple(stream, slide.contentType ?: "application/octet-stream", ext)
    }

    val dateFormatter = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.US)
    val fileName = "${dateFormatter.format(Date(messageRecord.dateSent))}.$extension"

    inputStream.use { stream ->
      Log.i(TAG, "Uploading $fileName to bucket=$bucketName prefix=$prefix")
      val objectName = helper.uploadFile(prefix, fileName, stream, mimeType)

      val mediaType = when {
        mmsRecord.storyType.isTextStory -> SavedStoryMediaType.TEXT
        MediaUtil.isVideoType(mimeType) -> SavedStoryMediaType.VIDEO
        else -> SavedStoryMediaType.IMAGE
      }

      val record = SavedStoryRecord(
        fileName = fileName,
        mediaType = mediaType,
        timestamp = messageRecord.dateSent,
        fileSize = 0,
        senderName = profileName,
        objectName = objectName
      )

      val db = SavedStoryDatabase(application)
      db.add(record)
      helper.uploadJsonDb(prefix, db.getJsonContent())

      SignalStore.cloudStorage.enabled = true
      Log.i(TAG, "Story saved to Cloud: $fileName")
    }

    return Result.success()
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to save story to Cloud")
  }

  class Factory : Job.Factory<SaveStoryToCloudJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SaveStoryToCloudJob {
      val messageId = serializedData?.let { String(it).toLong() } ?: 0L
      return SaveStoryToCloudJob(messageId, parameters)
    }
  }
}
