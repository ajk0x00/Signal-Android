package org.thoughtcrime.securesms.stories.saved

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.stories.cloudstorage.SavedStoryRecord

class SavedStoriesViewModel(private val repository: SavedStoriesRepository) : ViewModel() {

  private val _stories = MutableLiveData<List<SavedStoryRecord>>()
  val stories: LiveData<List<SavedStoryRecord>> = _stories

  private val _selection = MutableLiveData<Set<String>>(emptySet())
  val selection: LiveData<Set<String>> = _selection

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      val records = repository.getSavedStories()
      _stories.postValue(records)
    }
  }

  fun toggleSelection(objectName: String) {
    val current = _selection.value.orEmpty()
    _selection.value = if (objectName in current) current - objectName else current + objectName
  }

  fun clearSelection() {
    if (_selection.value?.isNotEmpty() == true) {
      _selection.value = emptySet()
    }
  }

  fun deleteSelected() {
    val target = _selection.value.orEmpty()
    if (target.isEmpty()) return

    _selection.value = emptySet()
    viewModelScope.launch(Dispatchers.IO) {
      repository.deleteRecords(target)
      val records = repository.getSavedStories()
      _stories.postValue(records)
    }
  }

  class Factory(private val repository: SavedStoriesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return modelClass.cast(SavedStoriesViewModel(repository)) as T
    }
  }
}
