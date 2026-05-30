package org.thoughtcrime.securesms.stories.saved

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryRecord

/**
 * Pages through the full ordered list of saved stories so the viewer can swipe between media.
 * Records carry only primitives into each page fragment, since [SavedStoryRecord] is not Parcelable.
 */
class SavedStoryPagerAdapter(
  activity: FragmentActivity,
  private val records: List<SavedStoryRecord>,
  private val bucketName: String
) : FragmentStateAdapter(activity) {

  override fun getItemCount(): Int = records.size

  override fun createFragment(position: Int): Fragment {
    val record = records[position]
    return SavedStoryPageFragment.create(
      objectName = record.objectName.orEmpty(),
      mediaTypeName = record.mediaType.name,
      bucketName = bucketName
    )
  }
}
