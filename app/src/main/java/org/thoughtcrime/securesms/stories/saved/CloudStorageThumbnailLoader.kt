package org.thoughtcrime.securesms.stories.saved

import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.cloudstorage.CloudStorageCredentialsProvider
import java.io.ByteArrayOutputStream
import java.io.InputStream

class CloudStorageThumbnailLoader : ModelLoader<CloudStorageThumbnailLoader.CloudStorageThumbnailKey, InputStream> {

  companion object {
    private val TAG = Log.tag(CloudStorageThumbnailLoader::class.java)
  }

  data class CloudStorageThumbnailKey(val objectName: String, val bucketName: String)

  override fun handles(model: CloudStorageThumbnailKey): Boolean = true

  override fun buildLoadData(model: CloudStorageThumbnailKey, width: Int, height: Int, options: com.bumptech.glide.load.Options): ModelLoader.LoadData<InputStream>? {
    return ModelLoader.LoadData(
      com.bumptech.glide.signature.ObjectKey(model.objectName),
      CloudStorageThumbnailFetcher(model)
    )
  }

  private class CloudStorageThumbnailFetcher(private val key: CloudStorageThumbnailKey) : com.bumptech.glide.load.data.DataFetcher<InputStream> {

    private var stream: InputStream? = null

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): com.bumptech.glide.load.DataSource = com.bumptech.glide.load.DataSource.REMOTE

    override fun loadData(priority: com.bumptech.glide.Priority, callback: com.bumptech.glide.load.data.DataFetcher.DataCallback<in InputStream>) {
      try {
        val context = org.thoughtcrime.securesms.dependencies.AppDependencies.application
        val storage = CloudStorageCredentialsProvider.getStorageInstance(context)
        if (storage == null) {
          callback.onLoadFailed(Exception("No Cloud Storage credentials"))
          return
        }

        val blob = storage.get(key.bucketName, key.objectName)
          ?: throw IllegalStateException("Object not found: ${key.objectName}")

        val outputStream = ByteArrayOutputStream()
        blob.downloadTo(outputStream)

        stream = outputStream.toByteArray().inputStream()
        callback.onDataReady(stream)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to load thumbnail from Cloud Storage", e)
        callback.onLoadFailed(e)
      }
    }

    override fun cleanup() {
      stream?.close()
    }

    override fun cancel() = Unit
  }

  class Factory : ModelLoaderFactory<CloudStorageThumbnailKey, InputStream> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<CloudStorageThumbnailKey, InputStream> {
      return CloudStorageThumbnailLoader()
    }

    override fun teardown() = Unit
  }
}
