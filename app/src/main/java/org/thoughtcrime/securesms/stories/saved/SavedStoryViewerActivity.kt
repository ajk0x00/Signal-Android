package org.thoughtcrime.securesms.stories.saved

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryRecord
import org.thoughtcrime.securesms.util.DynamicNoActionBarTheme

class SavedStoryViewerActivity : PassphraseRequiredActivity() {

  companion object {
    private val TAG = Log.tag(SavedStoryViewerActivity::class.java)
    private const val EXTRA_OBJECT_NAME = "object_name"

    @JvmStatic
    fun intent(context: Context, record: SavedStoryRecord): Intent {
      return Intent(context, SavedStoryViewerActivity::class.java).apply {
        putExtra(EXTRA_OBJECT_NAME, record.objectName)
      }
    }
  }

  private val dynamicTheme = DynamicNoActionBarTheme()

  override fun onPreCreate() {
    dynamicTheme.onCreate(this)
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    super.onCreate(savedInstanceState, ready)
    setContentView(R.layout.saved_story_viewer_pager_activity)

    val startObjectName = intent.getStringExtra(EXTRA_OBJECT_NAME)
    val bucketName = SignalStore.cloudStorage.bucketName

    if (bucketName == null) {
      Log.w(TAG, "Missing bucket name")
      finish()
      return
    }

    findViewById<ImageButton>(R.id.back).setOnClickListener { finish() }
    val pager = findViewById<ViewPager2>(R.id.media_pager)

    lifecycleScope.launch {
      // Load via the repository so the order matches the grid (sorted by timestamp desc).
      val records = withContext(Dispatchers.IO) { SavedStoriesRepository(this@SavedStoryViewerActivity).getSavedStories() }
        .filter { it.objectName != null }
      if (isFinishing || isDestroyed) {
        return@launch
      }
      if (records.isEmpty()) {
        Log.w(TAG, "No saved stories to display")
        finish()
        return@launch
      }

      val startIndex = records.indexOfFirst { it.objectName == startObjectName }.coerceAtLeast(0)
      pager.adapter = SavedStoryPagerAdapter(this@SavedStoryViewerActivity, records, bucketName)
      pager.setCurrentItem(startIndex, false)
    }
  }

  override fun onResume() {
    super.onResume()
    dynamicTheme.onResume(this)
  }
}
