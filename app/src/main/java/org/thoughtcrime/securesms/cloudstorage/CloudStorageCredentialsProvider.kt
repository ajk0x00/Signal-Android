package org.thoughtcrime.securesms.cloudstorage

import android.content.Context
import com.google.auth.oauth2.GoogleCredentials
import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.signal.core.util.logging.Log

object CloudStorageCredentialsProvider {

  private val TAG = Log.tag(CloudStorageCredentialsProvider::class.java)

  @Volatile
  private var cachedStorage: Storage? = null
  private val lock = Any()

  fun getCredentialsAndBucket(context: Context): Pair<GoogleCredentials, String>? {
    try {
      val stream = context.assets.open("cloud_storage_config.json")
      val bytes = stream.readBytes()
      stream.close()

      val credential = ServiceAccountCredentials.fromStream(bytes.inputStream())
        .createScoped(listOf("https://www.googleapis.com/auth/devstorage.read_write"))

      val json = org.json.JSONObject(bytes.toString(Charsets.UTF_8))
      val bucketName = json.optString("bucket_name")
        .ifEmpty { json.optString("bucket") }
        .ifEmpty { json.optString("bucket_id") }
        .ifEmpty { org.thoughtcrime.securesms.keyvalue.SignalStore.cloudStorage.bucketName.orEmpty() }

      if (bucketName.isBlank()) {
        Log.w(TAG, "cloud_storage_config.json is missing 'bucket_name'. Please add \"bucket_name\": \"<your-bucket-name>\" to your service account JSON configuration.")
        return null
      }

      return Pair(credential, bucketName)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to load Cloud Storage credentials", e)
      return null
    }
  }

  fun getStorageInstance(context: Context): Storage? {
    cachedStorage?.let { return it }
    synchronized(lock) {
      cachedStorage?.let { return it }
      val (credentials, _) = getCredentialsAndBucket(context) ?: return null
      val storage = StorageOptions.newBuilder()
        .setCredentials(credentials)
        .build()
        .service
      cachedStorage = storage
      return storage
    }
  }
}
