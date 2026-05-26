package org.thoughtcrime.securesms.stories.saved

import android.content.Context
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryDatabase
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryRecord

class SavedStoriesRepository(private val context: Context) {

  fun getSavedStories(): List<SavedStoryRecord> {
    val db = SavedStoryDatabase(context)
    return db.getAll().sortedByDescending { it.timestamp }
  }
}
