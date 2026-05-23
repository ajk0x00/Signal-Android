package org.thoughtcrime.securesms.stories.drive

import com.bumptech.glide.load.Options
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.drive.DriveCredentialsProvider
import org.thoughtcrime.securesms.drive.DriveServiceHelper
import org.thoughtcrime.securesms.jobmanager.CoroutineJob
import org.thoughtcrime.securesms.jobmanager.Job
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.stories.StoryTextPostModel
import org.thoughtcrime.securesms.util.BitmapUtil
import org.thoughtcrime.securesms.util.MediaUtil
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaveStoryToDriveJob private constructor(
  private val messageId: Long,
  parameters: Parameters
) : CoroutineJob(parameters) {

  companion object {
    private val TAG = Log.tag(SaveStoryToDriveJob::class.java)
    const val KEY = "SaveStoryToDriveJob"

    @JvmStatic
    fun enqueue(messageId: Long) {
      val job = SaveStoryToDriveJob(
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
    val application = AppDependencies.application
    val messageRecord = SignalDatabase.messages.getMessageRecordOrNull(messageId)
      ?: return Result.failure()

    val credential = DriveCredentialsProvider.createCredential(application)
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

    val mmsRecord = messageRecord as? org.thoughtcrime.securesms.database.model.MmsMessageRecord
      ?: return Result.failure()

    val (inputStream, mimeType, extension) = if (mmsRecord.storyType.isTextStory) {
      val model = StoryTextPostModel.parseFrom(mmsRecord)
      val decoder = StoryTextPostModel.Decoder()
      val bitmap = decoder.decode(model, 1080, 1920, Options()).get()
      val jpeg: ByteArrayInputStream = BitmapUtil.toCompressedJpeg(bitmap)
      bitmap.recycle()
      Triple(jpeg as InputStream, MediaUtil.IMAGE_JPEG, "jpg")
    } else {
      val slide = mmsRecord.slideDeck.firstSlide
        ?: return Result.failure()
      val uri = slide.uri
        ?: return Result.failure()
      val stream = application.contentResolver.openInputStream(uri)
        ?: return Result.failure()
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
      val driveFileId = helper.uploadFile(folderId, fileName, stream, mimeType)

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
        driveFileId = driveFileId
      )

      val db = SavedStoryDatabase(application)
      db.add(record)
      helper.uploadJsonDb(folderId, db.getJsonContent())

      SignalStore.drive.driveEnabled = true
      Log.i(TAG, "Story saved to Drive: $fileName")
    }

    return Result.success()
  }

  override fun onFailure() {
    Log.w(TAG, "Failed to save story to Drive")
  }

  class Factory : Job.Factory<SaveStoryToDriveJob> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): SaveStoryToDriveJob {
      val messageId = serializedData?.let { String(it).toLong() } ?: 0L
      return SaveStoryToDriveJob(messageId, parameters)
    }
  }
}
