package org.thoughtcrime.securesms.stories.saved

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.github.chrisbanes.photoview.PhotoView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.cloudstorage.CloudStorageCredentialsProvider
import org.thoughtcrime.securesms.cloudstorage.CloudStorageServiceHelper
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryMediaType
import java.io.File
import java.io.FileOutputStream

/**
 * A single page in the swipeable saved-stories viewer. Renders an image with [PhotoView] or streams a video with
 * [ExoPlayer]. Because the hosting [androidx.viewpager2.widget.ViewPager2] keeps only the current page RESUMED, video
 * playback is started in [onResume] and paused/released as the page leaves the foreground — so only the visible page plays.
 */
class SavedStoryPageFragment : Fragment(R.layout.saved_story_page_fragment) {

  companion object {
    private val TAG = Log.tag(SavedStoryPageFragment::class.java)

    private const val ARG_OBJECT_NAME = "object_name"
    private const val ARG_MEDIA_TYPE = "media_type"
    private const val ARG_BUCKET_NAME = "bucket_name"

    fun create(objectName: String, mediaTypeName: String, bucketName: String): SavedStoryPageFragment {
      return SavedStoryPageFragment().apply {
        arguments = Bundle().apply {
          putString(ARG_OBJECT_NAME, objectName)
          putString(ARG_MEDIA_TYPE, mediaTypeName)
          putString(ARG_BUCKET_NAME, bucketName)
        }
      }
    }
  }

  private val objectName: String get() = requireArguments().getString(ARG_OBJECT_NAME)!!
  private val bucketName: String get() = requireArguments().getString(ARG_BUCKET_NAME)!!
  private val mediaType: SavedStoryMediaType
    get() = runCatching { SavedStoryMediaType.valueOf(requireArguments().getString(ARG_MEDIA_TYPE)!!) }.getOrDefault(SavedStoryMediaType.IMAGE)

  private var player: ExoPlayer? = null
  private var cachedVideoFile: File? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    if (mediaType == SavedStoryMediaType.VIDEO) {
      view.findViewById<PlayerView>(R.id.player).visibility = View.VISIBLE
    } else {
      showImage(view)
    }
  }

  private fun showImage(view: View) {
    val image = view.findViewById<PhotoView>(R.id.image)
    image.visibility = View.VISIBLE
    com.bumptech.glide.Glide.with(this)
      .load(CloudStorageThumbnailLoader.CloudStorageThumbnailKey(objectName, bucketName))
      .fitCenter()
      .into(image)
  }

  override fun onResume() {
    super.onResume()
    if (mediaType == SavedStoryMediaType.VIDEO) {
      startOrResumeVideo()
    }
  }

  override fun onPause() {
    super.onPause()
    player?.playWhenReady = false
  }

  override fun onDestroyView() {
    player?.release()
    player = null
    super.onDestroyView()
  }

  private fun startOrResumeVideo() {
    val existing = player
    if (existing != null) {
      existing.playWhenReady = true
      return
    }

    val view = view ?: return
    val playerView = view.findViewById<PlayerView>(R.id.player)
    val loading = view.findViewById<ProgressBar>(R.id.loading)
    loading.visibility = View.VISIBLE

    viewLifecycleOwner.lifecycleScope.launch {
      val file = cachedVideoFile ?: withContext(Dispatchers.IO) { downloadToCache(objectName) }
      if (view == null || !isResumed) {
        return@launch
      }
      loading.visibility = View.GONE
      if (file == null) {
        Log.w(TAG, "Failed to load saved video $objectName")
        return@launch
      }
      cachedVideoFile = file
      val exo = ExoPlayer.Builder(requireContext()).build()
      player = exo
      playerView.player = exo
      exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
      exo.prepare()
      exo.playWhenReady = true
    }
  }

  private fun downloadToCache(objectName: String): File? {
    return try {
      val storage = CloudStorageCredentialsProvider.getStorageInstance(requireContext()) ?: return null
      val bucket = SignalStore.cloudStorage.bucketName ?: return null
      val helper = CloudStorageServiceHelper(storage, bucket)
      val dir = File(requireContext().cacheDir, "saved_stories").apply { mkdirs() }
      val file = File(dir, objectName.substringAfterLast('/').ifEmpty { "video.mp4" })
      if (!file.exists() || file.length() == 0L) {
        FileOutputStream(file).use { helper.downloadFile(objectName, it) }
      }
      file
    } catch (e: Exception) {
      Log.w(TAG, "Failed to download $objectName", e)
      null
    }
  }
}
