package org.thoughtcrime.securesms.stories.saved

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.cloudstorage.CloudStorageCredentialsProvider
import org.thoughtcrime.securesms.cloudstorage.CloudStorageServiceHelper
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryMediaType
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryRecord
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme
import java.io.File
import java.io.FileOutputStream

class SavedStoryViewerActivity : PassphraseRequiredActivity() {

  companion object {
    private val TAG = Log.tag(SavedStoryViewerActivity::class.java)
    private const val EXTRA_OBJECT_NAME = "object_name"
    private const val EXTRA_MEDIA_TYPE = "media_type"

    @JvmStatic
    fun intent(context: Context, record: SavedStoryRecord): Intent {
      return Intent(context, SavedStoryViewerActivity::class.java).apply {
        putExtra(EXTRA_OBJECT_NAME, record.objectName)
        putExtra(EXTRA_MEDIA_TYPE, record.mediaType.name)
      }
    }
  }

  private val dynamicTheme = DynamicNoActionBarTheme()
  private var player: ExoPlayer? = null

  override fun onPreCreate() {
    dynamicTheme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    setContentView(R.layout.saved_story_viewer_activity)

    val objectName = intent.getStringExtra(EXTRA_OBJECT_NAME)
    val mediaTypeName = intent.getStringExtra(EXTRA_MEDIA_TYPE)
    val bucketName = SignalStore.cloudStorage.bucketName

    if (objectName == null || mediaTypeName == null || bucketName == null) {
      Log.w(TAG, "Missing extras or bucket. objectName=$objectName mediaType=$mediaTypeName bucket=$bucketName")
      finish()
      return
    }

    findViewById<ImageButton>(R.id.back).setOnClickListener { finish() }

    val mediaType = runCatching { SavedStoryMediaType.valueOf(mediaTypeName) }.getOrDefault(SavedStoryMediaType.IMAGE)
    when (mediaType) {
      SavedStoryMediaType.VIDEO -> showVideo(objectName)
      else -> showImage(objectName, bucketName)
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }

  override fun onDestroy() {
    player?.release()
    player = null
    super.onDestroy()
  }

  private fun showImage(objectName: String, bucketName: String) {
    val image = findViewById<PhotoView>(R.id.image)
    image.visibility = View.VISIBLE
    com.bumptech.glide.Glide.with(this)
      .load(CloudStorageThumbnailLoader.CloudStorageThumbnailKey(objectName, bucketName))
      .fitCenter()
      .into(image)
  }

  private fun showVideo(objectName: String) {
    val playerView = findViewById<PlayerView>(R.id.player)
    val loading = findViewById<ProgressBar>(R.id.loading)
    playerView.visibility = View.VISIBLE
    loading.visibility = View.VISIBLE

    lifecycleScope.launch {
      val file = withContext(Dispatchers.IO) { downloadToCache(objectName) }
      loading.visibility = View.GONE
      if (file == null) {
        Toast.makeText(this@SavedStoryViewerActivity, R.string.SavedStoryViewerActivity__playback_error, Toast.LENGTH_SHORT).show()
        finish()
        return@launch
      }
      val exo = ExoPlayer.Builder(this@SavedStoryViewerActivity).build()
      player = exo
      playerView.player = exo
      exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
      exo.prepare()
      exo.playWhenReady = true
    }
  }

  private fun downloadToCache(objectName: String): File? {
    return try {
      val storage = CloudStorageCredentialsProvider.getStorageInstance(this) ?: return null
      val bucketName = SignalStore.cloudStorage.bucketName ?: return null
      val helper = CloudStorageServiceHelper(storage, bucketName)
      val dir = File(cacheDir, "saved_stories").apply { mkdirs() }
      val file = File(dir, objectName.substringAfterLast('/').ifEmpty { "video.mp4" })
      FileOutputStream(file).use { helper.downloadFile(objectName, it) }
      file
    } catch (e: Exception) {
      Log.w(TAG, "Failed to download $objectName", e)
      null
    }
  }
}
