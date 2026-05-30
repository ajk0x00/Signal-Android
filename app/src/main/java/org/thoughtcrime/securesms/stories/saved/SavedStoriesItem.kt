package org.thoughtcrime.securesms.stories.saved

import android.view.View
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.util.adapter.mapping.LayoutFactory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

object SavedStoriesItem {

  fun register(mappingAdapter: MappingAdapter) {
    mappingAdapter.registerFactory(Model::class.java, LayoutFactory(::ViewHolder, R.layout.stories_landing_item_saved_stories))
  }

  class Model(
    val count: Int,
    val onClick: () -> Unit
  ) : PreferenceModel<Model>() {
    override fun areItemsTheSame(newItem: Model): Boolean = true
    override fun areContentsTheSame(newItem: Model): Boolean = count == newItem.count
  }

  private class ViewHolder(itemView: View) : MappingViewHolder<Model>(itemView) {

    private val subtitle: android.widget.TextView = itemView.findViewById(R.id.subtitle)
    private val root: View = itemView

    override fun bind(model: Model) {
      subtitle.text = context.resources.getQuantityString(R.plurals.SavedStoriesItem__stories_saved, model.count, model.count)
      root.setOnClickListener { model.onClick() }
    }
  }
}
