package org.thoughtcrime.securesms.stories.saved

import android.content.Context
import org.thoughtcrime.securesms.stories.drive.SavedStoryDatabase
import org.thoughtcrime.securesms.stories.drive.SavedStoryRecord

class SavedStoriesRepository(private val context: Context) {

  fun getSavedStories(): List<SavedStoryRecord> {
    val db = SavedStoryDatabase(context)
    return db.getAll().sortedByDescending { it.timestamp }
  }
}
