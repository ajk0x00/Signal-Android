package org.thoughtcrime.securesms.stories.saved

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R

class SavedStoriesFragment : Fragment(R.layout.saved_stories_fragment) {

  private lateinit var viewModel: SavedStoriesViewModel
  private lateinit var adapter: SavedStoriesAdapter

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val factory = SavedStoriesViewModel.Factory(SavedStoriesRepository(requireContext()))
    viewModel = ViewModelProvider(this, factory)[SavedStoriesViewModel::class.java]

    adapter = SavedStoriesAdapter()

    val recycler = view.findViewById<RecyclerView>(R.id.recycler)
    val emptyState = view.findViewById<TextView>(R.id.empty_state)
    val spanCount = resources.getInteger(R.integer.media_overview_cols)
    recycler.layoutManager = GridLayoutManager(requireContext(), spanCount)
    recycler.adapter = adapter

    viewModel.stories.observe(viewLifecycleOwner) { stories ->
      adapter.submitList(stories)
      emptyState.visibility = if (stories.isEmpty()) View.VISIBLE else View.GONE
    }
  }
}
