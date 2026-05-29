package org.thoughtcrime.securesms.stories.saved

import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.thoughtcrime.securesms.R

class SavedStoriesFragment : Fragment(R.layout.saved_stories_fragment) {

  private lateinit var viewModel: SavedStoriesViewModel
  private lateinit var adapter: SavedStoriesAdapter
  private var actionMode: ActionMode? = null

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    val factory = SavedStoriesViewModel.Factory(SavedStoriesRepository(requireContext()))
    viewModel = ViewModelProvider(this, factory)[SavedStoriesViewModel::class.java]

    adapter = SavedStoriesAdapter(
      onItemClick = { record ->
        val objectName = record.objectName ?: return@SavedStoriesAdapter
        if (viewModel.selection.value.isNullOrEmpty()) {
          startActivity(SavedStoryViewerActivity.intent(requireContext(), record))
        } else {
          viewModel.toggleSelection(objectName)
        }
      },
      onItemLongClick = { record ->
        val objectName = record.objectName ?: return@SavedStoriesAdapter
        viewModel.toggleSelection(objectName)
      },
      onNeedVideoThumbnail = { record ->
        viewModel.ensureVideoThumbnail(record)
      }
    )

    val recycler = view.findViewById<RecyclerView>(R.id.recycler)
    val emptyState = view.findViewById<TextView>(R.id.empty_state)
    val spanCount = resources.getInteger(R.integer.media_overview_cols)
    recycler.layoutManager = GridLayoutManager(requireContext(), spanCount)
    recycler.adapter = adapter

    viewModel.stories.observe(viewLifecycleOwner) { stories ->
      adapter.submitList(stories)
      emptyState.visibility = if (stories.isEmpty()) View.VISIBLE else View.GONE
    }

    viewModel.selection.observe(viewLifecycleOwner) { selection ->
      adapter.selectedObjectNames = selection
      if (selection.isEmpty()) {
        actionMode?.finish()
      } else {
        if (actionMode == null) {
          actionMode = requireActivity().startActionMode(actionModeCallback)
        }
        actionMode?.title = resources.getQuantityString(
          R.plurals.SavedStoriesFragment__selected,
          selection.size,
          selection.size
        )
      }
    }
  }

  override fun onDestroyView() {
    actionMode?.finish()
    actionMode = null
    super.onDestroyView()
  }

  private val actionModeCallback = object : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
      mode.menuInflater.inflate(R.menu.saved_stories_action_mode, menu)
      return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
      return if (item.itemId == R.id.action_delete) {
        confirmAndDelete()
        true
      } else {
        false
      }
    }

    override fun onDestroyActionMode(mode: ActionMode) {
      actionMode = null
      viewModel.clearSelection()
    }
  }

  private fun confirmAndDelete() {
    val count = viewModel.selection.value?.size ?: 0
    if (count == 0) return

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.SavedStoriesFragment__delete_confirm_title)
      .setMessage(resources.getQuantityString(R.plurals.SavedStoriesFragment__delete_confirm_body, count, count))
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(R.string.SavedStoriesFragment__delete) { _, _ ->
        viewModel.deleteSelected()
        actionMode?.finish()
      }
      .show()
  }
}
