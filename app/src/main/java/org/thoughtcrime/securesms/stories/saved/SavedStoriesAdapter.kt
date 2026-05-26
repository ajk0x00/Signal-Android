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
  private val onItemLongClick: (SavedStoryRecord) -> Unit
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
    return ViewHolder(view, onItemClick, onItemLongClick) { selectedObjectNames }
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(getItem(position))
  }

  class ViewHolder(
    itemView: View,
    private val onItemClick: (SavedStoryRecord) -> Unit,
    private val onItemLongClick: (SavedStoryRecord) -> Unit,
    private val selectedProvider: () -> Set<String>
  ) : RecyclerView.ViewHolder(itemView) {
    private val thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)
    private val playOverlay: View = itemView.findViewById(R.id.play_overlay)
    private val selectedOverlay: View = itemView.findViewById(R.id.selected_overlay)

    fun bind(record: SavedStoryRecord) {
      playOverlay.visibility = if (record.mediaType == SavedStoryMediaType.VIDEO) View.VISIBLE else View.GONE

      val isSelected = record.objectName != null && record.objectName in selectedProvider()
      selectedOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
      itemView.isActivated = isSelected

      if (record.objectName != null) {
        val bucketName = SignalStore.cloudStorage.bucketName
        if (bucketName != null) {
          com.bumptech.glide.Glide.with(itemView.context)
            .load(CloudStorageThumbnailLoader.CloudStorageThumbnailKey(record.objectName, bucketName))
            .centerCrop()
            .into(thumbnail)
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
