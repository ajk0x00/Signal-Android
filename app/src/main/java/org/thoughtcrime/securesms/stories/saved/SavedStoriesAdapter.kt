package org.thoughtcrime.securesms.stories.saved

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.stories.drive.SavedStoryMediaType
import org.thoughtcrime.securesms.stories.drive.SavedStoryRecord

class SavedStoriesAdapter : ListAdapter<SavedStoryRecord, SavedStoriesAdapter.ViewHolder>(SavedStoryDiffCallback()) {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.saved_stories_grid_item, parent, false)
    return ViewHolder(view)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)
    private val playOverlay: View = itemView.findViewById(R.id.play_overlay)

    fun bind(record: SavedStoryRecord) {
      playOverlay.visibility = if (record.mediaType == SavedStoryMediaType.VIDEO) View.VISIBLE else View.GONE

      if (record.driveFileId != null) {
        com.bumptech.glide.Glide.with(itemView.context)
          .load(DriveThumbnailLoader.DriveThumbnailKey(record.driveFileId))
          .centerCrop()
          .into(thumbnail)
      }
    }
  }

  private class SavedStoryDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<SavedStoryRecord>() {
    override fun areItemsTheSame(oldItem: SavedStoryRecord, newItem: SavedStoryRecord): Boolean = oldItem.driveFileId == newItem.driveFileId
    override fun areContentsTheSame(oldItem: SavedStoryRecord, newItem: SavedStoryRecord): Boolean = oldItem == newItem
  }
}
