package org.thoughtcrime.securesms.cloudstorage

import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.Storage.BlobListOption
import org.signal.core.util.logging.Log
import java.io.InputStream
import java.io.OutputStream

class CloudStorageServiceHelper(
  private val storage: Storage,
  private val bucketName: String
) {

  companion object {
    private val TAG = Log.tag(CloudStorageServiceHelper::class.java)
    private const val DB_OBJECT_NAME = "saved_stories_db.json"
  }

  fun getPrefix(profileName: String): String {
    return "$profileName/"
  }

  fun uploadFile(prefix: String, fileName: String, inputStream: InputStream, mimeType: String): String {
    val objectName = prefix + fileName
    val blobInfo = BlobInfo.newBuilder(bucketName, objectName)
      .setContentType(mimeType)
      .build()

    storage.create(blobInfo, inputStream)
    Log.i(TAG, "Uploaded object: $objectName")
    return objectName
  }

  fun downloadFile(objectName: String, outputStream: OutputStream) {
    val blob = storage.get(bucketName, objectName)
      ?: throw IllegalStateException("Object not found: $objectName")
    blob.downloadTo(outputStream)
  }

  fun listObjects(prefix: String): List<CloudStorageObjectInfo> {
    val blobs = storage.list(bucketName, BlobListOption.prefix(prefix)).iterateAll()
    return blobs
      .filter { !it.name.endsWith(DB_OBJECT_NAME) }
      .map { blob ->
        CloudStorageObjectInfo(
          objectName = blob.name,
          name = blob.name.removePrefix(prefix),
          contentType = blob.contentType ?: "application/octet-stream",
          size = blob.size,
          updatedTime = blob.updateTime ?: blob.createTime
        )
      }
  }

  fun downloadJsonDb(prefix: String): String? {
    val objectName = prefix + DB_OBJECT_NAME
    val blob = storage.get(bucketName, objectName)
      ?: return null

    val bytes = blob.getContent()
    Log.i(TAG, "Downloaded DB object: $objectName")
    return String(bytes, Charsets.UTF_8)
  }

  fun deleteBlob(objectName: String): Boolean {
    val deleted = storage.delete(bucketName, objectName)
    Log.i(TAG, "Delete $objectName -> $deleted")
    return deleted
  }

  fun uploadJsonDb(prefix: String, jsonContent: String) {
    val objectName = prefix + DB_OBJECT_NAME
    val blobInfo = BlobInfo.newBuilder(bucketName, objectName)
      .setContentType("application/json")
      .build()

    storage.create(blobInfo, jsonContent.toByteArray(Charsets.UTF_8))
    Log.i(TAG, "Uploaded DB object: $objectName")
  }
}
