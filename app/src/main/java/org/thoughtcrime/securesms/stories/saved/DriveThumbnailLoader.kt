package org.thoughtcrime.securesms.stories.saved

import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.drive.DriveCredentialsProvider
import java.io.ByteArrayOutputStream
import java.io.InputStream

class DriveThumbnailLoader : ModelLoader<DriveThumbnailLoader.DriveThumbnailKey, InputStream> {

  companion object {
    private val TAG = Log.tag(DriveThumbnailLoader::class.java)
  }

  data class DriveThumbnailKey(val fileId: String)

  override fun handles(model: DriveThumbnailKey): Boolean = true

  override fun buildLoadData(model: DriveThumbnailKey, width: Int, height: Int, options: com.bumptech.glide.load.Options): ModelLoader.LoadData<InputStream>? {
    return ModelLoader.LoadData(
      com.bumptech.glide.signature.ObjectKey(model.fileId),
      DriveThumbnailFetcher(model)
    )
  }

  private class DriveThumbnailFetcher(private val key: DriveThumbnailKey) : com.bumptech.glide.load.data.DataFetcher<InputStream> {

    private var stream: InputStream? = null

    override fun getDataClass(): Class<InputStream> = InputStream::class.java

    override fun getDataSource(): com.bumptech.glide.load.DataSource = com.bumptech.glide.load.DataSource.REMOTE

    override fun loadData(priority: com.bumptech.glide.Priority, callback: com.bumptech.glide.load.data.DataFetcher.DataCallback<in InputStream>) {
      try {
        val context = org.thoughtcrime.securesms.dependencies.AppDependencies.application
        val credential = DriveCredentialsProvider.createCredential(context)
        if (credential == null) {
          callback.onLoadFailed(Exception("No Drive credentials"))
          return
        }

        val drive = Drive.Builder(
          NetHttpTransport(),
          JacksonFactory.getDefaultInstance(),
          credential
        ).setApplicationName("Signal").build()

        val outputStream = ByteArrayOutputStream()
        drive.files().get(key.fileId)
          .executeMediaAndDownloadTo(outputStream)

        stream = outputStream.toByteArray().inputStream()
        callback.onDataReady(stream)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to load thumbnail from Drive", e)
        callback.onLoadFailed(e)
      }
    }

    override fun cleanup() {
      stream?.close()
    }

    override fun cancel() = Unit
  }

  class Factory : ModelLoaderFactory<DriveThumbnailKey, InputStream> {
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<DriveThumbnailKey, InputStream> {
      return DriveThumbnailLoader()
    }

    override fun teardown() = Unit
  }
}
