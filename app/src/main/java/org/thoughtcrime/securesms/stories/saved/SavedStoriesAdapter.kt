package org.thoughtcrime.securesms.stories.saved

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryMediaType
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryRecord

class SavedStoriesAdapter(
  private val onItemClick: (SavedStoryRecord) -> Unit,
  private val onItemLongClick: (SavedStoryRecord) -> Unit,
  private val onNeedVideoThumbnail: (SavedStoryRecord) -> Unit
) : ListAdapter<SavedStoryRecord, SavedStoriesAdapter.ViewHolder>(SavedStoryDiffCallback()) {

  var selectedObjectNames: Set<String> = emptySet()
    set(value) {
      if (field != value) {
        field = value
        notifyDataSetChanged()
      }
    }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val view = LayoutInflater.from(parent.context).inflate(R.layout.saved_stories_grid_item, parent, false)
    return ViewHolder(view, onItemClick, onItemLongClick, onNeedVideoThumbnail) { selectedObjectNames }
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class ViewHolder(
    itemView: View,
    private val onItemClick: (SavedStoryRecord) -> Unit,
    private val onItemLongClick: (SavedStoryRecord) -> Unit,
    private val onNeedVideoThumbnail: (SavedStoryRecord) -> Unit,
    private val selectedProvider: () -> Set<String>
  ) : RecyclerView.ViewHolder(itemView) {
    private val thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)
    private val playOverlay: View = itemView.findViewById(R.id.play_overlay)
    private val selectedOverlay: View = itemView.findViewById(R.id.selected_overlay)

    fun bind(record: SavedStoryRecord) {
      val isVideo = record.mediaType == SavedStoryMediaType.VIDEO
      playOverlay.visibility = if (isVideo) View.VISIBLE else View.GONE

      val isSelected = record.objectName != null && record.objectName in selectedProvider()
      selectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
      itemView.isActivated = isSelected

      // For videos, load the dedicated thumbnail object. Loading the .mp4 bytes as an image would fail, so when a
      // video has no thumbnail yet (legacy records) show a placeholder and trigger a one-time backfill.
      val thumbnailObject = if (isVideo) record.thumbnailObjectName else record.objectName
      val bucketName = SignalStore.cloudStorage.bucketName
      if (thumbnailObject != null && bucketName != null) {
        thumbnail.background = null
        com.bumptech.glide.Glide.with(itemView.context)
          .load(CloudStorageThumbnailLoader.CloudStorageThumbnailKey(thumbnailObject, bucketName))
          .centerCrop()
          .into(thumbnail)
      } else {
        com.bumptech.glide.Glide.with(itemView.context).clear(thumbnail)
        thumbnail.setImageDrawable(null)
        thumbnail.setBackgroundResource(org.signal.core.ui.R.color.signal_colorSurfaceVariant)
        if (isVideo && record.thumbnailObjectName == null && record.objectName != null) {
          onNeedVideoThumbnail(record)
        }
      }

      itemView.setOnClickListener { onItemClick(record) }
      itemView.setOnLongClickListener {
        onItemLongClick(record)
        true
      }
    }
  }

  private class SavedStoryDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<SavedStoryRecord>() {
    override fun areItemsTheSame(oldItem: SavedStoryRecord, newItem: SavedStoryRecord): Boolean = oldItem.objectName == newItem.objectName
    override fun areContentsTheSame(oldItem: SavedStoryRecord, newItem: SavedStoryRecord): Boolean = oldItem == newItem
  }
}
